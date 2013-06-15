package com.ingemark.perftest;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Shell;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.ringojs.wrappers.ScriptableList;
import org.ringojs.wrappers.ScriptableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingemark.perftest.plugin.ui.RequestAgeView;
import com.ning.http.client.Response;

public class Util
{
  static final Logger log = LoggerFactory.getLogger(Util.class);

  public static int toIndex(int[] array, int timeSlot) {
    return toIndex(array.length, timeSlot);
  }
  public static int toIndex(char[] array, int timeSlot) {
    return toIndex(array.length, timeSlot);
  }
  private static int toIndex(int length, int timeSlot) {
    int slot = timeSlot%length;
    return slot >= 0? slot : slot + length;
  }
  public static int arraySum(int[] array) {
    int sum = 0;
    for (int cnt : array) sum += cnt;
    return sum;
  }
  public static long now() { return System.nanoTime(); }

  public static String join(String separator, String... parts) {
    final StringBuilder b = new StringBuilder(128);
    String sep = "";
    for (String part : parts) { b.append(sep).append(part); sep = separator; }
    return b.toString();
  }
  public static boolean isSuccessResponse(Response r) {
    return r != null && r.getStatusCode() >= 200 && r.getStatusCode() < 400;
  }
  public static void nettySend(Channel channel, final Message msg, boolean sync) {
    if (channel == null) {
      log.error("Attempt to send message without connected peer: " + msg);
      return;
    }
    final ChannelFuture fut = channel.write(msg);
    fut.addListener(new ChannelFutureListener() {
      @Override public void operationComplete(ChannelFuture f) {
        if (!f.isSuccess()) log.error("Failed to send message {}", msg, f.getCause());
      }
    });
    if (sync) try { fut.await(); } catch (InterruptedException e) { sneakyThrow(e); }
  }
  public static void nettySend(Channel channel, Message msg) {
    nettySend(channel, msg, false);
  }
  public static <T> T spy(String msg, T ret) { log.debug("{}: {}", msg, ret); return ret; }

  static void swtSend(final int evtype, final Serializable value) {
    Display.getDefault().asyncExec(new Runnable() { public void run() {
      final Event e = new Event();
      e.data = value;
      RequestAgeView.instance.statsParent.notifyListeners(evtype, e);
    }});
  }
  public static <R> R sneakyThrow(Throwable t) {
    return Util.<RuntimeException, R>sneakyThrow0(t);
  }
  @SuppressWarnings("unchecked")
  private static <E extends Throwable, R> R sneakyThrow0(Throwable t) throws E { throw (E)t; }


  public static Object javaToJS(Object obj, Scriptable scope) {
    if (obj instanceof Scriptable) {
      if (obj instanceof ScriptableObject
          && ((Scriptable) obj).getParentScope() == null
          && ((Scriptable) obj).getPrototype() == null) {
        ScriptRuntime.setObjectProtoAndParent((ScriptableObject) obj, scope);
      }
      return obj;
    } else if (obj instanceof List) {
      return new ScriptableList(scope, (List) obj);
    } else if (obj instanceof Map) {
      return new ScriptableMap(scope, (Map) obj);
    } else {
      return Context.javaToJS(obj, scope);
    }
  }
  public static GridDataFactory gridData() { return GridDataFactory.fillDefaults(); }

  private static final String BUSYID_NAME = "SWT BusyIndicator";
  private static int nextBusyId = Integer.MIN_VALUE;
  public static Object nextBusyId() { return nextBusyId++; }
  public static void busyIndicator(boolean state, Object busyId) {
    final Display disp = Display.getCurrent();
    final Cursor cursorToSet = state? disp.getSystemCursor(SWT.CURSOR_WAIT) : null;
    for (Shell shell : disp.getShells())
      if (shell.getData(BUSYID_NAME) == (state? null : busyId)) {
        shell.setCursor(cursorToSet);
        shell.setData(BUSYID_NAME, state? busyId : null);
      }
  }
}
