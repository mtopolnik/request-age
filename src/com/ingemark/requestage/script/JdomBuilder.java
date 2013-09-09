package com.ingemark.requestage.script;

import static com.ingemark.requestage.Util.sneakyThrow;
import static com.ingemark.requestage.Util.wrapper;
import static org.jdom2.Namespace.NO_NAMESPACE;
import static org.jdom2.output.Format.getCompactFormat;

import java.io.IOException;
import java.io.StringWriter;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.XMLOutputter;
import org.mozilla.javascript.NativeJavaObject;
import org.mozilla.javascript.Scriptable;


public class JdomBuilder
{
  private final NativeJavaObject wrapper;
  private final Document doc;
  private Element root, currEl;

  JdomBuilder(Scriptable scope, String root, Namespace ns) {
    wrapper = wrapper(scope, this);
    this.root = push(nsEl(root, ns));
    this.doc = new Document().setRootElement(this.root);
  }
  JdomBuilder(Scriptable scope, Document doc) {
    if (doc == null) throw new NullPointerException("Document must not be null");
    wrapper = wrapper(scope, this);
    this.doc = doc;
    this.currEl = this.root = doc.getRootElement();
  }
  JdomBuilder(Scriptable scope, Element start) {
    if (start == null) throw new NullPointerException("Start element must not be null");
    wrapper = wrapper(scope, this);
    this.doc = start.getDocument();
    this.currEl = start;
    this.root = doc != null && doc.getRootElement() != null? doc.getRootElement() : start;
  }
  public static Scriptable jdomBuilder(Scriptable scope, Object start) {
    return (start instanceof Document?
        new JdomBuilder(scope,(Document)start) : new JdomBuilder(scope,(Element)start)).wrapper;
  }
  public static Scriptable jdomBuilder(Scriptable scope, String root, Namespace ns) {
    return new JdomBuilder(scope, root, ns).wrapper;
  }
  public Object topNode() { return doc != null? doc : root; }

  public Scriptable el(String name) { return el(name, null); }
  public Scriptable el(String name, Namespace ns) { push(nsEl(name, ns)); return wrapper; }
  public Scriptable emptyel(String name, Object... atts) {
    el(name); att(atts); end(name);
    return wrapper;
  }
  public Scriptable textel(Object... nvs) {
    final Element e = currEl;
    for (int i = 0; i < nvs.length;) {
      final String key = nvs[i++].toString(), value = nvs[i++].toString();
      if (value != null) e.addContent(nsEl(key, null).addContent(value));
    }
    return wrapper;
  }
  public Scriptable att(Object... nvs) {
    final Element e = currEl;
    for (int i = 0; i < nvs.length;)
      e.setAttribute(nvs[i++].toString(), nvs[i++].toString());
    return wrapper;
  }
  public Scriptable text(Object... texts) {
    final Element p = currEl;
    for (Object t : texts) p.addContent(t.toString());
    return wrapper;
  }
  public Scriptable end(String name) {
    final String currName = pop().getName();
    if (!currName.equals(name))
      throw new RuntimeException(
          "Receiving </" + name + ">, but the current element is <" + currName + ">.");
    return wrapper;
  }
  public Scriptable end() { pop(); return wrapper; }

  @Override public String toString() {
    final StringWriter w = new StringWriter(256);
    try {
      final XMLOutputter outputter = new XMLOutputter(getCompactFormat());
      if (doc != null) outputter.output(doc, w);
      else outputter.output(root, w);
    }
    catch (IOException e) { sneakyThrow(e); }
    return w.toString();
  }

  private Element nsEl(String name, Namespace ns) {
    return new Element(name, ns != null? ns : currEl != null? currEl.getNamespace() : NO_NAMESPACE);
  }
  private Element push(Element e) {
    if (root == null) {
      root = currEl = e;
      if (doc != null) doc.setRootElement(root);
    } else currEl.addContent(e);
    return currEl = e;
  }
  private Element pop() {
    final Element e = currEl;
    currEl = e.getParentElement();
    return e;
  }
}
