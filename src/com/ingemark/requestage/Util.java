package com.ingemark.requestage;

import static java.lang.Math.log10;
import static java.lang.Math.pow;
import static java.lang.Math.round;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.eclipse.ui.PlatformUI.getWorkbench;

import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.ringojs.wrappers.ScriptableList;
import org.ringojs.wrappers.ScriptableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingemark.requestage.script.NativeJavaObjectPreserveThis;
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
  public static double arrayMean(float[] array) {
    return arrayMean(array, array.length);
  }
  public static double arrayMean(float[] array, int limit) {
    double avg = 0;
    for (int i = 0; i < limit; i++) avg += (double) array[i] / limit;
    return avg;
  }
  public static double arrayStdev(double avg, float[] array) {
    double sumSq = 0;
    for (double d : array) {
      final double dev = d - avg;
      sumSq += dev * (dev/(double) array.length);
    }
    return Math.sqrt(sumSq);
  }
  public static float[] reciprocalArray(float[] array) {
    final float[] ret = new float[array.length];
    for (int i = 0; i < array.length; i++) ret[i] = array[i] == 0? 0 : 1/array[i];
    return ret;
  }
  public static long now() { return System.nanoTime(); }

  public static int encodeElapsedMillis(long now, long start) {
    long elapsedMillis = (now-start)/MILLISECONDS.toNanos(1);
    double scaledLog = log10(elapsedMillis)*20;
    return (int)round(scaledLog);
  }
  public static int decodeElapsedMillis(int encoded) {
    return (int) pow(10, encoded/20.0);
  }
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

  static void swtSend(final Control eventReceiver, final int evtype, final Serializable value) {
    Display.getDefault().asyncExec(new Runnable() { public void run() {
      eventReceiver.notifyListeners(evtype, event(value));
    }});
  }
  public static Event event(Object data) {
    final Event e = new Event(); e.data = data; return e;
  }
  public static <R> R sneakyThrow(Throwable t) {
    return Util.<RuntimeException, R>sneakyThrow0(t);
  }
  @SuppressWarnings("unchecked")
  private static <E extends Throwable, R> R sneakyThrow0(Throwable t) throws E { throw (E)t; }

  public static <T> T spy(String msg, final T r) {
    final Class<?> c = r.getClass();
    log.debug(msg + " {}", c.isArray()? new Object() {
      public String toString() { return c.getComponentType().isPrimitive()?
                                 Arrays.toString((double[])r) : Arrays.toString((Object[])r);}}
      : r);
    return r;
  }

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
  public static NativeJavaObject builderWrapper(Scriptable scope, Object wrapped, Object proto) {
    final NativeJavaObject wrapper = new NativeJavaObjectPreserveThis(scope, wrapped);
    if (proto != null)
      wrapper.setPrototype(proto instanceof Scriptable?
          (Scriptable)proto : new NativeJavaObjectPreserveThis(scope, proto, wrapper));
    return wrapper;
  }
  public static NativeJavaObject builderWrapper(Scriptable scope, Object wrapped) {
    return builderWrapper(scope, wrapped, new NativeObject());
  }
  public static GridDataFactory gridData() { return GridDataFactory.fillDefaults(); }

  public static void showView(String id) {
    try {
      getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(id);
    } catch (Exception e) { sneakyThrow(e); }
  }

  public static String excToString(Throwable t) {
    if (t == null) return "";
    final StringWriter sw = new StringWriter(256);
    t.printStackTrace(new PrintWriter(sw));
    return sw.toString();
  }
  public static Color color(int id) { return Display.getCurrent().getSystemColor(id); }
}
