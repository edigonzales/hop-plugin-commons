// SPDX-License-Identifier: MIT
package ch.so.agi.hop.commons.ui;

import static org.junit.jupiter.api.Assertions.*;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.hop.core.HopClientEnvironment;
import org.apache.hop.core.exception.HopException;
import org.apache.hop.core.variables.Variables;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.junit.jupiter.api.*;

class ValueOrFieldControlTest {
  private static Display display;
  private Shell shell;

  @BeforeAll
  static void init() throws Exception {
    HopClientEnvironment.init();
    display = new Display();
  }

  @AfterAll
  static void shutdown() {
    if (display != null) display.dispose();
  }

  @BeforeEach
  void open() {
    shell = new Shell(display);
    shell.setLayout(new FillLayout());
    shell.setSize(800, 180);
  }

  @AfterEach
  void close() {
    shell.dispose();
  }

  @Test
  void loadsSilentlyAndPreservesInactiveValuesAcrossSwitches() {
    AtomicInteger changes = new AtomicInteger();
    ValueOrFieldControl control = builder().onChange(changes::incrementAndGet).build();
    ValueOrField original = new ValueOrField(SourceMode.CONFIGURED, " ${P}/a.tif ", " raw ");
    control.setValue(original);
    assertEquals(0, changes.get());
    switchMode(control, 1);
    assertEquals(new ValueOrField(SourceMode.FIELD, " ${P}/a.tif ", " raw "), control.getValue());
    field(control).setText("${column}");
    switchMode(control, 0);
    assertEquals(" ${P}/a.tif ", control.getValue().configuredValue());
    assertEquals("${column}", control.getValue().fieldName());
    assertEquals(3, changes.get());
    text(control).setText("changed");
    assertEquals(4, changes.get());
    control.setValue(original);
    assertEquals(4, changes.get());
  }

  @Test
  void lazySuggestionsRefreshSilentlyAndRetainManualNameOnFailure() {
    AtomicInteger calls = new AtomicInteger();
    AtomicInteger changes = new AtomicInteger();
    ValueOrFieldControl control =
        builder()
            .onChange(changes::incrementAndGet)
            .fieldProvider(
                () -> {
                  int call = calls.incrementAndGet();
                  if (call == 2) throw new HopException("upstream unavailable");
                  return call == 3 ? new String[0] : new String[] {"a", "b"};
                })
            .build();
    control.setValue(new ValueOrField(SourceMode.CONFIGURED, "value", "manual"));
    assertEquals(0, calls.get());
    switchMode(control, 1);
    assertArrayEquals(new String[] {"a", "b"}, field(control).getItems());
    assertEquals("manual", field(control).getText());
    control.refreshFields();
    assertArrayEquals(new String[] {"a", "b"}, field(control).getItems());
    Label status =
        all(control, Label.class).stream()
            .filter(l -> l.getText().contains("upstream unavailable"))
            .findFirst()
            .orElseThrow();
    assertTrue(status.getVisible());
    control.refreshFields();
    assertEquals(0, field(control).getItemCount());
    assertEquals("manual", field(control).getText());
    assertFalse(status.getVisible());
    switchMode(control, 0);
    switchMode(control, 1);
    assertEquals(3, calls.get());
    assertEquals(3, changes.get());
  }

  @Test
  void loadingFieldModeFetchesSuggestionsWithoutChangeAndFailureCanRetry() {
    AtomicInteger changes = new AtomicInteger();
    AtomicInteger calls = new AtomicInteger();
    ValueOrFieldControl control =
        builder()
            .onChange(changes::incrementAndGet)
            .fieldProvider(
                () -> {
                  if (calls.incrementAndGet() == 1) throw new HopException("not ready");
                  return null;
                })
            .build();
    control.setValue(new ValueOrField(SourceMode.FIELD, "saved", "unknown"));
    assertEquals(0, changes.get());
    assertEquals("unknown", field(control).getText());
    control.refreshFields();
    assertEquals(2, calls.get());
    assertEquals(0, changes.get());
  }

