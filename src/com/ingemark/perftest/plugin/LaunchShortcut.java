package com.ingemark.perftest.plugin;

import static com.ingemark.perftest.Util.sneakyThrow;
import static com.ingemark.perftest.plugin.StressTestActivator.EVT_RUN_SCRIPT;
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
      final RequestAgeView view = RequestAgeView.instance;
      final Event e = new Event();
      e.data = in.getLocation().toOSString();
      view.statsParent.notifyListeners(EVT_RUN_SCRIPT, e);
    } catch (CoreException e) { sneakyThrow(e); }
  }
}
