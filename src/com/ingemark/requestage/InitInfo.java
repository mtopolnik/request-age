package com.ingemark.requestage;

import java.io.Serializable;
import java.util.Map;

import com.ingemark.requestage.JsHttp.InitParams;

public class InitInfo implements Serializable
{
  public final int maxThrottle;
  public final boolean showRunningScriptCount;
  public final String[] reqNames;

  public InitInfo(Map<String, LiveStats> stats, InitParams ps) {
    this.reqNames = new String[stats.size()];
    for (LiveStats ls : stats.values()) reqNames[ls.index] = ls.name;
    this.maxThrottle = ps.maxThrottle;
    this.showRunningScriptCount = ps.showRunningScriptCount;
  }

  public InitInfo(String[] reqNames) {
    this.reqNames = reqNames;
    maxThrottle = 0; showRunningScriptCount = false;
  }
}
