package com.secret.fastalign.general;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.secret.fastalign.utils.FastAlignRuntimeException;

public abstract class AbstractHashSearch<H extends AbstractSequenceHashes<H>, T extends AbstractReducedSequence<H,T>>
{
	protected final int kmerSize;
	protected final int numWords;

	private final boolean storeResults;
	protected static BufferedWriter outWriter = new BufferedWriter(new OutputStreamWriter(System.out), 8*1024*1024);

	public AbstractHashSearch(int kmerSize, int numWords, boolean storeResults)
	{
		this.kmerSize = kmerSize;
		this.numWords = numWords;
		this.storeResults = storeResults;
	}

	protected void addData(final FastaData data)
	{
		//figure out number of cores
		final int numThreads = Runtime.getRuntime().availableProcessors()*2;
		ExecutorService execSvc = Executors.newFixedThreadPool(numThreads);
		
		final AtomicInteger counter = new AtomicInteger();
	  for (int iter=0; iter<numThreads; iter++)
		{
			Runnable task = new Runnable()
			{					
				@Override
				public void run()
				{
					try
					{
			    	Sequence seq = data.dequeue();
				    while(seq != null)
				    {
			    		addSequence(seq);

			    		int currCount = counter.incrementAndGet();
				    	if (currCount%10000==0)
				    		System.err.println("Current sequences hashed: "+currCount+"...");
				    	
				    	seq = data.dequeue();
				    }
			    }
					catch (IOException e)
					{
						throw new FastAlignRuntimeException(e);
					}
				}
			};
		
	  	//enqueue the task
			execSvc.execute(task);					
		}
		
		//shutdown the service
	  execSvc.shutdown();
	  try
		{
			execSvc.awaitTermination(365L, TimeUnit.DAYS);
		} 
	  catch (InterruptedException e) 
	  {
	  	execSvc.shutdownNow();
	  	throw new FastAlignRuntimeException("Unable to finish all tasks.");
	  }
	}

	protected abstract boolean addDirectionalSequence(Sequence seq);
	
	protected boolean addSequence(Sequence seq)
	{
		//add forward sequence
		boolean success = addDirectionalSequence(seq);
		
		//add reverse sequence
		if (success)
		{
			Sequence reverse = seq.getReverseCompliment();		
			success = addDirectionalSequence(reverse);
		}
	
		return success;
	}
	
	public ArrayList<MatchResult> findMatches(final double acceptScore)
	{
		//figure out number of cores
		final int numThreads = Runtime.getRuntime().availableProcessors()*2;
		ExecutorService execSvc = Executors.newFixedThreadPool(numThreads);
		
		//allocate the storage and get the list of valeus
		final ArrayList<MatchResult> combinedList = new ArrayList<MatchResult>();
		final ConcurrentLinkedQueue<SequenceId> seqList = new ConcurrentLinkedQueue<SequenceId>(getStoredForwardSequenceIds());
	
		//for each thread create a task
	  for (int iter=0; iter<numThreads; iter++)
		{
			Runnable task = new Runnable()
			{ 			
				@Override
				public void run()
				{
	  			List<MatchResult> localMatches = new ArrayList<MatchResult>();

      		//get next sequence
	  			SequenceId nextSequence = seqList.poll();

	  			while (nextSequence!=null)
			    {		    		
		    		T sequenceHashes = getStoredSequenceHash(nextSequence);
		    		
		    		//only search the forward sequences
	      		localMatches.addAll(findMatches(sequenceHashes, acceptScore, true));

	      		//get next sequence
		    		nextSequence = seqList.poll();		    		
			    }
		    	
	  			if (AbstractHashSearch.this.storeResults)
	  			{	  			
		    		//combine the results
		    		synchronized (combinedList)
						{
							combinedList.addAll(localMatches);
						}
	  			}
	  			else
	  				outputResults(localMatches);
				}
			};
		
	  	//enqueue the task
			execSvc.execute(task);					
		}
		
		//shutdown the service
	  execSvc.shutdown();
	  try
		{
			execSvc.awaitTermination(365L, TimeUnit.DAYS);
		} 
	  catch (InterruptedException e) 
	  {
	  	execSvc.shutdownNow();
	  	throw new FastAlignRuntimeException("Unable to finish all tasks.");
	  }
		
		return combinedList;
	}

	public ArrayList<MatchResult> findMatches(final FastaData data, final double acceptScore)
	{
		//figure out number of cores
		final int numThreads = Runtime.getRuntime().availableProcessors()*2;
		ExecutorService execSvc = Executors.newFixedThreadPool(numThreads);
		
		//allocate the storage and get the list of valeus
		final ArrayList<MatchResult> combinedList = new ArrayList<MatchResult>();
	
		//for each thread create a task
	  for (int iter=0; iter<numThreads; iter++)
		{
			Runnable task = new Runnable()
			{ 			
				@Override
				public void run()
				{
	  			List<MatchResult> localMatches = new ArrayList<MatchResult>();
	  			
	  			try
	  			{
		  			Sequence nextSequence = data.dequeue();
	
		  			while (nextSequence!=null)
				    {		    		
			    		T sequenceHashes = getSequenceHash(nextSequence);
			    		
			    		//only search the forward sequences
		      		localMatches.addAll(findMatches(sequenceHashes, acceptScore, false));
	
		      		//get the sequence hashes
			    		nextSequence = data.dequeue();	    		
				    }
			    	
		  			if (AbstractHashSearch.this.storeResults)
		  			{	  			
			    		//combine the results
			    		synchronized (combinedList)
							{
								combinedList.addAll(localMatches);
							}
		  			}
		  			else
		  				outputResults(localMatches);
		  		}
	  			catch (IOException e)
	  			{
	  				throw new FastAlignRuntimeException(e);
	  			}
				}
			};
		
	  	//enqueue the task
			execSvc.execute(task);					
		}
		
		//shutdown the service
	  execSvc.shutdown();
	  try
		{
			execSvc.awaitTermination(365L, TimeUnit.DAYS);
		} 
	  catch (InterruptedException e) 
	  {
	  	execSvc.shutdownNow();
	  	throw new FastAlignRuntimeException("Unable to finish all tasks.");
	  }
		
		return combinedList;	
	}

	public List<MatchResult> findMatches(Sequence seq, double acceptScore)
	{
		//see if already have the sequence stored
		T hashes = getStoredSequenceHash(seq.getId());
		
		//if not get the sequence
		if (hashes==null)
			hashes = getSequenceHash(seq);
		
		return findMatches(hashes, acceptScore, false);
	}

	public abstract List<MatchResult> findMatches(T hashes, double acceptScore, boolean allToAll);
	
	public abstract T getSequenceHash(Sequence seq);

	public abstract Collection<SequenceId> getStoredForwardSequenceIds();
	
	public abstract T getStoredSequenceHash(SequenceId id);

	protected void outputResults(List<MatchResult> matches)
	{
		if (this.storeResults || matches.isEmpty())
			return;
		
		try
		{
			synchronized (outWriter)
			{
				for (MatchResult currResult : matches)
				{
					outWriter.write(currResult.toString());
					outWriter.newLine();
				}

				outWriter.flush();
			}
		}
		catch (IOException e)
		{
			throw new FastAlignRuntimeException(e);
		}
	}

	public abstract int size();

}