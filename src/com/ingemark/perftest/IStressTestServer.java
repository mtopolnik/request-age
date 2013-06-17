package com.ingemark.perftest;

import com.ingemark.perftest.plugin.ui.ProgressDialog.ProgressMonitor;


public interface IStressTestServer {
  void intensity(int intensity);
  void shutdown();
  void send(Message msg);
  Thread start(ProgressMonitor ipm);
}