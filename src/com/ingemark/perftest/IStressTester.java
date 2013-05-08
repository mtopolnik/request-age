package com.ingemark.perftest;

public interface IStressTester {
  void setIntensity(int intensity);
  void shutdown();
  void runTest() throws Exception;
}