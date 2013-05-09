package com.ingemark.perftest.plugin;

import static com.ingemark.perftest.plugin.StressTestActivator.RUN_SCRIPT_EVTYPE;
import static com.ingemark.perftest.plugin.StressTestActivator.STRESSTEST_VIEW_ID;
import static org.eclipse.ui.PlatformUI.getWorkbench;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.widgets.Event;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.part.FileEditorInput;

import com.ingemark.perftest.plugin.ui.RequestAgeView;

public class LaunchShortcut implements ILaunchShortcut
{
  @Override public void launch(ISelection selection, String mode) {
    launch(((IFile)((StructuredSelection)selection).getFirstElement()));
  }
  @Override public void launch(IEditorPart editor, String mode) {
    launch(((FileEditorInput)editor.getEditorInput()).getFile());
  }
  void launch(IFile in) {
    try {
      getWorkbench().getActiveWorkbenchWindow().getActivePage().showView(STRESSTEST_VIEW_ID);
      final Event event = new Event();
      event.data = in.getContents();
      RequestAgeView.statsParent.notifyListeners(RUN_SCRIPT_EVTYPE, event);
    } catch (CoreException e) { throw new RuntimeException(e); }
  }
}