  @Test
  void browseSelectionAndCancellationUseExactlyOneChange() {
    for (EditorKind kind :
        new EditorKind[] {EditorKind.FILE_OPEN, EditorKind.FILE_SAVE, EditorKind.DIRECTORY}) {
      AtomicInteger changes = new AtomicInteger();
      AtomicInteger calls = new AtomicInteger();
      ValueOrFieldControl control =
          builder()
              .editor(kind)
              .fileExtensions("*.tif")
              .onChange(changes::incrementAndGet)
              .browseStrategy(
                  (s, v, current, editor, extensions, names) -> {
                    assertEquals(kind, editor);
                    assertArrayEquals(new String[] {"*.tif"}, extensions);
                    return calls.incrementAndGet() == 1
                        ? Optional.of(" ${ROOT}/picked ")
                        : Optional.empty();
                  })
              .build();
      control.setValue(new ValueOrField(SourceMode.CONFIGURED, "old", "field"));
      Button button = all(control, Button.class).getFirst();
      click(button);
      assertEquals(" ${ROOT}/picked ", control.getValue().configuredValue());
      assertEquals(1, changes.get());
      click(button);
      assertEquals(1, changes.get());
      assertEquals("field", control.getValue().fieldName());
      control.dispose();
    }
  }

  @Test
  void browseFailureKeepsStateAndShowsError() {
    ValueOrFieldControl control =
        builder()
            .editor(EditorKind.FILE_OPEN)
            .browseStrategy(
                (s, v, c, e, x, n) -> {
                  throw new HopException("picker failed");
                })
            .build();
    ValueOrField original = new ValueOrField(SourceMode.CONFIGURED, "old", "field");
    control.setValue(original);
    click(all(control, Button.class).getFirst());
    assertEquals(original, control.getValue());
    assertTrue(
        all(control, Label.class).stream()
            .anyMatch(l -> l.getText().contains("picker failed") && l.getVisible()));
  }

