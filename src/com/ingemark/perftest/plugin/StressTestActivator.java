package com.ingemark.perftest.plugin;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class StressTestActivator implements BundleActivator
{
  public static final String
    STRESSTEST_VIEW_ID = "com.ingemark.perftest.plugin.views.RequestAgeView";
  public static final int
    RUN_SCRIPT_EVTYPE = 1024,
    INIT_HIST_EVTYPE = 1025,
    STATS_EVTYPE_BASE = 2048;
  static StressTestActivator instance;
  private Bundle bundle;

  @Override public void start(BundleContext context) throws Exception {
    instance = this;
    bundle = context.getBundle();
  }
  @Override public void stop(BundleContext context) throws Exception {
    instance = null;
  }
  public static StressTestActivator stressTestPlugin() {
    return instance;
  }
  public Bundle bundle() { return bundle; }
}
