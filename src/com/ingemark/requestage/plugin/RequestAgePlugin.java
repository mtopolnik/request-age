package com.ingemark.requestage.plugin;

import static com.ingemark.requestage.Util.gridData;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.FontMetrics;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import com.ibm.icu.text.DecimalFormat;

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
    EVT_SCRIPTS_RUNNING = 1028,
    STATS_EVTYPE_BASE = 2048;
  static RequestAgePlugin instance;
  private static final DecimalFormat threeDigitFormat = new DecimalFormat("@##");
  private Bundle bundle;

  private static final class EventShellHolder {
    private EventShellHolder() {}
    static final Shell SHELL = new Shell(Display.getDefault());
    static final int LINE_HEIGHT, AVG_CHAR_WIDTH;
    static {
      final GC gc = new GC(globalEventHub());
      final FontMetrics fm = gc.getFontMetrics();
      LINE_HEIGHT = fm.getHeight();
      AVG_CHAR_WIDTH = fm.getAverageCharWidth();
      gc.dispose();
    }
  }
  public static Shell globalEventHub() {
    return EventShellHolder.SHELL.isDisposed()? null : EventShellHolder.SHELL;
  }
  public static int lineHeight() { return EventShellHolder.LINE_HEIGHT; }
  public static int averageCharWidth() { return EventShellHolder.AVG_CHAR_WIDTH; }

  public static String threeDigitFormat(float f) { return threeDigitFormat(f, true); }
  public static String threeDigitFormat(float f, boolean withSpace) {
    final String optSpace = withSpace? " " : "";
    return f == 0? "0"
         : f >= 1000000? threeDigitFormat.format(f/1000000) + optSpace + "M"
         : f >= 1000? threeDigitFormat.format(f/1000) + optSpace + "k"
         : f >= 1? threeDigitFormat.format(f) + optSpace
         : threeDigitFormat.format(f*1000) + optSpace + "m";
  }

  @Override public void start(BundleContext context) throws Exception {
    instance = this;
    bundle = context.getBundle();
  }
  @Override public void stop(BundleContext context) throws Exception {
    instance = null;
  }
  public static RequestAgePlugin requestAgePlugin() {
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

  public static Button okButton(final Shell parent, final boolean disposeOnClose) {
    final Button ok = new Button(parent, SWT.NONE);
    parent.setDefaultButton(ok);
    ok.setText("OK");
    gridData().align(SWT.RIGHT, SWT.FILL).applyTo(ok);
    parent.addListener(SWT.Traverse, new Listener() { public void handleEvent(Event event) {
      if (event.detail != SWT.TRAVERSE_ESCAPE) return;
      if (disposeOnClose) parent.close(); else parent.setVisible(false);
      event.detail = SWT.TRAVERSE_NONE;
      event.doit = false;
    }});
    ok.addSelectionListener(new SelectionListener() {
      @Override public void widgetSelected(SelectionEvent e) {
        if (disposeOnClose) parent.close(); else parent.setVisible(false);
      }
      @Override public void widgetDefaultSelected(SelectionEvent e) {}
    });
    return ok;
  }
}
