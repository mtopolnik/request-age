package com.ingemark.requestage;

import com.ingemark.requestage.plugin.ui.ProgressDialog.ProgressMonitor;


public interface IStressTestServer {
  String testName();
  void intensity(int intensity);
  void shutdown(Runnable andThen);
  void send(Message msg);
  void start();
  IStressTestServer progressMonitor(ProgressMonitor ipm);
}