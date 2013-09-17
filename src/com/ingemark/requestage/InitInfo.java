package com.ingemark.requestage;

import java.io.Serializable;

public class InitInfo implements Serializable
{
  public final int histCount, maxThrottle;

  public InitInfo(int histCount, int maxThrottle) {
    this.histCount = histCount;
    this.maxThrottle = maxThrottle;
  }
}
