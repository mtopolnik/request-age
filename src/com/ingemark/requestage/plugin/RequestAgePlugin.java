package com.ingemark.requestage.plugin;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

public class RequestAgePlugin extends AbstractUIPlugin
{
  public static final String
    REQUESTAGE_VIEW_ID = "com.ingemark.requestage.views.RequestAgeView",
    HISTORY_VIEW_ID = "com.ingemark.requestage.views.HistoryView";
  public static final int
    EVT_RUN_SCRIPT = 1024,
    EVT_INIT_HIST = 1025,
    EVT_ERROR = 1026,
    EVT_REPORT = 1027,
    EVT_HISTORY_UPDATE = 1028,
    STATS_EVTYPE_BASE = 2048;
  static RequestAgePlugin instance;
  private Bundle bundle;

  private static final class EventShellHolder {
    private EventShellHolder() {}
    private static final Shell SHELL = new Shell(Display.getDefault());
  }
  public static Shell globalEventHub() {
    return EventShellHolder.SHELL.isDisposed()? null : EventShellHolder.SHELL;
  }

  @Override public void start(BundleContext context) throws Exception {
    instance = this;
    bundle = context.getBundle();
  }
  @Override public void stop(BundleContext context) throws Exception {
    instance = null;
  }
  public static RequestAgePlugin stressTestPlugin() {
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
