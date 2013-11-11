package com.ingemark.requestage;

import static com.ingemark.requestage.Util.arrayMean;
import static com.ingemark.requestage.Util.arrayStdev;
import static com.ingemark.requestage.Util.arraySum;
import static com.ingemark.requestage.Util.reciprocalArray;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TLongObjectHashMap;

import java.io.Serializable;


public class Stats implements Serializable
{
  public final int index;
  public final String name;
  public final char[] histogram;
  public final float avgRespTime, stdevRespTime, avgServIntensty, stdevServIntensty, avgRespSize;
  public final int reqsPerSec, succRespPerSec, failsPerSec, pendingReqs;
  public final TLongObjectHashMap<TIntIntHashMap> respHistory;
  public Stats() {
    index = 0;
    name = "<empty>";
    histogram = new char[0];
    avgRespTime = stdevRespTime = avgServIntensty = stdevServIntensty = avgRespSize = reqsPerSec =
        succRespPerSec = failsPerSec = pendingReqs = 0;
    respHistory = new TLongObjectHashMap<TIntIntHashMap>();
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
    respHistory = new TLongObjectHashMap<TIntIntHashMap>(live.respHistory);
  }
  @Override public String toString() {
    return String.format("%s %d %d %d %d",
        name, reqsPerSec, succRespPerSec, failsPerSec, pendingReqs);
  }
}
