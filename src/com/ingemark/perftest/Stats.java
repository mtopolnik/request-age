package com.ingemark.perftest;

import java.io.Serializable;

import com.ingemark.perftest.RequestProvider.LiveStats;

public class Stats implements Serializable
{
  public final int index;
  public final String name;
  public final char[] histogram;
  public final int reqsPerSec, succRespPerSec, failsPerSec, pendingReqs;
  public Stats() {
    index = 0;
    name = "<empty>";
    histogram = new char[0];
    reqsPerSec = succRespPerSec = failsPerSec = pendingReqs = 0;
  }
  Stats(LiveStats live, String name) {
    this.index = live.index;
    this.name = name;
    reqsPerSec = Util.arraySum(live.reqs);
    succRespPerSec = Util.arraySum(live.succs);
    failsPerSec = Util.arraySum(live.fails);
    pendingReqs = live.pendingReqs.get();
    histogram = live.reqHistogram();
  }
  @Override public String toString() {
    return String.format("%s %d %d %d %d",
        name, reqsPerSec, succRespPerSec, failsPerSec, pendingReqs);
  }
}
