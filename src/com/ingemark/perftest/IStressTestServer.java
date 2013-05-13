package com.ingemark.perftest;

public interface IStressTestServer {
  void intensity(int intensity);
  void shutdown();
}