  @Test
  void visibilityFocusDisabledStateAndTabOrderFollowMode() {
    ValueOrFieldControl control =
        builder().editor(EditorKind.FILE_OPEN).fieldProvider(() -> new String[] {"a"}).build();
    shell.open();
    shell.forceActive();
    long deadline = System.nanoTime() + 2_000_000_000L;
    while (display.getActiveShell() != shell && System.nanoTime() < deadline) {
      pump();
      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
    pump();
    assertTrue(text(control).isVisible());
    assertFalse(field(control).isVisible());
    switchMode(control, 1);
    pump();
    assertTrue(field(control).isVisible());
    assertFalse(text(control).isVisible());
    assertTrue(control.setFocus());
    assertSame(field(control), display.getFocusControl());
    Composite editors = field(control).getParent().getParent();
    assertArrayEquals(new Control[] {field(control).getParent()}, editors.getTabList());
    control.setEnabled(false);
    assertFalse(field(control).isEnabled());
    assertFalse(text(control).isEnabled());
    for (Button b : all(control, Button.class)) assertFalse(b.isEnabled());
    control.setEnabled(true);
    switchMode(control, 0);
    assertTrue(text(control).isEnabled());
    assertTrue(text(control).isVisible());
  }

  @Test
  void defaultsAllowManualFieldsAndBuildersAreIndependent() {
    ValueOrFieldControl.Builder builder = builder();
    ValueOrFieldControl first = builder.build();
    builder.editor(EditorKind.DIRECTORY);
    ValueOrFieldControl second = builder.build();
    assertTrue(all(first, Button.class).isEmpty());
    assertEquals(1, all(second, Button.class).size());
    switchMode(first, 1);
    field(first).setText("manual");
    assertEquals("manual", first.getValue().fieldName());
    assertThrows(
        IllegalArgumentException.class,
        () -> builder.fileFilters(new String[] {"*"}, new String[0]));
  }

  @Test
  void usesHopGermanTranslations() {
    var language = org.apache.hop.i18n.LanguageChoice.getInstance();
    var previous = language.getDefaultLocale();
    try {
      language.setDefaultLocale(java.util.Locale.GERMANY);
      ValueOrFieldControl control = builder().editor(EditorKind.FILE_OPEN).build();
      assertArrayEquals(
          new String[] {"Wert / Variable", "Eingabefeld"},
          all(control, Combo.class).getFirst().getItems());
      assertEquals("Durchsuchen...", all(control, Button.class).getFirst().getText());
    } finally {
      language.setDefaultLocale(previous);
    }
  }

  @Test
  void externalStatusIsInitializedDeduplicatedAndDoesNotChangeValuesOrHeight() {
    List<String> messages = new ArrayList<>();
    AtomicReference<String> failure = new AtomicReference<>("upstream unavailable");
    AtomicInteger changes = new AtomicInteger();
    ValueOrFieldControl control =
        builder()
            .onStatus(
                message -> {
                  assertSame(display, Display.getCurrent());
                  messages.add(message);
                })
            .onChange(changes::incrementAndGet)
            .fieldProvider(
                () -> {
                  if (failure.get() != null) throw new HopException(failure.get());
                  return new String[] {"available"};
                })
            .build();
    assertEquals(List.of(""), messages);
    ValueOrField original = new ValueOrField(SourceMode.FIELD, "${P}/saved", "manual");
    int emptyHeight = control.computeSize(SWT.DEFAULT, SWT.DEFAULT).y;
    control.setValue(original);
    assertEquals(2, messages.size());
    assertTrue(messages.getLast().contains("upstream unavailable"));
    assertEquals(emptyHeight, control.computeSize(SWT.DEFAULT, SWT.DEFAULT).y);
    control.refreshFields();
    assertEquals(2, messages.size());
    failure.set(null);
    control.refreshFields();
    assertEquals("", messages.getLast());
    assertEquals(3, messages.size());
    assertEquals(original, control.getValue());
    assertEquals(0, changes.get());
    for (Control child : control.getChildren()) {
      if (child instanceof Label label) {
        assertFalse(label.getVisible());
        assertTrue(((GridData) label.getLayoutData()).exclude);
      }
    }
  }

  @Test
  void fieldStatusCallbackExceptionsAreNotReinterpretedAndUpdatingIsRestored() {
    AtomicReference<String> failure = new AtomicReference<>("provider failed");
    AtomicReference<String> throwOn = new AtomicReference<>("error");
    RuntimeException callbackFailure = new IllegalStateException("status callback failed");
    AtomicInteger changes = new AtomicInteger();
    ValueOrFieldControl control =
        builder()
            .onChange(changes::incrementAndGet)
            .onStatus(
                message -> {
                  if ((throwOn.get().equals("error") && !message.isEmpty())
                      || (throwOn.get().equals("clear") && message.isEmpty()))
                    throw callbackFailure;
                })
            .fieldProvider(
                () -> {
                  if (failure.get() != null) throw new HopException(failure.get());
                  return new String[] {"a"};
                })
            .build();
    assertSame(callbackFailure, assertThrows(RuntimeException.class, control::refreshFields));
    throwOn.set("clear");
    failure.set(null);
    assertSame(callbackFailure, assertThrows(RuntimeException.class, control::refreshFields));
    text(control).setText("still editable");
    assertEquals(1, changes.get());
  }

  @Test
  void browseStatusAndChangeCallbacksPropagateWithoutBecomingPickerErrors() {
    List<String> messages = new ArrayList<>();
    AtomicReference<Boolean> failPicker = new AtomicReference<>(true);
    AtomicReference<Boolean> failClear = new AtomicReference<>(false);
    AtomicReference<Boolean> failChange = new AtomicReference<>(false);
    RuntimeException callbackFailure = new IllegalStateException("caller failure");
    ValueOrFieldControl control =
        builder()
            .editor(EditorKind.FILE_OPEN)
            .onStatus(
                message -> {
                  messages.add(message);
                  if (failClear.get() && message.isEmpty()) throw callbackFailure;
                })
            .onChange(
                () -> {
                  if (failChange.get()) throw callbackFailure;
                })
            .browseStrategy(
                (sh, vars, value, kind, exts, names) -> {
                  if (failPicker.get()) throw new HopException("picker failure");
                  return Optional.of("picked");
                })
            .build();
    Button browse = all(control, Button.class).getFirst();
    click(browse);
    assertTrue(messages.getLast().contains("picker failure"));
    failPicker.set(false);
    failClear.set(true);
    assertSame(callbackFailure, assertThrows(RuntimeException.class, () -> click(browse)));
    assertEquals("", messages.getLast());
    assertEquals(3, messages.size());
    failClear.set(false);
    failChange.set(true);
    assertSame(callbackFailure, assertThrows(RuntimeException.class, () -> click(browse)));
    assertEquals(3, messages.size());
    assertEquals("picked", control.getValue().configuredValue());
  }

  @Test
  void externalLabelAndEditorsStayCenteredWithWrappingMessagesAndDifferentFonts() {
    FontData[] data = shell.getFont().getFontData();
    for (FontData item : data) item.setHeight(item.getHeight() + 6);
    Font large = new Font(display, data);
    try {
      for (EditorKind kind : EditorKind.values()) {
        for (boolean enlarged : new boolean[] {false, true}) {
          Composite form = new Composite(shell, SWT.NONE);
          form.setLayout(new GridLayout(2, false));
          Label caption = new Label(form, SWT.NONE);
          caption.setText("Wert");
          caption.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
          Label spacer = new Label(form, SWT.NONE);
          spacer.setLayoutData(new GridData());
          Label status = new Label(form, SWT.WRAP);
          GridData statusData = new GridData(SWT.FILL, SWT.TOP, true, false);
          statusData.widthHint = 0;
          status.setLayoutData(statusData);
          Label following = new Label(form, SWT.NONE);
          following.setText("Following row");
          following.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false, 2, 1));
          AtomicReference<String> failure = new AtomicReference<>();
          ValueOrFieldControl control =
              ValueOrFieldControl.builder(form, new Variables())
                  .editor(kind)
                  .fieldProvider(
                      () -> {
                        if (failure.get() != null) throw new HopException(failure.get());
                        return new String[] {"a"};
                      })
                  .onStatus(
                      message -> {
                        status.setText(message);
                        for (Label part : new Label[] {spacer, status}) {
                          part.setVisible(!message.isEmpty());
                          ((GridData) part.getLayoutData()).exclude = message.isEmpty();
                        }
                        form.layout(true, true);
                      })
                  .build();
          control.moveAbove(spacer);
          control.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
          if (enlarged) {
            form.setFont(large);
            for (Control child : all(form, Control.class)) child.setFont(large);
          }
          try {
            for (int width : new int[] {520, 980}) {
              shell.setSize(width, 750);
              for (SourceMode mode : SourceMode.values()) {
                ValueOrField original = new ValueOrField(mode, "${P}/saved", "manual");
                failure.set(null);
                control.setValue(original);
                shell.layout(true, true);
                Combo source = all(control, Combo.class).getFirst();
                Control active = mode == SourceMode.FIELD ? field(control) : text(control);
                Rectangle initialCaption = boundsIn(form, caption);
                Rectangle initialControl = boundsIn(form, control);
                assertCentered(initialCaption, boundsIn(form, source));
                assertCentered(initialCaption, boundsIn(form, active));
                for (String message :
                    new String[] {
                      "short failure", "Long upstream metadata failure with details. ".repeat(18)
                    }) {
                  failure.set(message);
                  control.refreshFields();
                  shell.layout(true, true);
                  assertEquals(initialCaption, boundsIn(form, caption), kind + " label moved");
                  assertEquals(initialControl, boundsIn(form, control), kind + " input row moved");
                  assertEquals(original, control.getValue());
                  assertTrue(status.getVisible());
                  assertEquals(control.getBounds().x, status.getBounds().x);
                  assertTrue(status.getBounds().y >= initialControl.y + initialControl.height);
                  assertTrue(
                      following.getBounds().y >= status.getBounds().y + status.getBounds().height);
                  if (message.length() > 100) {
                    assertTrue(
                        status.getSize().y > status.computeSize(SWT.DEFAULT, SWT.DEFAULT).y,
                        "Long message must wrap at the allocated width");
                  }
                }
                failure.set(null);
                control.refreshFields();
                shell.layout(true, true);
                assertFalse(status.getVisible());
                assertFalse(spacer.getVisible());
                assertEquals(initialCaption, boundsIn(form, caption));
                assertEquals(initialControl, boundsIn(form, control));
              }
            }
          } finally {
            form.dispose();
          }
        }
      }
    } finally {
      large.dispose();
    }
  }

  private static Rectangle boundsIn(Composite parent, Control child) {
    return display.map(child.getParent(), parent, child.getBounds());
  }

  private static void assertCentered(Rectangle caption, Rectangle editor) {
    double delta = Math.abs(caption.y + caption.height / 2.0 - editor.y - editor.height / 2.0);
    assertTrue(
        delta <= 1.0,
        () -> "Vertical centers differ by " + delta + ": " + caption + " vs " + editor);
  }

  private ValueOrFieldControl.Builder builder() {
    return ValueOrFieldControl.builder(shell, new Variables());
  }

  static void switchMode(ValueOrFieldControl c, int mode) {
    Combo source = all(c, Combo.class).getFirst();
    source.select(mode);
    source.notifyListeners(SWT.Selection, new Event());
  }

  static void click(Button b) {
    b.notifyListeners(SWT.Selection, new Event());
  }

  static Combo field(ValueOrFieldControl c) {
    return all(c, Combo.class).get(1);
  }

  static TextVar text(ValueOrFieldControl c) {
    return all(c, TextVar.class).getFirst();
  }

  static <T extends Control> List<T> all(Composite parent, Class<T> type) {
    List<T> result = new ArrayList<>();
    for (Control child : parent.getChildren()) {
      if (type.isInstance(child)) result.add(type.cast(child));
      if (child instanceof Composite composite) result.addAll(all(composite, type));
    }
    return result;
  }

  static void pump() {
    while (display.readAndDispatch()) {}
  }
}
