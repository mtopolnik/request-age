package com.ingemark.perftest.plugin;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator
{
  static Activator instance;
  private Bundle bundle;

  @Override public void start(BundleContext context) throws Exception {
    instance = this;
    bundle = context.getBundle();
  }
  @Override public void stop(BundleContext context) throws Exception {
    instance = null;
  }
  public static Activator stressTestPlugin() {
    return instance;
  }
  public Bundle bundle() { return bundle; }
}
