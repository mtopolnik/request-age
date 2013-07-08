package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.requestage.Util.arrayMean;

import com.ingemark.requestage.Stats;

public class History {
  private static final int FULL_SIZE = 1<<11, BUFSIZ = 8*TIMESLOTS_PER_SEC;
  private int index, bufIndex, bufLimit = TIMESLOTS_PER_SEC, divisor = 1, calledCount;
  public String name;
  private final double[]
      reqsPerSec = new double[FULL_SIZE],
      pendingReqs = new double[FULL_SIZE],
      servingIntensity = new double[FULL_SIZE];
  private final float[]
      bufReqsPerSec = new float[BUFSIZ],
      bufPendingReqs = new float[BUFSIZ],
      bufServingIntensity = new float[BUFSIZ];

  public void stats(Stats stats) {
    if (++calledCount % divisor != 0) return;
    name = stats.name;
    bufReqsPerSec[bufIndex] = stats.reqsPerSec;
    bufPendingReqs[bufIndex] = stats.pendingReqs;
    bufServingIntensity[bufIndex] = stats.avgServIntensty;
    if (++bufIndex == bufLimit) {
      reqsPerSec[index] = arrayMean(bufReqsPerSec, bufLimit);
      pendingReqs[index] = arrayMean(bufPendingReqs, bufLimit);
      servingIntensity[index] = arrayMean(bufServingIntensity, bufLimit);
      if (++index == FULL_SIZE) {
        squeeze(reqsPerSec);
        squeeze(pendingReqs);
        squeeze(servingIntensity);
        index /= 2;
        if (bufLimit <= BUFSIZ/2) bufLimit *= 2;
        else divisor *= 2;
      }
    }
  }

  private void squeeze(double[] array) {
    for (int i = 0; i < array.length/2; i++) array[i] = (array[2*i]+array[2*i+1])/2;
  }
}
