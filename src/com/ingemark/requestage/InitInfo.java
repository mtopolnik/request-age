package com.ingemark.requestage;

import java.io.Serializable;
import java.util.Map;

public class InitInfo implements Serializable
{
  public final int maxThrottle;
  public final String[] histograms;

  public InitInfo(Map<String, LiveStats> stats, int maxThrottle) {
    this.histograms = new String[stats.size()];
    for (LiveStats ls : stats.values()) histograms[ls.index] = ls.name;
    this.maxThrottle = maxThrottle;
  }
}
