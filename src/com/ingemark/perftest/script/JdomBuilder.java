package com.ingemark.perftest.script;

import static com.ingemark.perftest.Util.sneakyThrow;
import static org.jdom2.Namespace.NO_NAMESPACE;
import static org.jdom2.output.Format.getCompactFormat;

import java.io.IOException;
import java.io.StringWriter;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.XMLOutputter;


public class JdomBuilder
{
  private final Document doc;
  private Element root, currEl;

  JdomBuilder(String root, Namespace ns) {
    this.root = push(nsEl(root, ns));
    this.doc = new Document().setRootElement(this.root);
  }
  public JdomBuilder(Document doc) {
    if (doc == null) throw new NullPointerException("Document must not be null");
    this.doc = doc;
    this.currEl = this.root = doc.getRootElement();
  }
  public JdomBuilder(Element start) {
    if (start == null) throw new NullPointerException("Start element must not be null");
    this.doc = start.getDocument();
    this.currEl = start;
    this.root = doc != null && doc.getRootElement() != null? doc.getRootElement() : start;
  }
  public Object topNode() { return doc != null? doc : root; }

  public JdomBuilder el(String name) { return el(name, null); }
  public JdomBuilder el(String name, Namespace ns) { push(nsEl(name, ns)); return this; }
  public JdomBuilder empty(String name, Object... atts) {
    el(name).att(atts).end(name);
    return this;
  }
  public JdomBuilder textel(Object... nvs) {
    final Element e = currEl;
    for (int i = 0; i < nvs.length;) {
      final String key = nvs[i++].toString(), value = nvs[i++].toString();
      if (value != null) e.addContent(nsEl(key, null).addContent(value));
    }
    return this;
  }
  public JdomBuilder att(Object... nvs) {
    final Element e = currEl;
    for (int i = 0; i < nvs.length;)
      e.setAttribute(nvs[i++].toString(), nvs[i++].toString());
    return this;
  }
  public JdomBuilder text(Object... texts) {
    final Element p = currEl;
    for (Object t : texts) p.addContent(t.toString());
    return this;
  }
  public JdomBuilder end(String name) {
    final String currName = pop().getName();
    if (!currName.equals(name))
      throw new RuntimeException(
          "Receiving </" + name + ">, but the current element is <" + currName + ">.");
    return this;
  }
  public JdomBuilder end() { pop(); return this; }

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
