package com.ingemark.perftest.script;

import static com.ingemark.perftest.Util.sneakyThrow;
import static org.mozilla.javascript.ScriptableObject.DONTENUM;
import static org.mozilla.javascript.ScriptableObject.getTypedProperty;
import static org.mozilla.javascript.ScriptableObject.putProperty;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import org.jdom2.Text;
import org.mozilla.javascript.Callable;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextAction;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ContextFactory.Listener;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.ScriptRuntime;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.mozilla.javascript.WrapFactory;
import org.ringojs.wrappers.ScriptableList;
import org.ringojs.wrappers.ScriptableMap;

import com.ingemark.perftest.JsHttp;
import com.ingemark.perftest.StressTester;

public class JsScope {
  public static final String JS_LOGGER_NAME = "js";
  private static final ContextFactory fac = ContextFactory.getGlobal();
  private static final WrapFactory betterWrapFactory = new BetterWrapFactory();
  public final ScriptableObject global;
  public JsHttp jsHttp;

  public JsScope(final StressTester tester) {
    fac.addListener(new Listener() {
      @Override public void contextCreated(Context cx) {
        cx.setOptimizationLevel(9);
        cx.setWrapFactory(betterWrapFactory);
      }
      @Override public void contextReleased(Context cx) {}
    });
    this.global = (ScriptableObject) fac.call(new ContextAction() {
      public Object run(Context cx) {
        final ScriptableObject global = cx.initStandardObjects(null, true);
        cx.evaluateString(global,
            "RegExp; getClass; java; Packages; JavaAdapter;", "lazyLoad", 0, null);
        global.defineFunctionProperties(JsFunctions.JS_METHODS, JsFunctions.class, DONTENUM);
        jsHttp = new JsHttp(global, tester);
        putProperty(global, "req", jsHttp);
//        putProperty(global, "log", javaToJS(getLogger(JS_LOGGER_NAME), global));
        return global;
      }});
  }
  public void initDone() {
    fac.call(new ContextAction() { @Override public Object run(Context cx) {
      ((JsHttp)global.get("req")).initDone();
      global.sealObject();
      return null;
    }});
  }
  public Object call(final Callable fn, final Object... args) {
    return fn != null? fac.call(new ContextAction() { @Override public Object run(Context cx) {
      return fn.call(cx, global, null, args);
    }})
    : null;
  }
  public Object call(String fn, Object... args) {
    return call(getTypedProperty(global, fn, Function.class), args);
  }
  public void evaluateFile(final String fname) {
    fac.call(new ContextAction() {
      @Override public Object run(Context cx) {
        try {
          final Reader js = new InputStreamReader(new FileInputStream(fname), "UTF-8");
          cx.evaluateReader(global, js, fname, 1, null);
          return null;
        } catch (IOException e) { return sneakyThrow(e); }
      }
    });

  }

  static class BetterWrapFactory extends WrapFactory {
    @Override public Object wrap(Context cx, Scriptable scope, Object obj, Class<?> staticType) {
      if (obj instanceof Scriptable) {
        if (obj instanceof ScriptableObject && ((Scriptable) obj).getParentScope() == null
            && ((Scriptable) obj).getPrototype() == null) {
          ScriptRuntime.setObjectProtoAndParent((ScriptableObject) obj, scope);
        }
        return obj;
      }
      return obj instanceof List? new ScriptableList(scope, (List) obj)
      : obj instanceof Map? new ScriptableMap(scope, (Map) obj)
      : obj instanceof Text? ((Text)obj).getValue()
      : super.wrap(cx, scope, obj, staticType);
    }
  }
}
