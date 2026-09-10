// SPDX-License-Identifier: MIT
package ch.so.agi.hop.commons.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ValueOrFieldTest {
  @Test
  void normalizesMissingTextButRequiresMode() {
    assertEquals(
        new ValueOrField(SourceMode.CONFIGURED, "", ""),
        new ValueOrField(SourceMode.CONFIGURED, null, null));
    assertThrows(NullPointerException.class, () -> new ValueOrField(null, "", ""));
  }

  @Test
  void retainsBothInputsExactly() {
    String expression = " ${PROJECT_HOME}/a.tif ";
    ValueOrField value = new ValueOrField(SourceMode.FIELD, expression, " ${raw_field} ");
    assertEquals(expression, value.configuredValue());
    assertEquals(" ${raw_field} ", value.fieldName());
  }
}
