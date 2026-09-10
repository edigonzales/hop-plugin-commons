// SPDX-License-Identifier: MIT
package ch.so.agi.hop.commons.ui;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.hopgui.HopGui;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;

/** Standalone desktop demonstration; never shipped in the library JAR. */
public final class ValueOrFieldDemo {
  private final Display display;
  private final Shell launcher;
  private final Variables variables = new Variables();
  private final Map<EditorKind, ValueOrField> saved = new LinkedHashMap<>();
  private final Label summary;

  private ValueOrFieldDemo(Display display) {
    this.display = display;
    variables.setVariable("PROJECT_HOME", System.getProperty("user.home"));
    launcher = new Shell(display);
    launcher.setText("Hop Plugin Commons — Demo");
    launcher.setLayout(new GridLayout(2, false));
    HopGui.getInstance().setVariables(variables);
    HopGui.getInstance().setShell(launcher);
    HopGui.getInstance().setProps(PropsUi.getInstance());
    for (EditorKind editor : EditorKind.values()) {
      saved.put(
          editor,
          new ValueOrField(
              SourceMode.CONFIGURED,
              editor == EditorKind.TEXT ? "layer_name" : "${PROJECT_HOME}",
              "raster_path"));
      Button button = new Button(launcher, SWT.PUSH);
      button.setText(
          switch (editor) {
            case FILE_OPEN -> "Datei öffnen…";
            case DIRECTORY -> "Ausgabeverzeichnis…";
            case FILE_SAVE -> "Datei speichern…";
            case TEXT -> "Text / Layername…";
          });
      button.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
      button.addListener(SWT.Selection, e -> edit(editor));
    }
    Button nativePicker = new Button(launcher, SWT.CHECK);
    nativePicker.setText("Native Betriebssystem-Dialoge verwenden");
    nativePicker.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    nativePicker.addListener(
        SWT.Selection,
        e ->
            variables.setVariable(
                "HOP_USE_NATIVE_FILE_DIALOG", nativePicker.getSelection() ? "Y" : "N"));
    summary = new Label(launcher, SWT.WRAP);
    GridData summaryData = new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1);
    summaryData.widthHint = 700;
    summary.setLayoutData(summaryData);
    updateSummary();
    launcher.setSize(800, 430);
    launcher.open();
  }

  private void edit(EditorKind editor) {
    Shell dialog = new Shell(launcher, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.APPLICATION_MODAL);
    dialog.setText("ValueOrField — " + editor);
    dialog.setLayout(new GridLayout(2, false));
    Button fail = new Button(dialog, SWT.CHECK);
    fail.setText("Fehler beim Laden der Eingangsfelder simulieren");
    fail.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    Label label = new Label(dialog, SWT.NONE);
    label.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
    label.setText(editor == EditorKind.DIRECTORY ? "Ausgabeverzeichnis" : "Wert");
    Label dirty = new Label(dialog, SWT.NONE);
    ValueOrFieldControl control =
        ValueOrFieldControl.builder(dialog, variables)
            .editor(editor)
            .fileExtensions("*.tif", "*.tiff", "*")
            .fieldProvider(
                () -> {
                  if (fail.getSelection())
                    throw new HopException("Vorgelagerte Metadaten sind nicht verfügbar");
                  return new String[] {"raster_path", "output_directory", "layer_name"};
                })
            .onChange(() -> dirty.setText("Geändert — noch nicht übernommen"))
            .build();
    control.moveAbove(dirty);
    dirty.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
    control.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
    control.setValue(saved.get(editor));
    Composite buttons = new Composite(dialog, SWT.NONE);
    buttons.setLayout(new GridLayout(2, true));
    buttons.setLayoutData(new GridData(SWT.RIGHT, SWT.BOTTOM, true, true, 2, 1));
    Button ok = new Button(buttons, SWT.PUSH);
    ok.setText("OK");
    ok.addListener(
        SWT.Selection,
        e -> {
          saved.put(editor, control.getValue());
          updateSummary();
          dialog.dispose();
        });
    Button cancel = new Button(buttons, SWT.PUSH);
    cancel.setText("Abbrechen");
    cancel.addListener(SWT.Selection, e -> dialog.dispose());
    dialog.setDefaultButton(ok);
    dialog.addTraverseListener(
        e -> {
          if (e.detail == SWT.TRAVERSE_ESCAPE) {
            e.doit = false;
            dialog.dispose();
          }
        });
    dialog.setSize(850, 280);
    dialog.open();
    control.setFocus();
    while (!dialog.isDisposed()) {
      if (!display.readAndDispatch()) display.sleep();
    }
  }

  private void updateSummary() {
    StringBuilder text =
        new StringBuilder("Bestätigter Zustand (Dialog erneut öffnen zum Prüfen):\n\n");
    saved.forEach((kind, value) -> text.append(kind).append(": ").append(value).append("\n\n"));
    summary.setText(text.toString());
  }

  public static void main(String[] args) throws Exception {
    HopClientEnvironment.init();
    Display display = new Display();
    try {
      ValueOrFieldDemo demo = new ValueOrFieldDemo(display);
      while (!demo.launcher.isDisposed()) {
        if (!display.readAndDispatch()) display.sleep();
      }
    } finally {
      display.dispose();
    }
  }
}
