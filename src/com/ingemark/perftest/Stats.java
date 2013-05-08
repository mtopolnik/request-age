package com.ingemark.perftest;

import com.ingemark.perftest.RequestProvider.LiveStats;

public class Stats
{
  public final int index;
  public final String name;
  public final int[] histogram;
  public final int reqsPerSec, succRespPerSec, failsPerSec, pendingReqs;
  public Stats() {
    index = 0;
    name = "<empty>";
    histogram = new int[0];
    reqsPerSec = succRespPerSec = failsPerSec = pendingReqs = 0;
  }
  Stats(LiveStats live, String name) {
    this.index = live.index;
    this.name = name;
    reqsPerSec = arraySum(live.reqs);
    succRespPerSec = arraySum(live.succs);
    failsPerSec = arraySum(live.fails);
    pendingReqs = live.pendingReqs.get();
    histogram = live.reqHistogram();
  }
  static int arraySum(int[] array) {
    int sum = 0;
    for (int cnt : array) sum += cnt;
    return sum;
  }
}
