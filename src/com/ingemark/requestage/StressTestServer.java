package com.ingemark.requestage;

import static com.ingemark.requestage.Message.ERROR;
import static com.ingemark.requestage.Message.EXCEPTION;
import static com.ingemark.requestage.Message.INIT;
import static com.ingemark.requestage.Message.INITED;
import static com.ingemark.requestage.Message.INTENSITY;
import static com.ingemark.requestage.Message.SHUTDOWN;
import static com.ingemark.requestage.Message.STATS;
import static com.ingemark.requestage.StressTester.launchTester;
import static com.ingemark.requestage.Util.event;
import static com.ingemark.requestage.Util.nettySend;
import static com.ingemark.requestage.Util.showView;
import static com.ingemark.requestage.Util.swtSend;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_ERROR;
import static com.ingemark.requestage.plugin.RequestAgePlugin.EVT_INIT_HIST;
import static com.ingemark.requestage.plugin.RequestAgePlugin.HISTORY_VIEW_ID;
import static com.ingemark.requestage.plugin.RequestAgePlugin.globalEventHub;
import static com.ingemark.requestage.plugin.ui.RequestAgeView.requestAgeView;
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

import java.io.File;
import java.net.InetSocketAddress;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.Launch;
import org.eclipse.swt.widgets.Display;
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

import com.ingemark.requestage.plugin.ui.InfoDialog;
import com.ingemark.requestage.plugin.ui.ProgressDialog.ProgressMonitor;
import com.ingemark.requestage.plugin.ui.StatsReceiver;

public class StressTestServer implements IStressTestServer
{
  static final Logger log = getLogger(StressTestServer.class);
  public static final int NETTY_PORT = 49131;
  public final String filename;
  private final AtomicBoolean shuttingDown = new AtomicBoolean();
  private final StatsReceiver statsReceiver = new StatsReceiver();
  private volatile ServerBootstrap netty;
  private volatile Channel channel;
  private volatile Thread initThread;
  private volatile int workIncrement = 256;
  private volatile Process subprocess;
  private volatile ProgressMonitor pm;

  public StressTestServer(String filename) {
    this.filename = filename;
  }

  @Override public String testName() { return new File(filename).getName(); }

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
            if (!pm.isCanceled()) {
              pm.done();
              Display.getDefault().asyncExec(new Runnable() { public void run() {
                requestAgeView.statsParent.notifyListeners(EVT_INIT_HIST, event(msg.value));
                globalEventHub().notifyListeners(EVT_INIT_HIST, event(msg.value));
                showView(HISTORY_VIEW_ID);
              }});
            }
            break;
          case ERROR:
            swtSend(requestAgeView.statsParent, EVT_ERROR, msg.value);
            break;
          case EXCEPTION:
            InfoDialog.show((DialogInfo) msg.value);
            break;
          case STATS:
            statsReceiver.receiveStats((StatsHolder)msg.value);
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
          if (subprocess != null) subprocess.destroy();
        }
        catch (Throwable t) { log.error("Error destroying stress tester subprocess", t); }
      }}, initThread!=null && initThread.isAlive()? 10 : 5, TimeUnit.SECONDS);
    } catch (RejectedExecutionException e) {}
  }


  public static final IStressTestServer NULL = new IStressTestServer() {
    @Override public void start() { }
    @Override public void intensity(int intensity) { }
    @Override public void shutdown(Runnable r) { r.run(); }
    @Override public void send(Message msg) { }
    @Override public IStressTestServer progressMonitor(ProgressMonitor ipm) { return this; }
    @Override public String testName() { return "NULL-testServer"; }
  };


  public static void main(String[] args) {
    final ServerBootstrap b = new ServerBootstrap(
        new NioServerSocketChannelFactory(newCachedThreadPool(),newCachedThreadPool()));
    b.setPipelineFactory(pipelineFactory(pipeline(
        new ObjectDecoder(softCachingResolver(StressTestServer.class.getClassLoader())),
        new SimpleChannelHandler(), new ObjectEncoder())));
    b.bind(new InetSocketAddress("127.0.0.1", NETTY_PORT));
    log.info("Listening on " + NETTY_PORT);
  }
}
