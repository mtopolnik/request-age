package com.ingemark.perftest.plugin;

import org.eclipse.debug.ui.ILaunchShortcut;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IEditorPart;

public class LaunchShortcut implements ILaunchShortcut
{
  @Override public void launch(ISelection selection, String mode) {
  }
  @Override public void launch(IEditorPart editor, String mode) {
  }
}
