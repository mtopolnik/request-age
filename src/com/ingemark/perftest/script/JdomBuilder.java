package com.ingemark.perftest;

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
  private final Element root;
  private Element currEl;

  JdomBuilder(String root, Namespace ns) {
    this.root = push(nsEl(root, ns));
    this.doc = new Document().setRootElement(this.root);
  }

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
    try { new XMLOutputter(getCompactFormat()).output(doc, w); }
    catch (IOException e) { sneakyThrow(e); }
    return w.toString();
  }

  private Element nsEl(String name, Namespace ns) {
    return new Element(name, ns != null? ns : currEl != null? currEl.getNamespace() : NO_NAMESPACE);
  }
  private Element push(Element e) {
    if (currEl != null) currEl.addContent(e);
    return currEl = e;
  }
  private Element pop() {
    final Element e = currEl;
    currEl = e.getParentElement();
    return e;
  }
}
