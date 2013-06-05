package com.ingemark.perftest.script;

import static org.mozilla.javascript.Context.javaToJS;
import static org.mozilla.javascript.ScriptableObject.putProperty;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptableObject;

import com.ning.http.client.AsyncCompletionHandlerBase;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Request;
import com.ning.http.client.RequestBuilder;
import com.ning.http.client.Response;

public class JsTest
{
  private static final ContextFactory fac = ContextFactory.getGlobal();
  final ScriptableObject scope = initJsScope();
  final AsyncHttpClient client = new AsyncHttpClient();

  ScriptableObject initJsScope() {
    return (ScriptableObject) fac.call(new ContextAction() {
      public Object run(Context cx) {
        final ScriptableObject scope = cx.initStandardObjects(null, true);
        cx.evaluateString(scope,
            "RegExp; getClass; java; Packages; JavaAdapter;", "lazyLoad", 0, null);
        putProperty(scope, "jst", javaToJS(JsTest.this, scope));
        scope.sealObject();
        return scope;
      }
    });
  }

  public void http(String method, String url, final Function f) throws IOException {
    final Request request = new RequestBuilder(url).setMethod(method).build();
    client.executeRequest(request, new AsyncCompletionHandlerBase() {
      @Override public Response onCompleted(final Response resp) throws IOException {
        fac.call(new ContextAction() {
          @Override public Object run(Context cx) {
            f.call(cx, scope, null, new Object[] {resp});
            return null;
          }
        });
        return resp;
      }
      @Override public void onThrowable(Throwable t) {
      }
    });
  }

  public static void main(String[] args) {
    final Reader js = new InputStreamReader(JsTest.class.getResourceAsStream("test.js"));
    fac.call(new ContextAction() {
      @Override public Object run(Context cx) {
        final JsTest t = new JsTest();
        try { cx.evaluateReader(t.scope, js, "<here>", 1, null); }
        catch (IOException e) { e.printStackTrace(); }
        return null;
      }
    });
  }
}
