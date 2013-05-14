package com.ingemark.perftest;

import static com.ingemark.perftest.Message.DIVISOR;
import static com.ingemark.perftest.Message.INIT;
import static com.ingemark.perftest.Message.INTENSITY;
import static com.ingemark.perftest.Message.SHUTDOWN;
import static com.ingemark.perftest.Message.STATS;
import static com.ingemark.perftest.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.perftest.Util.arraySum;
import static com.ingemark.perftest.Util.now;
import static com.ingemark.perftest.Util.toIndex;
import static com.ingemark.perftest.plugin.StressTestActivator.INIT_HIST_EVTYPE;
import static java.lang.Math.max;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.channel.Channels.pipelineFactory;
import static org.jboss.netty.handler.codec.serialization.ClassResolvers.cacheDisabled;

import java.net.InetSocketAddress;

import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.jboss.netty.bootstrap.ServerBootstrap;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory;
import org.jboss.netty.handler.codec.serialization.ObjectDecoder;
import org.jboss.netty.handler.codec.serialization.ObjectEncoder;

import com.ingemark.perftest.plugin.StressTestActivator;
import com.ingemark.perftest.plugin.ui.RequestAgeView;

public class StressTestServer implements IStressTestServer
{
  private static final int NS_TO_MS = 1_000_000;
  final Control eventReceiver;
  final Channel channel;
  final ServerBootstrap netty;
  int refreshTimeslot = Integer.MIN_VALUE;
  volatile int[] refreshTimes;
  volatile int maxRefreshTime, refreshDivisor = 1;
  volatile long guiSlowSince, guiFastSince;

  public StressTestServer(Control c) {
    this.eventReceiver = c;
    this.netty = netty();
    this.channel = netty.bind(new InetSocketAddress(49131));
    refreshDivisorChanged();
  }

  public void intensity(int intensity) { channel.write(new Message(INTENSITY, intensity)); }

  public void shutdown() {
    final ChannelFuture fut = channel.write(new Message(SHUTDOWN, 0));
    try {fut.await();} catch (InterruptedException e) {}
    netty.shutdown();
  };

  ServerBootstrap netty() {
    final ServerBootstrap b = new ServerBootstrap(
        new NioServerSocketChannelFactory(newCachedThreadPool(),newCachedThreadPool()));
    b.setPipelineFactory(pipelineFactory(pipeline(
      new ObjectDecoder(cacheDisabled(StressTestServer.class.getClassLoader())),
      new SimpleChannelHandler() {
        @Override public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) {
          final Message msg = (Message)e.getMessage();
          System.out.println("Server received " + msg);
          switch (msg.type) {
          case INIT:
            final Event event = new Event();
            event.data = (int)msg.value;
            RequestAgeView.instance.statsParent.notifyListeners(INIT_HIST_EVTYPE, event);
            break;
          case STATS:
          try {
            final Stats[] stats = (Stats[])e.getMessage();
            System.out.println("Received stats " + stats);
          } catch (Throwable t) {t.printStackTrace();}
          break;
          }
        }
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
          System.err.println("Server netty error " + e.getCause().getMessage());
        }},
        new ObjectEncoder()
      )));
    return b;
  }

  void refreshDivisorChanged() {
    channel.write(new Message(DIVISOR, refreshDivisor));
    refreshTimes = new int[(5 * TIMESLOTS_PER_SEC) / refreshDivisor];
    maxRefreshTime = (980 * refreshDivisor) / TIMESLOTS_PER_SEC;
  }

  void receive(final Stats[] stats) {
    final long enqueuedAt = now()/NS_TO_MS;
    Display.getDefault().asyncExec(new Runnable() {
      public void run() {
        final long start = now()/NS_TO_MS;
        if (eventReceiver.isDisposed()) return;
        for (Stats s : stats) {
          final Event e = new Event();
          e.data = s;
          eventReceiver.notifyListeners(StressTestActivator.STATS_EVTYPE_BASE + s.index, e);
        }
        final long end = now()/NS_TO_MS;
        final int elapsed = (int)(end-start), timeInQueue = (int)(start-enqueuedAt);
        refreshTimes[toIndex(refreshTimes, refreshTimeslot++)] = elapsed;
        final int avgRefresh = arraySum(refreshTimes)/refreshTimes.length;
        if (!adjustSlowGui(end, avgRefresh, timeInQueue))
          adjustFastGui(end, avgRefresh, timeInQueue);
        if (refreshTimeslot % ((5*TIMESLOTS_PER_SEC)/refreshDivisor) == 0)
          System.out.format("timeInQueue %d avgRefresh %d refreshDivisor %d\n",
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
          System.out.println("Reducing refresh rate");
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
          System.out.println("Increasing refresh rate");
        }
      }
    });
  }

  public static final IStressTestServer NULL = new IStressTestServer() {
    @Override public void intensity(int intensity) { }
    @Override public void shutdown() { }
  };
}
