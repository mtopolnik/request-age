package com.ingemark.requestage.script;

import static com.ingemark.requestage.Util.sneakyThrow;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

public class UrlBuilder extends ScriptableObject
{
  private final StringBuilder b = new StringBuilder(16);
  private boolean withinQuery;

  UrlBuilder(Scriptable scope, Object[] args) {
    super(scope, getObjectPrototype(scope));
    putProperty(this, "s", new Callable() {
      @Override public Object call(Context _1, Scriptable _2, Scriptable thisObj, Object[] args) {
        segments(args);
        return thisObj;
      }
    });
    putProperty(this, "q", new Callable() {
      @Override public Object call(Context _1, Scriptable _2, Scriptable thisObj, Object[] args) {
        queryParams(args);
        return thisObj;
      }
    });
    b.append(args[0]);
  }

  private void segments(Object[] segs) {
    if (withinQuery)
      throw new IllegalStateException("Cannot add path segments after query params");
    for (Object seg : segs) {
      if (b.charAt(b.length()-1) != '/') b.append('/');
      if (seg != null) b.append(seg);
    }
  }
  private void queryParams(Object[] paramValuePairs) {
    for (int i = 0; i < paramValuePairs.length;) {
      b.append(withinQuery? '&' : '?');
      withinQuery = true;
      b.append(encode(paramValuePairs[i++].toString()))
       .append('=')
       .append(encode(paramValuePairs[i++].toString()));
    }
  }
  @Override public Object getDefaultValue(Class<?> typeHint) {
    return toString();
  }
  @Override public String toString() { return b.toString(); }

  static String encode(String s) {
    try { return URLEncoder.encode(s, "UTF-8"); }
    catch (UnsupportedEncodingException e) { return sneakyThrow(e); }
  }

  @Override public String getClassName() { return "UrlBuilder"; }
}
