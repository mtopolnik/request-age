package com.ingemark.perftest;

import static com.ingemark.perftest.Message.DIVISOR;
import static com.ingemark.perftest.Message.ERROR;
import static com.ingemark.perftest.Message.EXCEPTION;
import static com.ingemark.perftest.Message.INIT;
import static com.ingemark.perftest.Message.INITED;
import static com.ingemark.perftest.Message.INTENSITY;
import static com.ingemark.perftest.Message.SHUTDOWN;
import static com.ingemark.perftest.Message.STATS;
import static com.ingemark.perftest.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.perftest.StressTester.launchTester;
import static com.ingemark.perftest.Util.arraySum;
import static com.ingemark.perftest.Util.nettySend;
import static com.ingemark.perftest.Util.now;
import static com.ingemark.perftest.Util.swtSend;
import static com.ingemark.perftest.Util.toIndex;
import static com.ingemark.perftest.plugin.StressTestPlugin.EVT_ERROR;
import static com.ingemark.perftest.plugin.StressTestPlugin.EVT_INIT_HIST;
import static java.lang.Math.max;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.eclipse.debug.core.DebugPlugin.newProcess;
import static org.eclipse.debug.core.ILaunchManager.RUN_MODE;
import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.channel.Channels.pipelineFactory;
import static org.jboss.netty.handler.codec.serialization.ClassResolvers.softCachingResolver;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.InetSocketAddress;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.Launch;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;
import org.slf4j.Logger;

import com.ingemark.perftest.plugin.StressTestPlugin;
import com.ingemark.perftest.plugin.ui.InfoDialog;
import com.ingemark.perftest.plugin.ui.ProgressDialog.ProgressMonitor;

public class StressTestServer implements IStressTestServer
{
  static final Logger log = getLogger(StressTestServer.class);
  public static final int NETTY_PORT = 49131;
  private static final int NS_TO_MS = 1_000_000;
  private final Control eventReceiver;
  private final String filename;
  private final AtomicBoolean shuttingDown = new AtomicBoolean();
  private volatile ServerBootstrap netty;
  private volatile Channel channel;
  private volatile int[] refreshTimes;
  private volatile int maxRefreshTime, refreshDivisor = 1;
  private volatile long guiSlowSince, guiFastSince;
  private volatile int workIncrement = 256;
  private volatile Thread initThread;
  private volatile int refreshTimeslot = Integer.MIN_VALUE;
  private volatile Process subprocess;
  private volatile ProgressMonitor pm;

  public StressTestServer(Control c, String filename) {
    this.eventReceiver = c;
    this.filename = filename;
  }

  @Override public void start() {
    this.initThread = currentThread();
    try {
      pm.subTask("Starting netty");
      this.netty = netty();
      pm.worked(20);
      pm.subTask("Launching subprocess");
      this.subprocess = launchTester(filename);
      this.initThread = null;
      final Launch launch = new Launch(null, RUN_MODE, null);
      newProcess(launch, subprocess, "Stress Test");
      DebugPlugin.getDefault().getLaunchManager().addLaunch(launch);
      pm.worked(20);
      pm.subTask("Listening for messages from the subprocess");
      log.debug("Listening for messages from the subprocess");
    } catch (Throwable t) { pm.done(); }
  }

  @Override public StressTestServer progressMonitor(ProgressMonitor ipm) {
    this.pm = ipm; return this;
  }

  @Override public void send(Message msg) { nettySend(channel, msg); }

  @Override public void intensity(int intensity) { send(new Message(INTENSITY, intensity)); }

  private boolean shutdownDone(Runnable andThen) {
    final boolean stillUp = shuttingDown.getAndSet(false);
    if (stillUp) {
      andThen.run();
      if (pm.isCanceled()) pm.done();
    }
    return !stillUp;
  }

