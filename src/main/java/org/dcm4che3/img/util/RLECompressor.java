/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.dcm4che3.img.util;

public class RLECompressor {

  enum State {
    INITIAL,
    LITERAL,
    UNDECIDED
  }

  /**
   * Calculate length of every byte run in the input buffer and store into runb[] buffer. Note that
   * the count - 1 value is stored so data stored ranges from 0 to 127 which represents the values 1
   * to 128. e.g INPUT: 03 04 05 05 06 07 08 09 09 09 OUTPUT: 00 00 01 00 00 00 02 Note that a
   * runlength of > 128 bytes is encoded as multiple runs of 128 bytes. Writing byte value of 128
   * into a byte array in java actually casts it to a signed value of -128.
   *
   * @param inb raw data to calculate byte runs on
   * @return output to contain length of each byte run this buffer must be as long as input buffer
   *     to handle worst case.
   */
  public static int getAllRuns(byte[] inb) {
    int i; // input index
    int o; // output index in runb
    byte b;
    int repLen;
    int start;

    i = 0;
    o = 0;
    do {
      start = i; // start of replicate run
      b = inb[i++];
      while (i < inb.length && b == inb[i]) // find replicate runs
      ++i;
      repLen = i - start;
      while (repLen > 128) {

        runb[o++] = (byte) 127; // 128 - 1
        repLen -= 128;
      }
      runb[o++] = (byte) (repLen - 1);
    } while (i < inb.length);

    return o; // number of bytes in runb[]
  }

  /** Temp buffer for compression see getAllRuns() for size buffer required */
  static byte[] runb;

  /**
   * Compress input buffer using RLE packbits compression.
   *
   * <p>The second part of the code passes through the output compressed bytes and converts any 2 by
   * replicate runs which are between literal runs into a literal run.. [ shit cant do if literal
   * gets > 128 then i have start inserting bytes what a suck... ]
   *
   * @param inb input buffer containing bytes to be compressed using RLE packbits algorithm. buffer
   *     is assumed to be completely filled.
   * @param outb output buffer containing compressed data. This must be large enough for worst case
   *     compression of input buffer. [ worst case is 1 additional byte for every 128 bytes of input
   *     buffer with a minimum size of 2 bytes ]
   * @return the number of bytes in the output buffer. It should not be possible to return a value <
   *     2.
   *     <pre>
   * Example Input buffer.
   *   03 04 05 05 06 07 08
   * Example Stoopid encoding
   *   01 03 04 -01 05 02 06 07 08
   * Example Proper encoding
   *   06 03 04 05 05 06 07 08
   * </pre>
   */
  public static int packbits(byte[] inb, byte[] outb)
      throws ArrayStoreException, ArrayIndexOutOfBoundsException {
    int runbLen; // length of replicate run data in rub[]
    int runI; // index into replicate run buffer
    int repLen; // length of current replicate run
    State state; // compressor state
    int i; // input buffer index
    int o; // output buffer index
    int runcount; // count of literals so far covered
    // maybe literal made up of pairs of replicate
    // bytes if state UNDECIDED

    if (runb == null || runb.length != inb.length) runb = new byte[inb.length];

    runbLen = getAllRuns(inb);

    runcount = 0;
    state = State.INITIAL;
    i = 0;
    o = 0;
    for (runI = 0; runI < runbLen; ++runI) {
      repLen = runb[runI] + 1;
      // P.rt("repLen " + repLen + " input " + i);
      switch (state) {
        case INITIAL:
          while (repLen > 128) // encode replicates > 128
          {
            outb[o++] = (byte) (-(128 - 1));
            outb[o++] = inb[i];
            i += 128;
            repLen -= 128;
          }
          if (repLen == 1) {
            state = State.LITERAL;
            runcount = 1;
          } else if (repLen == 2) {
            state = State.UNDECIDED;
            runcount = 2;
          } else // repLen >= 3 and repLen < 128
          {
            // state = INITIAL
            outb[o++] = (byte) (-(repLen - 1));
            outb[o++] = inb[i];
            i += repLen; // advance to byte after replicate in input
          }
          break;

        case LITERAL:
          if (repLen < 3) {
            // state = LITERAL
            runcount += repLen;
          } else {
            state = State.INITIAL;

            // check for LITERAL runcount > 128, dice up as required
            while (runcount > 128) {
              outb[o++] = (byte) (128 - 1); // encode literal
              System.arraycopy(inb, i, outb, o, 128);
              i += 128;
              o += 128;
              runcount -= 128;
            }

            outb[o++] = (byte) (runcount - 1); // encode literal
            System.arraycopy(inb, i, outb, o, runcount);
            i += runcount;
            o += runcount;

            // check for repLen > 128, dice up into 128 chunks.
            while (repLen > 128) // encode replicates > 128
            {
              outb[o++] = (byte) (-(128 - 1));
              outb[o++] = inb[i];
              i += 128;
              repLen -= 128;
            }
            // damn if repLen == 1 or 2 then need to handle as in INITIAL
            if (repLen == 1) {
              state = State.LITERAL;
              runcount = 1;
            } else if (repLen == 2) {
              state = State.UNDECIDED;
              runcount = 2;
            } else // repLen >= 3 and repLen < 128
            {
              outb[o++] = (byte) (-(repLen - 1)); // encode replicate
              outb[o++] = inb[i];
              i += repLen;
            }
          }
          break;

        case UNDECIDED:
          if (repLen == 1) {
            state = State.LITERAL;
            ++runcount;
          } else if (repLen == 2) {
            // state = UNDECIDED;
            runcount += 2;
          } else // repLen > 2
          {
            state = State.INITIAL;

            // at this point may have multiple pairs of replicate
            // bytes all added into runcount followed by a replicate
            // run of > 2 bytes. So output each of the twin replicate
            // runs then output the > 2 length replicate run.
            for (; runcount > 0; runcount -= 2) {
              outb[o++] = (byte) (-(2 - 1));
              outb[o++] = inb[i];
              i += 2; // skip over replicate run just output
            }

            // check for repLen > 128, dice up into 128 chunks.
            while (repLen > 128) // encode replicates > 128
            {
              outb[o++] = (byte) (-(128 - 1));
              outb[o++] = inb[i];
              i += 128;
              repLen -= 128;
            }
            // damn if repLen == 1 or 2 then need to handle as in INITIAL
            if (repLen == 1) {
              state = State.LITERAL;
              runcount = 1;
            } else if (repLen == 2) {
              state = State.UNDECIDED;
              runcount = 2;
            } else // repLen >= 3 and repLen < 128
            {
              // now output the > 2 length replicate run
              outb[o++] = (byte) (-(repLen - 1));
              outb[o++] = inb[i];
              i += repLen; // skip over replicate run just output
            }
          }
          break;
      }
    }

    // finalise states
    switch (state) {
      case LITERAL:
        while (runcount > 128) {
          outb[o++] = (byte) (128 - 1); // encode literal
          System.arraycopy(inb, i, outb, o, 128);
          i += 128;
          o += 128;
          runcount -= 128;
        }
        if (runcount > 0) {
          outb[o++] = (byte) (runcount - 1); // encode literal
          System.arraycopy(inb, i, outb, o, runcount);
          i += runcount;
          o += runcount;
        }
        break;

      case UNDECIDED:
        // dump the added replicate pairs as replicates
        // to finish.
        for (; runcount > 0; runcount -= 2) {
          outb[o++] = (byte) (-(2 - 1));
          outb[o++] = inb[i];
          i += 2; // skip over replicate run just output
        }
        break;
    }

    return o;
  }
}
