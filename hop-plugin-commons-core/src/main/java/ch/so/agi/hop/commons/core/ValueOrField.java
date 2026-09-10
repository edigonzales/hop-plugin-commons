// SPDX-License-Identifier: MIT
package ch.so.agi.hop.commons.core;

import java.util.Objects;

/**
 * Dialog state retaining both inputs. This model neither resolves variables nor reads rows.
 *
 * @param mode selected source
 * @param configuredValue literal or variable expression, null becomes empty
 * @param fieldName incoming field name, null becomes empty
 */
public record ValueOrField(SourceMode mode, String configuredValue, String fieldName) {
  public ValueOrField {
    Objects.requireNonNull(mode, "mode");
    configuredValue = Objects.requireNonNullElse(configuredValue, "");
    fieldName = Objects.requireNonNullElse(fieldName, "");
  }
}
