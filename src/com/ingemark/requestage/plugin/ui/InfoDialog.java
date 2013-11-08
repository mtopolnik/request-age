package com.ingemark.requestage.plugin.ui;

import static com.ingemark.requestage.Util.gridData;
import static com.ingemark.requestage.plugin.RequestAgePlugin.okButton;
import static org.eclipse.ui.PlatformUI.getWorkbench;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbenchWindow;

import com.ingemark.requestage.DialogInfo;

public class InfoDialog {
  public static void show(final DialogInfo info) {
    final Display disp = Display.getDefault();
    disp.asyncExec(new Runnable() { public void run() {
      final Shell top = new Shell(disp);
      final IWorkbenchWindow w = getWorkbench().getActiveWorkbenchWindow();
      final Rectangle mainBounds = disp.getBounds();
      mainBounds.width *= 0.65;
      final Rectangle b = w != null? w.getShell().getBounds() : mainBounds;
      b.x += 20; b.y += 20; b.height -= 40; b.width = mainBounds.width;
      top.setBounds(b);
      top.setLayout(new GridLayout(1, false));
      top.setBackground(disp.getSystemColor(SWT.COLOR_WHITE));
      top.setText(info.title);
      final Text t = new Text(top, SWT.H_SCROLL | SWT.V_SCROLL);
      gridData().align(SWT.FILL, SWT.FILL).grab(true, true).applyTo(t);
      t.setBackground(disp.getSystemColor(SWT.COLOR_WHITE));
      t.setText(info.msg);
      t.setEditable(false);
      okButton(top, true);
      top.setVisible(true);
      top.setFocus();
    }});
  }
}
