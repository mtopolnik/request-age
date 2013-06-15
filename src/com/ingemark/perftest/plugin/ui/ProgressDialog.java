package com.ingemark.perftest.plugin.ui;

import static com.ingemark.perftest.Util.gridData;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IProgressMonitorWithBlocking;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IconAndMessageDialog;
import org.eclipse.jface.dialogs.ProgressIndicator;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;

public class ProgressDialog extends IconAndMessageDialog
{
  private static String DEFAULT_TASKNAME =
      JFaceResources.getString("AsyncProgressMonitorDialog.message");
  private static int LABEL_DLUS = 21, BAR_DLUS = 9;
  private final ProgressMonitor progressMonitor = new ProgressMonitor();
  private ProgressIndicator progressIndicator;
  private Label subTaskLabel;
  private Button cancel;
  private String task;
  private int nestingDepth;

  public ProgressDialog(Shell parent) {
    super(parent);
    setShellStyle(getDefaultOrientation() | SWT.BORDER | SWT.TITLE | SWT.APPLICATION_MODAL |
                  (isResizable()? SWT.RESIZE | SWT.MAX : 0));
    setBlockOnOpen(false);
  }

  public void enter() { open(); nestingDepth++; }
  public void leave() { nestingDepth--; close(); }

  @Override public int open() {
    final int result = super.open();
    setMessage(task == null || task.length() == 0? DEFAULT_TASKNAME : task, true);
    return result;
  }
  @Override public boolean close() { return nestingDepth <= 0 && super.close(); }

  @Override protected void cancelPressed() {
    cancel.setEnabled(false);
    progressMonitor.setCanceled(true);
    super.cancelPressed();
  }

  @Override protected void configureShell(final Shell shell) {
    super.configureShell(shell);
    shell.setText(JFaceResources.getString("AsyncProgressMonitorDialog.title"));
    shell.setCursor(shell.getDisplay().getSystemCursor(SWT.CURSOR_WAIT));
    // Add a listener to set the message properly when the dialog becomes visible.
    shell.addListener(SWT.Show, new Listener() { @Override public void handleEvent(Event event) {
      // We need to async the message update since the Show event precedes visibility
      shell.getDisplay().asyncExec(new Runnable() { @Override public void run() {
        setMessage(message, true);
      }});
    }});
  }
  @Override protected void createButtonsForButtonBar(Composite parent) {
    cancel = createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, true);
    cancel.setCursor(null);
  }
  @Override protected Control createDialogArea(Composite parent) {
    setMessage(DEFAULT_TASKNAME, false);
    createMessageArea(parent);
    progressIndicator = new ProgressIndicator(parent);
    gridData().hint(SWT.DEFAULT, convertVerticalDLUsToPixels(BAR_DLUS)).
       align(GridData.FILL, SWT.DEFAULT).grab(true, false).span(2, 1)
       .applyTo(progressIndicator);
    subTaskLabel = new Label(parent, SWT.LEFT | SWT.WRAP);
    gridData().hint(SWT.DEFAULT, convertVerticalDLUsToPixels(LABEL_DLUS))
      .align(GridData.FILL, SWT.DEFAULT).span(2,1)
      .applyTo(subTaskLabel);
    subTaskLabel.setFont(parent.getFont());
    return parent;
  }
  @Override protected Point getInitialSize() {
    final Point p = super.getInitialSize();
    p.x = Math.max(p.x, 450);
    return p;
  }
  protected void setCancelButtonEnabled(boolean b) {
    if (cancel != null && !cancel.isDisposed()) cancel.setEnabled(b);
  }
  private void setMessage(String msg, boolean force) {
    message = msg == null ? "" : msg;
    if (messageLabel == null || messageLabel.isDisposed()) return;
    if (force || messageLabel.isVisible()) {
      messageLabel.setToolTipText(message);
      messageLabel.setText(shortenText(message, messageLabel));
    }
  }
  public IProgressMonitor progressMonitor() { return progressMonitor; }

  @Override protected Image getImage() { return getInfoImage(); }

  private class ProgressMonitor implements IProgressMonitorWithBlocking {
    private String subTask = "";
    private volatile boolean canceled;
    protected boolean locked = false;
    @Override public void beginTask(String name, int totalWork) {
      if (progressIndicator.isDisposed()) return;
      setTaskName(name);
      if (totalWork == UNKNOWN) progressIndicator.beginAnimatedTask();
      else progressIndicator.beginTask(totalWork);
    }
    @Override public void done() {
      if (progressIndicator.isDisposed()) return;
      progressIndicator.sendRemainingWork();
      progressIndicator.done();
    }
    @Override public void setTaskName(String name) {
      task = name == null? "" : name;
      setMessage(task.length() > 0? task : DEFAULT_TASKNAME, false);
      if (messageLabel != null && !messageLabel.isDisposed()) messageLabel.update();
    }
    @Override public void setCanceled(boolean b) {
      canceled = b;
      if (locked) clearBlocked();
    }
    @Override public void subTask(String name) {
      if (subTaskLabel.isDisposed()) return;
      subTask = name != null ? name : "";
      subTaskLabel.setText(shortenText(subTask, subTaskLabel));
      subTaskLabel.update();
    }
    @Override public void internalWorked(double work) {
      if (!progressIndicator.isDisposed()) progressIndicator.worked(work);
    }
    @Override public void clearBlocked() {
      if (getShell() == null || getShell().isDisposed()) return;
      locked = false;
      progressIndicator.showNormal();
      setMessage(task, true);
      imageLabel.setImage(getImage());
    }
    @Override public void setBlocked(IStatus reason) {
      if (getShell() == null || getShell().isDisposed()) return;
      locked = true;
      progressIndicator.showPaused();
      setMessage(reason.getMessage(), true);
      imageLabel.setImage(getImage());
    }
    @Override public boolean isCanceled() { return canceled; }
    @Override public void worked(int work) { internalWorked(work); }
  }
}
