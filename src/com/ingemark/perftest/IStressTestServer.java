package com.ingemark.perftest;

import com.ingemark.perftest.plugin.ui.ProgressDialog.ProgressMonitor;


public interface IStressTestServer {
  void intensity(int intensity);
  void shutdown(Runnable andThen);
  void send(Message msg);
  void start();
  IStressTestServer progressMonitor(ProgressMonitor ipm);
}