package com.ingemark.perftest;

import static com.ingemark.perftest.StressTester.fac;
import static com.ingemark.perftest.Util.sneakyThrow;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.Scriptable;

import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Response;

public class JsHelper extends BaseFunction
{
  StressTester tester;
  JsHelper(StressTester tester) {
    super(tester.jsScope, getFunctionPrototype(tester.jsScope));
    this.tester = tester;
  }
  private final Map<String, Callable> httpMethods = httpMethods(
      "get", "put", "post", "delete", "head", "options"
  );

  @Override public Object get(final String name, Scriptable start) {
    final Callable c = httpMethods.get(name);
    return c != null? c : super.get(name, start);
  }
  @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    return new ReqBuilder((String)args[0]);
  }
  @Override public int getArity() { return 1; }

  public class ReqBuilder {
    final String name;
    BoundRequestBuilder brb;

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

    public boolean go(Callable f) { return http(this, f); }

    private ReqBuilder brb(String method, String url) {
      brb = tester.client.prepareConnect(url).setMethod(method);
      return this;
    }
  }

  boolean http(ReqBuilder reqBuilder, final Callable f) {
    final LiveStats liveStats = tester.lsmap.get(reqBuilder.name);
    final int startSlot = liveStats.registerReq();
    try {
      tester.client.executeRequest(reqBuilder.brb.build(), new AsyncCompletionHandlerBase() {
        @Override public Response onCompleted(final Response resp) throws IOException {
          return (Response) fac.call(new ContextAction() {
            @Override public Object run(Context cx) {
              cx.setOptimizationLevel(9);
              final Object res = f != null? tester.jsCall(f, resp) : null;
              liveStats.deregisterReq(startSlot, !Boolean.FALSE.equals(res));
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

  private final Map<String, Callable> httpMethods(String... methods) {
    final Map<String, Callable> ret = new HashMap<>();
    for (final String m : methods) ret.put(m, new Callable() {
      public Object call(Context _1, Scriptable _2, Scriptable _3, Object[] args) {
        return new ReqBuilder(m, (String)args[0]);
      }
    });
    return ret;
  }
}
