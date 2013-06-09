package com.ingemark.perftest;

import static com.ingemark.perftest.Util.toProxyServer;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

import org.mozilla.javascript.Callable;

import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;

public class JsInitHttp extends JsHttp
{
  JsInitHttp(StressTester tester) { super(tester); }

  volatile int index;

  public void proxy(AsyncHttpClientConfig.Builder b, String proxyStr) {
    b.setProxyServer(toProxyServer(proxyStr));
  }

  @Override boolean execute(ReqBuilder reqBuilder, Callable f) {
    if (reqBuilder.name != null) {
      System.out.println("Adding " + reqBuilder.name + " under " + index);
      tester.lsmap.put(reqBuilder.name, new LiveStats(index++, reqBuilder.name));
    }
    try {
      final Response resp = tester.client.executeRequest(reqBuilder.brb.build()).get();
      return f == null || !Boolean.FALSE.equals(tester.jsScope.call(f, resp));
    } catch (InterruptedException | ExecutionException | IOException e) {
      throw new RuntimeException("Error during test initialization", e);
    }
  }
}