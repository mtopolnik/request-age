package com.ingemark.perftest.plugin.ui;

import static com.ingemark.perftest.Util.gridData;
import static org.slf4j.LoggerFactory.getLogger;

import org.eclipse.core.runtime.IProgressMonitorWithBlocking;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
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
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;

public class ProgressDialog extends IconAndMessageDialog
{
  private static final Logger log = getLogger(ProgressDialog.class);
  private static String DEFAULT_TASKNAME =
      JFaceResources.getString("ProgressMonitorDialog.message");
  private static int LABEL_DLUS = 21, BAR_DLUS = 9;
  private final ProgressMonitor progressMonitor = new ProgressMonitor();
  private final Runnable onCancel;
  private ProgressIndicator progressIndicator;
  private Label subTaskLabel;
  private Button cancel;
  private String task;

  public ProgressDialog(String taskName, int totalWork, Runnable onCancel) {
    super(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell());
    this.onCancel = onCancel;
    setShellStyle(getDefaultOrientation() | SWT.BORDER | SWT.TITLE | SWT.APPLICATION_MODAL |
        (isResizable()? SWT.RESIZE | SWT.MAX : 0));
    setBlockOnOpen(false);
    pm().beginTask(taskName, totalWork);
    open();
  }

  @Override public int open() {
    final int result = super.open();
    setMessage(task == null || task.length() == 0? DEFAULT_TASKNAME : task, true);
    return result;
  }

  public ProgressDialog cancelable(boolean cancelable) {
    cancel.setEnabled(cancelable); return this;
  }

  public ProgressMonitor pm() { return progressMonitor; }

  @Override protected void cancelPressed() {
    cancel.setEnabled(false);
    progressMonitor.setCanceled(true);
    setReturnCode(CANCEL);
  }

  @Override protected void configureShell(final Shell shell) {
    super.configureShell(shell);
    shell.setText(JFaceResources.getString("ProgressMonitorDialog.title"));
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
       align(GridData.FILL, GridData.BEGINNING).grab(true, false).span(2, 1)
       .applyTo(progressIndicator);
    subTaskLabel = new Label(parent, SWT.LEFT | SWT.WRAP);
    gridData().hint(SWT.DEFAULT, convertVerticalDLUsToPixels(LABEL_DLUS))
      .align(GridData.FILL, GridData.BEGINNING).span(2,1)
      .applyTo(subTaskLabel);
    subTaskLabel.setFont(parent.getFont());
    return parent;
  }
  @Override protected Point getInitialSize() {
    final Point p = super.getInitialSize();
    p.x = Math.max(p.x, 450);
    return p;
  }
  private void setMessage(String msg, boolean force) {
    message = msg == null ? "" : msg;
    if (messageLabel == null || messageLabel.isDisposed()) return;
    if (force || messageLabel.isVisible()) {
      messageLabel.setToolTipText(message);
      messageLabel.setText(shortenText(message, messageLabel));
    }
  }

  @Override protected Image getImage() { return getInfoImage(); }

  public class ProgressMonitor implements IProgressMonitorWithBlocking {
    private String subTask = "";
    private volatile boolean canceled;
    protected boolean locked = false;

    @Override public void beginTask(final String name, final int totalWork) {
      swtAsync(new R() { void r() {
        if (progressIndicator.isDisposed()) return;
        setTaskName(name);
        if (totalWork == UNKNOWN) progressIndicator.beginAnimatedTask();
        else progressIndicator.beginTask(totalWork);
      }});
    }
    @Override public void done() {
      log.debug("ProgressMonitor#done");
      swtAsync(new R() { void r() {
        if (progressIndicator.isDisposed()) return;
        progressIndicator.sendRemainingWork();
        progressIndicator.done();
        close();
      }});
    }
    @Override public void setTaskName(final String name) {
      swtAsync(new R() { void r() {
        task = name == null? "" : name;
        setMessage(task.length() > 0? task : DEFAULT_TASKNAME, false);
        if (messageLabel != null && !messageLabel.isDisposed()) messageLabel.update();
      }});
    }
    @Override public void setCanceled(final boolean b) {
      swtAsync(new R() { void r() {
        canceled = b;
        if (b) onCancel.run();
        if (!b && locked) clearBlocked();
        if (b && !locked) setBlocked(new Status(CANCEL, "stresstest", "Canceled"));
      }});
    }
    @Override public void subTask(final String name) {
      swtAsync(new R() { void r() {
        if (subTaskLabel.isDisposed()) return;
        subTask = name != null ? name : "";
        subTaskLabel.setText(shortenText(subTask, subTaskLabel));
        subTaskLabel.update();
      }});
    }
    @Override public void internalWorked(final double work) {
      swtAsync(new R() { void r() {
        if (!progressIndicator.isDisposed()) progressIndicator.worked(work);
      }});
    }
    @Override public void clearBlocked() {
      swtAsync(new R() { void r() {
        if (getShell() == null || getShell().isDisposed()) return;
        locked = false;
        progressIndicator.showNormal();
        setMessage(task, true);
        imageLabel.setImage(getImage());
      }});
    }
    @Override public void setBlocked(final IStatus reason) {
      swtAsync(new R() { void r() {
        if (getShell() == null || getShell().isDisposed()) return;
        locked = true;
        progressIndicator.showPaused();
        setMessage(reason.getMessage(), true);
        imageLabel.setImage(getImage());
      }});
    }
    @Override public boolean isCanceled() { return canceled; }
    @Override public void worked(int work) { internalWorked(work); }
  }

  abstract class R { abstract void r(); }
  static void swtAsync(final R r) {
    Display.getDefault().asyncExec(new Runnable() { public void run() { r.r(); }});
  }
}