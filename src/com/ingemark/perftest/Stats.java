package com.ingemark.perftest;

import static com.ingemark.perftest.Util.arrayMean;
import static com.ingemark.perftest.Util.arrayStdev;
import static com.ingemark.perftest.Util.arraySum;

import java.io.Serializable;


public class Stats implements Serializable
{
  public final int index;
  public final String name;
  public final char[] histogram;
  public final float avgRespTime, stdevRespTime;
  public final int reqsPerSec, succRespPerSec, failsPerSec, pendingReqs;
  public Stats() {
    index = 0;
    name = "<empty>";
    histogram = new char[0];
    avgRespTime = stdevRespTime = reqsPerSec = succRespPerSec = failsPerSec = pendingReqs = 0;
  }
  Stats(LiveStats live) {
    this.index = live.index;
    this.name = live.name;
    reqsPerSec = arraySum(live.reqs);
    succRespPerSec = arraySum(live.succs);
    failsPerSec = arraySum(live.fails);
    final double avg = arrayMean(live.respTimes);
    avgRespTime = (float)avg;
    stdevRespTime = (float)arrayStdev(avg, live.respTimes);
    pendingReqs = live.pendingReqs.get();
    histogram = live.reqHistogram();
  }
  @Override public String toString() {
    return String.format("%s %d %d %d %d",
        name, reqsPerSec, succRespPerSec, failsPerSec, pendingReqs);
  }
}
