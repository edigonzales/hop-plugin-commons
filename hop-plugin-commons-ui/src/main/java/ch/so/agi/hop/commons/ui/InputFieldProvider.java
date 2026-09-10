// SPDX-License-Identifier: MIT
package ch.so.agi.hop.commons.ui;

import org.apache.hop.core.exception.HopException;

/** Supplies suggestions on the SWT UI thread; field names remain freely editable. */
@FunctionalInterface
public interface InputFieldProvider {
  String[] getFieldNames() throws HopException;
}
