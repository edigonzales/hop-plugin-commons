// SPDX-License-Identifier: MIT
package ch.so.agi.hop.commons.ui;

import java.util.Optional;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.IVariables;
import org.eclipse.swt.widgets.Shell;

/** An optional plugin-specific picker. Empty means cancellation; never mutate the control. */
@FunctionalInterface
public interface BrowseStrategy {
  Optional<String> browse(
      Shell shell,
      IVariables variables,
      String currentValue,
      EditorKind editor,
      String[] filterExtensions,
      String[] filterNames)
      throws HopException;
}
