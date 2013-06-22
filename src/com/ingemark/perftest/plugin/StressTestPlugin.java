package com.ingemark.perftest.plugin;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class StressTestPlugin extends AbstractUIPlugin
{
  public static final String
    STRESSTEST_VIEW_ID = "com.ingemark.perftest.plugin.views.RequestAgeView";
  public static final int
    EVT_RUN_SCRIPT = 1024,
    EVT_INIT_HIST = 1025,
    EVT_ERROR = 1026,
    STATS_EVTYPE_BASE = 2048;
  static StressTestPlugin instance;
  private Bundle bundle;

  @Override public void start(BundleContext context) throws Exception {
    instance = this;
    bundle = context.getBundle();
  }
  @Override public void stop(BundleContext context) throws Exception {
    instance = null;
  }
  public static StressTestPlugin stressTestPlugin() {
    return instance;
  }
  public Bundle bundle() { return bundle; }

  public Image getImage(String name) {
    final ImageRegistry r = getImageRegistry();
    final Image cached = r.get(name);
    if (cached != null) return cached;
    r.put(name, imageDescriptor(name));
    return r.get(name);
  }
  public ImageDescriptor imageDescriptor(String name) {
    return ImageDescriptor.createFromURL(
        FileLocator.find(bundle, new Path("img/" + name), null));
  }
}
