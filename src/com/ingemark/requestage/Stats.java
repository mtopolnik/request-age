package com.ingemark.requestage;

import static com.ingemark.requestage.Util.arrayMean;
import static com.ingemark.requestage.Util.arrayStdev;
import static com.ingemark.requestage.Util.arraySum;
import static com.ingemark.requestage.Util.reciprocalArray;

import java.io.Serializable;


public class Stats implements Serializable
{
  public final int index;
  public final String name;
  public final char[] histogram;
  public final float avgRespTime, stdevRespTime, avgServIntensty, stdevServIntensty, avgRespSize;
  public final int reqsPerSec, succRespPerSec, failsPerSec, pendingReqs;
  public Stats() {
    index = 0;
    name = "<empty>";
    histogram = new char[0];
    avgRespTime = stdevRespTime = avgServIntensty = stdevServIntensty = avgRespSize = reqsPerSec =
        succRespPerSec = failsPerSec = pendingReqs = 0;
  }
  Stats(LiveStats live) {
    this.index = live.index;
    this.name = live.name;
    reqsPerSec = arraySum(live.reqs);
    succRespPerSec = arraySum(live.succs);
    failsPerSec = arraySum(live.fails);
    final float[] intenArray = reciprocalArray(live.respTimes);
    final double avgTime = arrayMean(live.respTimes), avgInten = arrayMean(intenArray);
    avgRespSize = (float)arrayMean(live.respSizes);
    avgRespTime = (float)avgTime;
    stdevRespTime = (float)arrayStdev(avgTime, live.respTimes);
    avgServIntensty = (float)avgInten;
    stdevServIntensty = (float)arrayStdev(avgInten, intenArray);
    pendingReqs = live.pendingReqs.get();
    histogram = live.reqHistogram();
  }
  @Override public String toString() {
    return String.format("%s %d %d %d %d",
        name, reqsPerSec, succRespPerSec, failsPerSec, pendingReqs);
  }
}
