package com.ingemark.perftest;

import static com.ingemark.perftest.StressTester.fac;
import static com.ingemark.perftest.Util.sneakyThrow;
import static com.ingemark.perftest.script.JsFunctions.parseXml;
import static com.ingemark.perftest.script.JsScope.FAILED_RESPONSE;
import static org.mozilla.javascript.Context.getCurrentContext;
import static org.mozilla.javascript.Context.javaToJS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.JavaScriptException;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ingemark.perftest.script.JdomBuilder;
import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.Response;

public class JsHttp extends BaseFunction
{
  private static final Logger log = LoggerFactory.getLogger(JsHttp.class);
  private final StressTester tester;
  private final ScriptableObject JSON;
  private final Callable stringify, parse;
  private final Map<String, Acceptor> acceptors = hashMap(
    "any", new Acceptor() { public boolean acceptable(Response r) { return true; } },
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

  public JsHttp(ScriptableObject parentScope, StressTester tester) {
    super(parentScope, getFunctionPrototype(parentScope));
    this.tester = tester;
    JSON = (ScriptableObject)parentScope.get("JSON");
    stringify = (Callable)JSON.get("stringify");
    parse = (Callable)JSON.get("parse");
    defineHttpMethods("get", "put", "post", "delete", "head", "options");
    putProperty(this, "acceptableStatus", new Callable() {
      public Object call(Context _1, Scriptable _2, Scriptable _3, Object[] args) {
        return acceptor = acceptors.get(args[0]);
    }});
  }

  public void initDone() { index = -1; }

  @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    return new ReqBuilder((String)args[0]);
  }
  @Override public int getArity() { return 1; }

  public class ReqBuilder {
    final String name;
    public BoundRequestBuilder brb;
    private Acceptor acceptor = JsHttp.this.acceptor;

    public ReqBuilder(String name) { this.name = name; }
    public ReqBuilder(String method, String url) { this(null); brb(method, url); }

    public ReqBuilder get(String url) { return brb("GET", url); }
    public ReqBuilder put(String url) { return brb("PUT", url); }
    public ReqBuilder post(String url) { return brb("POST", url); }
    public ReqBuilder delete(String url) { return brb("DELETE", url); }
    public ReqBuilder head(String url) { return brb("HEAD", url); }
    public ReqBuilder options(String url) { return brb("OPTIONS", url); }

    public ReqBuilder body(final Object body) {
      if (body instanceof JdomBuilder) {
        brb.addHeader("Content-Type", "text/xml;charset=UTF-8");
        brb.setBody(body.toString());
      } else if (body instanceof NativeObject) {
        brb.addHeader("Content-Type", "application/json;charset=UTF-8");
        fac.call(new ContextAction() { @Override public Object run(Context cx) {
          brb.setBody((String)stringify.call(cx, getParentScope(), JSON, new Object[] {body}));
          return null;
        }});
      }
      else brb.setBody(body.toString());
      return this;
    }

    public ReqBuilder accept(String qualifier) {
      acceptor = acceptors.get(qualifier);
      return this;
    }
    public boolean go(Callable f) {
      return index == -1? executeTest(this, f) : executeInit(this, f);
    }
    public boolean go() { return go(null); }

    private ReqBuilder brb(String method, String url) {
      brb = tester.client.prepareConnect(url).setMethod(method);
      return this;
    }
    private boolean executeInit(ReqBuilder reqBuilder, Callable f) {
      if (reqBuilder.name != null) {
        log.debug("Adding " + reqBuilder.name + " under " + index);
        tester.lsmap.put(reqBuilder.name, new LiveStats(index++, reqBuilder.name));
      }
      try {
        final Response resp = tester.client.executeRequest(reqBuilder.brb.build()).get();
        return isRespSuccess(f, resp);
      } catch (Exception e) { return sneakyThrow(e); }
    }
    private boolean executeTest(ReqBuilder reqBuilder, final Callable f) {
      final LiveStats liveStats = tester.lsmap.get(reqBuilder.name);
      final int startSlot = liveStats.registerReq();
      try {
        tester.client.executeRequest(reqBuilder.brb.build(), new AsyncCompletionHandlerBase() {
          @Override public Response onCompleted(final Response resp) throws IOException {
            return (Response) fac.call(new ContextAction() {
              @Override public Object run(Context cx) {
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
      try {
        return acceptor.acceptable(resp) &&
            (f == null || !Boolean.FALSE.equals(tester.jsScope.call(f, toJsResponse(resp))));
      } catch (JavaScriptException e) {
        final String m = e.getMessage();
        log.debug("JavaScript Exception {}---\n{}", m, m.startsWith(FAILED_RESPONSE));
        if (m != null && m.startsWith(FAILED_RESPONSE)) return false;
        else throw e;
      }
    }
  }

  Scriptable toJsResponse(Response r) {
    final Scriptable br = (Scriptable) javaToJS(new BetterResponse(r), getParentScope());
    br.setPrototype((Scriptable) javaToJS(r, getParentScope()));
    return br;
  }
  public class BetterResponse {
    private final Response r;
    BetterResponse(Response r) { this.r = r; }
    public Object xmlBody() { return parseXml(this.r); }
    public Object jsonBody() { return parse.call(getCurrentContext(), getParentScope(),
        JSON, new Object[] { responseBody(this.r) }); }
    public String stringBody() { return responseBody(this.r); }
  }

  static String responseBody(Response r) {
    try { return r.getResponseBody(); } catch (IOException e) { return sneakyThrow(e); }
  }

  private static Map<String, Acceptor> hashMap(Object... kvs) {
    final Map<String, Acceptor> r = new HashMap<>();
    for (int i = 0; i < kvs.length;) r.put((String)kvs[i++], (Acceptor)kvs[i++]);
    return r;
  }

  private void defineHttpMethods(String... methods) {
    for (final String m : methods) putProperty(this, m, new Callable() {
      public Object call(Context _1, Scriptable _2, Scriptable _3, Object[] args) {
        return new ReqBuilder(m, (String)args[0]);
      }
    });
  }

  interface Acceptor { boolean acceptable(Response r); }
}