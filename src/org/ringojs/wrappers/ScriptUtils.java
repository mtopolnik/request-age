/*
 *  Copyright 2006 Hannes Wallnoefer <hannes@helma.at>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.ringojs.wrappers;

import static org.mozilla.javascript.ScriptRuntime.constructError;

import org.mozilla.javascript.*;
import org.ringojs.wrappers.ScriptableMap;
import org.ringojs.wrappers.ScriptableList;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScriptUtils {
  @SuppressWarnings("unchecked") public static Object javaToJS(Object obj, Scriptable scope) {
    if (obj instanceof Scriptable) {
      if (obj instanceof ScriptableObject && ((Scriptable) obj).getParentScope() == null
          && ((Scriptable) obj).getPrototype() == null) {
        ScriptRuntime.setObjectProtoAndParent((ScriptableObject) obj, scope);
      }
      return obj;
    }
    return obj instanceof List? new ScriptableList(scope, (List) obj)
         : obj instanceof Map? new ScriptableMap(scope, (Map) obj)
         : Context.javaToJS(obj, scope);
  }
  public static Object jsToJava(Object obj) {
    while (obj instanceof Wrapper) obj = ((Wrapper) obj).unwrap();
    return obj;
  }
  public static ScriptableObject getScriptableArgument(Object[] args, int pos, boolean allowNull) {
    if (pos >= args.length || args[pos] == null || args[pos] == Undefined.instance) {
      if (allowNull) return null;
      throw constructError("Error", "Argument " + (pos + 1) + " must not be null");
    }
    if (args[pos] instanceof ScriptableObject) { return (ScriptableObject) args[pos]; }
    throw constructError("Error", "Can't convert to ScriptableObject: " + args[pos]);
  }
  public static String getStringArgument(Object[] args, int pos, boolean allowNull) {
    if (pos >= args.length || args[pos] == null || args[pos] == Undefined.instance) {
      if (allowNull) return null;
      throw constructError("Error", "Argument " + (pos + 1) + " must not be null");
    }
    return ScriptRuntime.toString(args[pos]);
  }
  public static Map getMapArgument(Object[] args, int pos, boolean allowNull)
      throws IllegalArgumentException {
    if (pos >= args.length || args[pos] == null || args[pos] == Undefined.instance) {
      if (allowNull) return null;
      throw constructError("Error", "Argument " + (pos + 1) + " must not be null");
    }
    if (args[pos] instanceof Map) { return (Map) args[pos]; }
    throw constructError("Error", "Can't convert to java.util.Map: " + args[pos]);
  }
  public static Object getObjectArgument(Object[] args, int pos, boolean allowNull) {
    if (pos >= args.length || args[pos] == null || args[pos] == Undefined.instance) {
      if (allowNull) return null;
      throw constructError("Error", "Argument " + (pos + 1) + " must not be null");
    }
    return args[pos];
  }
  public static int toInt(Object obj, int defaultValue) {
    double d = ScriptRuntime.toNumber(obj);
    if (d == ScriptRuntime.NaN || (int) d != d) { return defaultValue; }
    return (int) d;
  }
  public static void traceHelper(Function function, Object... args) {
    Context cx = Context.getCurrentContext();
    Scriptable scope = ScriptableObject.getTopLevelScope(function);
    EcmaError error = ScriptRuntime.constructError("Trace", "");
    WrapFactory wrapFactory = cx.getWrapFactory();
    Scriptable thisObj = wrapFactory.wrapAsJavaObject(cx, scope, error, null);
    for (int i = 0; i < args.length; i++) {
      args[i] = wrapFactory.wrap(cx, scope, args[i], null);
    }
    function.call(cx, scope, thisObj, args);
  }
  public static void assertHelper(Object condition, Object... args) {
    if (ScriptRuntime.toBoolean(condition)) { return; }
    // assertion failed
    String msg = "";
    if (args.length > 0) {
      msg = ScriptRuntime.toString(args[0]);
      Pattern pattern = Pattern.compile("%[sdifo]");
      for (int i = 1; i < args.length; i++) {
        Matcher matcher = pattern.matcher(msg);
        if (matcher.find()) {
          msg = matcher.replaceFirst(ScriptRuntime.toString(args[i]));
        } else {
          msg = msg + " " + ScriptRuntime.toString(args[i]);
        }
      }
    }
    throw ScriptRuntime.constructError("AssertionError", msg);
  }
}
