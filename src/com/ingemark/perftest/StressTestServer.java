package com.ingemark.perftest;

import static com.ingemark.perftest.Message.DIVISOR;
import static com.ingemark.perftest.Message.ERROR;
import static com.ingemark.perftest.Message.EXCEPTION;
import static com.ingemark.perftest.Message.INIT;
import static com.ingemark.perftest.Message.INTENSITY;
import static com.ingemark.perftest.Message.SHUTDOWN;
import static com.ingemark.perftest.Message.STATS;
import static com.ingemark.perftest.StressTester.TIMESLOTS_PER_SEC;
import static com.ingemark.perftest.Util.arraySum;
import static com.ingemark.perftest.Util.excToString;
import static com.ingemark.perftest.Util.nettySend;
import static com.ingemark.perftest.Util.now;
import static com.ingemark.perftest.Util.swtSend;
import static com.ingemark.perftest.Util.toIndex;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_ERROR;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_INIT_HIST;
import static java.lang.Math.max;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static org.eclipse.jface.dialogs.MessageDialog.openInformation;
import static org.jboss.netty.channel.Channels.pipeline;
import static org.jboss.netty.channel.Channels.pipelineFactory;
import static org.jboss.netty.handler.codec.serialization.ClassResolvers.softCachingResolver;
import static org.slf4j.LoggerFactory.getLogger;

import java.net.InetSocketAddress;

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

import com.ingemark.perftest.plugin.StressTestActivator;

public class StressTestServer implements IStressTestServer
{
  static final Logger log = getLogger(StressTestServer.class);
  public static final int NETTY_PORT = 49131;
  private static final int NS_TO_MS = 1_000_000;
  private final Control eventReceiver;
  private volatile Channel channel;
  private final ServerBootstrap netty;
  private int refreshTimeslot = Integer.MIN_VALUE;
  private volatile int[] refreshTimes;
  private volatile int maxRefreshTime, refreshDivisor = 1;
  private volatile long guiSlowSince, guiFastSince;

  public StressTestServer(Control c) {
    this.eventReceiver = c;
    this.netty = netty();
  }

  @Override public void send(Message msg) { nettySend(channel, msg); }

  @Override public void intensity(int intensity) { send(new Message(INTENSITY, intensity)); }

  @Override public void shutdown() {
    try { nettySend(channel, new Message(SHUTDOWN, 0), true); }
    catch (Throwable t) {}
    netty.shutdown();
  };

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
            channel = ctx.getChannel();
            refreshDivisorChanged();
            swtSend(EVT_INIT_HIST, msg.value);
            break;
          case ERROR:
            swtSend(EVT_ERROR, msg.value);
            break;
          case EXCEPTION:
            Display.getDefault().asyncExec(new Runnable() { public void run() {
              openInformation(null, "Last Reported Exception",
                  excToString((Throwable)msg.value));
            }});
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
          eventReceiver.notifyListeners(StressTestActivator.STATS_EVTYPE_BASE + s.index, e);
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
    @Override public void intensity(int intensity) { }
    @Override public void shutdown() { }
    @Override public void send(Message msg) { }
  };
}
