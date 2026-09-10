// SPDX-License-Identifier: MIT
package ch.so.agi.hop.commons.ui;

import java.util.Optional;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.ui.core.dialog.BaseDialog;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

/** Delegates to Hop's picker without allowing its TextVar overload to mutate the live editor. */
final class HopBrowseStrategy implements BrowseStrategy {
  @Override
  public Optional<String> browse(
      Shell shell,
      IVariables variables,
      String currentValue,
      EditorKind editor,
      String[] extensions,
      String[] names) {
    if (editor == EditorKind.TEXT) {
      return Optional.empty();
    }
    if (editor == EditorKind.DIRECTORY) {
      return Optional.ofNullable(
          BaseDialog.presentDirectoryDialog(shell, currentValue, null, variables));
    }
    Composite holder = new Composite(shell, SWT.NONE);
    holder.setVisible(false);
    try {
      TextVar scratch = new TextVar(variables, holder, SWT.NONE);
      scratch.setText(currentValue);
      return Optional.ofNullable(
          BaseDialog.presentFileDialog(
              editor == EditorKind.FILE_SAVE, shell, scratch, variables, extensions, names, false));
    } finally {
      holder.dispose();
    }
  }
}
