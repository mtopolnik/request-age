package com.ingemark.requestage;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeJavaMethod;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;

public class NativeJavaObjectPreserveThis extends NativeJavaObject {
  private final NativeJavaObject wrapper;
  public NativeJavaObjectPreserveThis(Scriptable scope, Object wrapped) {
    super(scope, wrapped, wrapped.getClass());
    this.wrapper = this;
  }
  public NativeJavaObjectPreserveThis(Scriptable scope, Object proto, NativeJavaObject wrapper) {
    super(scope, proto, proto.getClass());
    this.wrapper = wrapper;
  }
  @Override public Object get(String name, Scriptable start) {
    final Object ret = super.get(name, start);
    return ret instanceof NativeJavaMethod?
        new JavaMethodPreserveThis((NativeJavaMethod)ret, this.javaObject, this.wrapper) : ret;
  }
}

class JavaMethodPreserveThis implements Function {
  private final NativeJavaMethod meth;
  private final Object target;
  private final NativeJavaObject wrapper;

  public JavaMethodPreserveThis(NativeJavaMethod meth, Object target, NativeJavaObject wrapper) {
    this.meth = meth;
    this.target = target;
    this.wrapper = wrapper;
  }

  @Override public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
    Object ret = meth.call(cx, scope, thisObj, args);
    if (ret instanceof NativeJavaObject) ret = ((NativeJavaObject)ret).unwrap();
    return ret == target? wrapper : ret;
  }

  // All methods below are just delegates

  public Scriptable construct(Context cx, Scriptable scope, Object[] args) {
    return meth.construct(cx, scope, args);
  }
  public boolean hasInstance(Scriptable instance) { return meth.hasInstance(instance); }
  public boolean has(int index, Scriptable start) { return meth.has(index, start); }
  public void put(int index, Scriptable start, Object value) { meth.put(index, start, value); }
  public void delete(int index) { meth.delete(index); }
  public boolean has(String name, Scriptable start) { return meth.has(name, start); }
  public void put(String name, Scriptable start, Object value) { meth.put(name, start, value); }
  public void delete(String name) { meth.delete(name); }
  public Scriptable getPrototype() { return meth.getPrototype(); }
  public void setPrototype(Scriptable m) { meth.setPrototype(m); }
  public Scriptable getParentScope() { return meth.getParentScope(); }
  public void setParentScope(Scriptable m) { meth.setParentScope(m); }
  public Object[] getIds() { return meth.getIds(); }
  public Object get(int index, Scriptable start) { return meth.get(index, start); }
  public Object get(String name, Scriptable start) { return meth.get(name, start); }
  public String getClassName() { return meth.getClassName(); }
  public Object getDefaultValue(Class<?> typeHint) { return meth.getDefaultValue(typeHint); }
}
