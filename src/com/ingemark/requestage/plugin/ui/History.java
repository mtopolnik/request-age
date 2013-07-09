package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.requestage.Util.arrayMean;
import static com.ingemark.requestage.Util.event;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_HISTORY_UPDATE;
import static com.ingemark.requestage.plugin.RequestAgePlugin.globalEventHub;

import java.util.Arrays;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingemark.requestage.Stats;

public class History {
  static final Logger log = LoggerFactory.getLogger(History.class);
  private static final int FULL_SIZE = 1<<11, BUFSIZ = 8*TIMESLOTS_PER_SEC;
  private int index, bufIndex, bufLimit = TIMESLOTS_PER_SEC, divisor = 1, calledCount;
  public String name;
  private final double[]
      reqsPerSec = new double[FULL_SIZE],
      pendingReqs = new double[FULL_SIZE],
      servingIntensity = new double[FULL_SIZE];
  private final Date[] timestamps = new Date[FULL_SIZE];
  private final float[]
      bufReqsPerSec = new float[BUFSIZ],
      bufPendingReqs = new float[BUFSIZ],
      bufServingIntensity = new float[BUFSIZ];

  public void statsUpdate(Stats stats) {
    if (++calledCount % divisor != 0) return;
    name = stats.name;
    bufReqsPerSec[bufIndex] = stats.reqsPerSec;
    bufPendingReqs[bufIndex] = stats.pendingReqs;
    bufServingIntensity[bufIndex] = stats.avgServIntensty;
    if (++bufIndex == bufLimit) {
      bufIndex = 0;
      timestamps[index] = new Date();
      reqsPerSec[index] = arrayMean(bufReqsPerSec, bufLimit);
      pendingReqs[index] = arrayMean(bufPendingReqs, bufLimit);
      servingIntensity[index] = arrayMean(bufServingIntensity, bufLimit);
      globalEventHub().notifyListeners(EVT_HISTORY_UPDATE, event(this));
      if (++index == FULL_SIZE) {
        squeezeDates(timestamps);
        squeeze(reqsPerSec);
        squeeze(pendingReqs);
        squeeze(servingIntensity);
        index /= 2;
        if (bufLimit <= BUFSIZ/2) bufLimit *= 2;
        else divisor *= 2;
      }
    }
  }

  private static void squeeze(double[] array) {
    for (int i = 0; i < array.length/2; i++) array[i] = (array[2*i]+array[2*i+1])/2;
  }
  private static void squeezeDates(Date[] array) {
    for (int i = 0; i < array.length/2; i++)
      array[i] = new Date((array[2*i].getTime()+array[2*i+1].getTime())/2);
  }

  public double[] reqsPerSec() {
    return Arrays.copyOf(reqsPerSec, index);
  }
  public Date[] timestamps() {
    return Arrays.copyOf(timestamps, index);
  }
}
