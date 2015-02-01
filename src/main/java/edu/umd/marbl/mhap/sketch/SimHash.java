/* 
 * MHAP package
 * 
 * This  software is distributed "as is", without any warranty, including 
 * any implied warranty of merchantability or fitness for a particular
 * use. The authors assume no responsibility for, and shall not be liable
 * for, any special, indirect, or consequential damages, or any damages
 * whatsoever, arising out of or in connection with the use of this
 * software.
 * 
 * Copyright (c) 2014 by Konstantin Berlin and Sergey Koren
 * University Of Maryland
 * 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */
package edu.umd.marbl.mhap.sketch;

import edu.umd.marbl.mhap.utils.Utils;

public final class SimHash extends AbstractBitSketch<SimHash>
{
	/**
	 * 
	 */
	private static final long serialVersionUID = -2655482279264410602L;
	public final int seqLength;
	
	private static final long[] recordHashes(final long[][] hashes, final int numWords)
	{
		final int[] counts = new int[numWords * 64];
		
		// perform count for each kmer
		for (long[] objectHashes : hashes)
		{
			for (int wordIndex = 0; wordIndex < numWords; wordIndex++)
			{
				final long val = objectHashes[wordIndex];
				final int offset = wordIndex * 64;

				long mask = 0b1;

				for (int bit = 0; bit < 64; bit++)
				{
					// if not different then increase counts
					if ((val & mask) == 0b0)
						counts[offset + bit]--;
					else
						counts[offset + bit]++;

					mask = mask << 1;
				}
			}
		}

		long[] bits = new long[numWords];
		for (int wordIndex = 0; wordIndex < numWords; wordIndex++)
		{
			final int offset = wordIndex * 64;
			long val = 0b0;
			long mask = 0b1;

			for (int bit = 0; bit < 64; bit++)
			{
				if (counts[offset + bit] > 0)
					val = val | mask;

				// adjust the mask
				mask = mask << 1;
			}

			bits[wordIndex] = val;
		}

		return bits;
	}

	public SimHash(String string, int kmerSize, int numberWords)
	{
		super(recordHashes(Utils.computeKmerHashesExact(string, kmerSize, numberWords, 0), numberWords));
		this.seqLength = string.length();
	}


}