  ServerBootstrap netty() {
    log.info("Starting Server Netty");
    final ServerBootstrap b = new ServerBootstrap(
        new NioServerSocketChannelFactory(newCachedThreadPool(),newCachedThreadPool()));
    b.setPipelineFactory(pipelineFactory(pipeline(
      new ObjectDecoder(softCachingResolver(getClass().getClassLoader())),
      new SimpleChannelHandler() {
        @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
          final Message msg = (Message)e.getMessage();
          switch (msg.type) {
          case INIT:
            if (pm.isCanceled()) break;
            pm.subTask("Awaiting response to " + msg.value.toString());
            pm.worked((workIncrement >>= 1) == 128? 20 : workIncrement);
            break;
          case INITED:
            channel = ctx.getChannel();
            refreshDivisorChanged();
            if (!pm.isCanceled()) {
              pm.done();
              swtSend(EVT_INIT_HIST, msg.value);
            }
            break;
          case ERROR:
            swtSend(EVT_ERROR, msg.value);
            break;
          case EXCEPTION:
            InfoDialog.show((DialogInfo) msg.value);
            break;
          case STATS:
            receivedStats((Stats[])msg.value);
            break;
          }
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) {
          log.error("Netty error", e.getCause());
        }}
      , new ObjectEncoder()
      )));
    b.bind(new InetSocketAddress("localhost", NETTY_PORT));
    log.info("Listening on " + NETTY_PORT);
    return b;
  }

  @Override public void shutdown(final Runnable andThen) {
    if (shuttingDown.getAndSet(true))
      throw new RuntimeException("Called StressTestServer#shutdown while shutdown in progress");
    final ScheduledExecutorService sched = newScheduledThreadPool(2);
    try { nettySend(channel, new Message(SHUTDOWN, 0), true); }
    catch (Throwable t) { log.warn("Failed to send shutdown message to stress tester", t); }
    sched.schedule(new Runnable() { public void run() {
      try {
        if (initThread != null) {
          pm.subTask("Aborting any startup procedure");
          log.debug("Interrupting startup thread");
          initThread.interrupt();
          initThread.join(SECONDS.toMillis(5));
        }
      }
      catch (Throwable t) { log.error("Joining init thread interrupted", t); }
      pm.worked(5);
      if (subprocess != null) try {
        pm.subTask("Waiting for the subprocess to end");
        log.debug("Waiting for the Stress Tester subprocess to end");
        subprocess.waitFor();
      }
      catch (Throwable t) { log.error("Waiting for subprocess interrupted", t); }
      pm.worked(5);
      pm.subTask("Shutting down netty");
      if (netty == null) return;
      try {
        log.debug("Shutting down netty");
        netty.shutdown();
        netty.releaseExternalResources();
      }
      catch (Throwable t) { log.error("Error while stopping netty", t); }
      pm.subTask("Shutdown complete");
      pm.worked(5);
      shutdownDone(andThen);
      log.debug("Shutdown done");
      sched.shutdown();
    }}, 0, TimeUnit.SECONDS);
    try {
      sched.schedule(new Runnable() { @Override public void run() {
        if (!shutdownDone(andThen)) try {
          log.debug("Shutdown still not done. Destroying subprocess.");
          subprocess.destroy();
        }
        catch (Throwable t) { log.error("Error destroying stress tester subprocess", t); }
      }}, initThread!=null && initThread.isAlive()? 10 : 5, TimeUnit.SECONDS);
    } catch (RejectedExecutionException e) {}
  }

  void refreshDivisorChanged() {
    send(new Message(DIVISOR, refreshDivisor));
    refreshTimes = new int[(5 * TIMESLOTS_PER_SEC) / refreshDivisor];
    maxRefreshTime = (980 * refreshDivisor) / TIMESLOTS_PER_SEC;
  }

  void receivedStats(final Stats[] stats) {
    final long enqueuedAt = now()/NS_TO_MS;
    Display.getDefault().asyncExec(new Runnable() {
      public void run() {
        final long start = now()/NS_TO_MS;
        if (eventReceiver.isDisposed()) return;
        for (Stats s : stats) {
          final Event e = new Event();
          e.data = s;
          eventReceiver.notifyListeners(StressTestPlugin.STATS_EVTYPE_BASE + s.index, e);
        }
        final Rectangle area = eventReceiver.getBounds();
        eventReceiver.redraw(0, 0, area.width, area.height, true);
//        eventReceiver.update();
        final long end = now()/NS_TO_MS;
        final int elapsed = (int)(end-start), timeInQueue = (int)(start-enqueuedAt);
        refreshTimes[toIndex(refreshTimes, refreshTimeslot++)] = elapsed;
        final int avgRefresh = arraySum(refreshTimes)/refreshTimes.length;
        if (!adjustSlowGui(end, avgRefresh, timeInQueue))
          adjustFastGui(end, avgRefresh, timeInQueue);
        if (refreshTimeslot % ((5*TIMESLOTS_PER_SEC)/refreshDivisor) == 0)
          log.debug("timeInQueue {} avgRefresh {} refreshDivisor {}",
              timeInQueue, avgRefresh, refreshDivisor);
      }
      boolean adjustSlowGui(long now, int avgRefresh, int timeInQueue) {
        if (timeInQueue < 200) { guiSlowSince = 0; return false; }
        if (refreshDivisor >= TIMESLOTS_PER_SEC) return true;
        if (guiSlowSince == 0) { guiSlowSince = now; return true; }
        if (now-guiSlowSince > 5000) {
          guiSlowSince = 0;
          refreshDivisor = max(refreshDivisor+1, (avgRefresh*TIMESLOTS_PER_SEC)/1000);
          refreshDivisorChanged();
          log.debug("Reducing refresh rate");
        }
        return true;
      }
      void adjustFastGui(long now, int avgRefresh, long timeInQueue) {
        if (refreshDivisor <= 1) return;
        final double d = refreshDivisor;
        if (timeInQueue > 100 || avgRefresh * (d/(d-1)) > maxRefreshTime) {
          guiFastSince = 0; return;
        }
        if (guiFastSince == 0) { guiFastSince = now; return; }
        if (now-guiFastSince > 5000) {
          guiFastSince = 0;
          refreshDivisor--;
          refreshDivisorChanged();
          log.debug("Increasing refresh rate");
        }
      }
    });
  }

  public static final IStressTestServer NULL = new IStressTestServer() {
    @Override public void start() { }
    @Override public void intensity(int intensity) { }
    @Override public void shutdown(Runnable r) { r.run(); }
    @Override public void send(Message msg) { }
    @Override public IStressTestServer progressMonitor(ProgressMonitor ipm) { return this; }
  };
}
