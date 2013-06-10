package com.ingemark.perftest;

import static com.ingemark.perftest.StressTester.fac;
import static com.ingemark.perftest.Util.sneakyThrow;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Response;

public class JsHttp extends BaseFunction
{
  private final StressTester tester;
  private final Map<String, Acceptor> acceptors = hashMap(
    "all", new Acceptor() { public boolean acceptable(Response r) { return true; } },
    "success", new Acceptor() { public boolean acceptable(Response r) {
      final int st = r.getStatusCode();
      return st >= 200 && st < 400;
    }},
    "ok", new Acceptor() { public boolean acceptable(Response r) {
      final int st = r.getStatusCode();
      return st >= 200 && st < 300;
    }}
  );
  volatile int index;
  volatile Acceptor acceptor = acceptors.get("all");
  private final Map<String, Callable> methods = httpMethods(
      "get", "put", "post", "delete", "head", "options"
  );

  public JsHttp(ScriptableObject global, StressTester tester) {
    super(global, getFunctionPrototype(global));
    this.tester = tester;
    methods.put("accept", new Callable() {
      public Object call(Context _1, Scriptable _2, Scriptable _3, Object[] args) {
        return acceptor = acceptors.get(args[0]);
      }
    });
  }

  public void initDone() { index = -1; }

  @Override public Object get(final String name, Scriptable start) {
    final Callable c = methods.get(name);
    return c != null? c : super.get(name, start);
  }
  @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    return new ReqBuilder((String)args[0]);
  }
  @Override public int getArity() { return 1; }

  public class ReqBuilder {
    final String name;
    BoundRequestBuilder brb;
    private Acceptor acceptor = JsHttp.this.acceptor;

    public ReqBuilder() { this(null); }
    public ReqBuilder(String name) { this.name = name; }
    public ReqBuilder(String method, String url) { this(null); brb(method, url); }

    public ReqBuilder get(String url) { return brb("GET", url); }
    public ReqBuilder put(String url) { return brb("PUT", url); }
    public ReqBuilder post(String url) { return brb("POST", url); }
    public ReqBuilder delete(String url) { return brb("DELETE", url); }
    public ReqBuilder head(String url) { return brb("HEAD", url); }
    public ReqBuilder options(String url) { return brb("OPTIONS", url); }

    public ReqBuilder body(Object body) { brb.setBody(body.toString()); return this; }

    public ReqBuilder accept(String qualifier) {
      acceptor = acceptors.get(qualifier);
      return this;
    }
    public boolean go(Callable f) {
      return index == -1? executeTest(this, f) : executeInit(this, f);
    }

    private ReqBuilder brb(String method, String url) {
      brb = tester.client.prepareConnect(url).setMethod(method);
      return this;
    }
    private boolean executeInit(ReqBuilder reqBuilder, Callable f) {
      if (reqBuilder.name != null) {
        System.out.println("Adding " + reqBuilder.name + " under " + index);
        tester.lsmap.put(reqBuilder.name, new LiveStats(index++, reqBuilder.name));
      }
      try {
        final Response resp = tester.client.executeRequest(reqBuilder.brb.build()).get();
        return isRespSuccess(f, resp);
      } catch (InterruptedException | ExecutionException | IOException e) {
        throw new RuntimeException("Error during test initialization", e);
      }
    }
    private boolean executeTest(ReqBuilder reqBuilder, final Callable f) {
      final LiveStats liveStats = tester.lsmap.get(reqBuilder.name);
      final int startSlot = liveStats.registerReq();
      try {
        tester.client.executeRequest(reqBuilder.brb.build(), new AsyncCompletionHandlerBase() {
          @Override public Response onCompleted(final Response resp) throws IOException {
            return (Response) fac.call(new ContextAction() {
              @Override public Object run(Context cx) {
                cx.setOptimizationLevel(9);
                liveStats.deregisterReq(startSlot, isRespSuccess(f, resp));
                return resp;
              }
            });
          }
          @Override public void onThrowable(Throwable t) {
            liveStats.deregisterReq(startSlot, false);
          }
        });
        return true;
      } catch (IOException e) { return sneakyThrow(e); }
    }
    private boolean isRespSuccess(Callable f, Response resp) {
      return acceptor.acceptable(resp) &&
          (f == null || !Boolean.FALSE.equals(tester.jsScope.call(f, resp)));
    }
  }

  private static Map<String, Acceptor> hashMap(Object... kvs) {
    final Map<String, Acceptor> r = new HashMap<>();
    for (int i = 0; i < kvs.length;) r.put((String)kvs[i++], (Acceptor)kvs[i++]);
    return r;
  }

  private final Map<String, Callable> httpMethods(String... methods) {
    final Map<String, Callable> ret = new HashMap<>();
    for (final String m : methods) ret.put(m, new Callable() {
      public Object call(Context _1, Scriptable _2, Scriptable _3, Object[] args) {
        return new ReqBuilder(m, (String)args[0]);
      }
    });
    return ret;
  }
  interface Acceptor { boolean acceptable(Response r); }
}
