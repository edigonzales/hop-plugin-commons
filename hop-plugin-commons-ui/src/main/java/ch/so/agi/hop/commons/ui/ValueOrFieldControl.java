// SPDX-License-Identifier: MIT
package ch.so.agi.hop.commons.ui;

import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.apache.hop.core.variables.IVariables;
import org.apache.hop.i18n.BaseMessages;
import org.apache.hop.ui.core.PropsUi;
import org.apache.hop.ui.core.widget.TextVar;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

/**
 * A string option with an explicit source selector. All methods must run on the SWT UI thread. The
 * caller owns the label, persistence, validation and commit/cancel semantics.
 */
public final class ValueOrFieldControl extends Composite {
  private final Builder config;
  private final Combo source;
  private final Composite editors;
  private final StackLayout stack;
  private final Composite configuredPage;
  private final Composite fieldPage;
  private final TextVar configured;
  private final Combo field;
  private final Label status;
  private final Button browse;
  private final Button refresh;
  private SourceMode mode = SourceMode.CONFIGURED;
  private boolean updating;
  private boolean fieldsLoaded;
  private String statusMessage;
  private ValueOrField lastValue = new ValueOrField(SourceMode.CONFIGURED, "", "");

  private ValueOrFieldControl(Builder builder) {
    super(builder.parent, SWT.NONE);
    config = builder.copy();
    GridLayout layout = new GridLayout(2, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    setLayout(layout);
    source = new Combo(this, SWT.READ_ONLY);
    source.setItems(message("Configured"), message("Field"));
    source.select(0);
    source.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
    editors = new Composite(this, SWT.NONE);
    editors.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    stack = new StackLayout();
    editors.setLayout(stack);
    configuredPage = page(editors);
    configured = new TextVar(config.variables, configuredPage, SWT.SINGLE | SWT.BORDER);
    configured.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
    if (config.editor != EditorKind.TEXT) {
      browse = new Button(configuredPage, SWT.PUSH);
      browse.setText(message("Browse"));
      browse.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
      browse.addListener(SWT.Selection, event -> browse());
    } else {
      browse = null;
    }
    fieldPage = page(editors);
    field = new Combo(fieldPage, SWT.DROP_DOWN | SWT.BORDER);
    field.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
    if (config.fieldProvider != null) {
      refresh = new Button(fieldPage, SWT.PUSH);
      refresh.setText(message("Refresh"));
      refresh.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));
      refresh.addListener(SWT.Selection, event -> refreshFields());
    } else {
      refresh = null;
    }
    status = new Label(this, SWT.WRAP);
    GridData statusData = new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1);
    statusData.widthHint = 320;
    statusData.exclude = true;
    status.setLayoutData(statusData);
    status.setVisible(false);
    PropsUi.setLook(this);
    PropsUi.setLook(configuredPage);
    PropsUi.setLook(fieldPage);
    PropsUi.setLook(status);
    configured.addModifyListener(event -> changed());
    field.addModifyListener(event -> changed());
    source.addListener(
        SWT.Selection,
        event -> {
          mode = source.getSelectionIndex() == 1 ? SourceMode.FIELD : SourceMode.CONFIGURED;
          showMode();
          setFocus();
          changed();
        });
    showMode();
  }

  public static Builder builder(Composite parent, IVariables variables) {
    return new Builder(parent, variables);
  }

  /** Loads both inputs without invoking the change callback. */
  public void setValue(ValueOrField value) {
    checkWidget();
    Objects.requireNonNull(value, "value");
    updating = true;
    try {
      mode = value.mode();
      configured.setText(value.configuredValue());
      field.setText(value.fieldName());
      source.select(mode == SourceMode.FIELD ? 1 : 0);
      lastValue = value;
      clearStatus();
      showMode();
    } finally {
      updating = false;
    }
  }

  /** Returns a snapshot, including the inactive editor's input. */
  public ValueOrField getValue() {
    checkWidget();
    return new ValueOrField(mode, configured.getText(), field.getText());
  }

  /** Refreshes suggestions, retaining the typed name and old suggestions if the provider fails. */
  public void refreshFields() {
    checkWidget();
    if (config.fieldProvider == null) return;
    boolean wasUpdating = updating;
    updating = true;
    try {
      String[] names;
      try {
        names = config.fieldProvider.getFieldNames();
      } catch (Exception e) {
        showError("FieldsError", e);
        return;
      }
      String[] suggestions =
          names == null
              ? new String[0]
              : Arrays.stream(names).filter(Objects::nonNull).distinct().toArray(String[]::new);
      String current = field.getText();
      field.setItems(suggestions);
      field.setText(current);
      fieldsLoaded = true;
      clearStatus();
    } finally {
      updating = wasUpdating;
    }
  }

  @Override
  public boolean setFocus() {
    checkWidget();
    return mode == SourceMode.FIELD ? field.setFocus() : configured.setFocus();
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (source == null) return;
    source.setEnabled(enabled);
    configured.setEnabled(enabled);
    field.setEnabled(enabled);
    if (browse != null) browse.setEnabled(enabled);
    if (refresh != null) refresh.setEnabled(enabled);
  }

  private void showMode() {
    boolean fromField = mode == SourceMode.FIELD;
    stack.topControl = fromField ? fieldPage : configuredPage;
    editors.setTabList(new Control[] {stack.topControl});
    editors.layout();
    if (fromField && !fieldsLoaded) refreshFields();
    if (!fromField) clearStatus();
    layout(true, true);
  }

  private void browse() {
    Optional<String> selection;
    try {
      selection =
          config.browseStrategy.browse(
              getShell(),
              config.variables,
              configured.getText(),
              config.editor,
              config.extensions.clone(),
              config.filterNames.clone());
    } catch (Exception e) {
      showError("BrowseError", e);
      return;
    }
    // Caller callbacks deliberately run outside the picker exception handler.
    selection.ifPresent(
        value -> {
          clearStatus();
          configured.setText(value);
        });
  }

  private void changed() {
    if (updating) return;
    ValueOrField value = getValue();
    if (!value.equals(lastValue)) {
      lastValue = value;
      config.onChange.run();
    }
  }

  private void clearStatus() {
    setStatus("");
  }

  private void showError(String key, Exception error) {
    setStatus(
        BaseMessages.getString(
            ValueOrFieldControl.class,
            "ValueOrField." + key,
            Objects.requireNonNullElse(error.getMessage(), error.getClass().getSimpleName())));
  }

  private void setStatus(String message) {
    if (message.equals(statusMessage)) return;
    statusMessage = message;
    if (config.onStatus != null) {
      // The internal label stays excluded, so messages cannot change this row's height.
      config.onStatus.accept(message);
      return;
    }
    status.setText(message);
    boolean visible = !message.isEmpty();
    status.setVisible(visible);
    ((GridData) status.getLayoutData()).exclude = !visible;
    requestLayout();
  }

  private static String message(String key) {
    return BaseMessages.getString(ValueOrFieldControl.class, "ValueOrField." + key);
  }

  private static Composite page(Composite parent) {
    Composite page = new Composite(parent, SWT.NONE);
    GridLayout layout = new GridLayout(2, false);
    layout.marginWidth = 0;
    layout.marginHeight = 0;
    page.setLayout(layout);
    return page;
  }

  /** Builder settings are copied on build; instances do not share mutable configuration. */
  public static final class Builder {
    private final Composite parent;
    private final IVariables variables;
    private EditorKind editor = EditorKind.TEXT;
    private String[] extensions = {"*"};
    private String[] filterNames = {"*"};
    private InputFieldProvider fieldProvider;
    private BrowseStrategy browseStrategy = new HopBrowseStrategy();
    private Runnable onChange = () -> {};
    private Consumer<String> onStatus;

    private Builder(Composite parent, IVariables variables) {
      this.parent = Objects.requireNonNull(parent, "parent");
      this.variables = Objects.requireNonNull(variables, "variables");
    }

    public Builder editor(EditorKind editor) {
      this.editor = Objects.requireNonNull(editor, "editor");
      return this;
    }

    /** Sets glob filters, using each glob as its display name. */
    public Builder fileExtensions(String... extensions) {
      return fileFilters(extensions, extensions);
    }

    /** Sets matching glob and display-name arrays. */
    public Builder fileFilters(String[] extensions, String[] names) {
      Objects.requireNonNull(extensions, "extensions");
      Objects.requireNonNull(names, "names");
      if (extensions.length == 0 || extensions.length != names.length) {
        throw new IllegalArgumentException("Filters must be nonempty arrays of equal length");
      }
      Arrays.stream(extensions).forEach(Objects::requireNonNull);
      Arrays.stream(names).forEach(Objects::requireNonNull);
      this.extensions = extensions.clone();
      this.filterNames = names.clone();
      return this;
    }

    public Builder fieldProvider(InputFieldProvider provider) {
      this.fieldProvider = provider;
      return this;
    }

    public Builder browseStrategy(BrowseStrategy strategy) {
      this.browseStrategy = Objects.requireNonNull(strategy, "strategy");
      return this;
    }

    public Builder onChange(Runnable callback) {
      this.onChange = Objects.requireNonNull(callback, "callback");
      return this;
    }

    /**
     * Replaces the internal message row with a caller-owned status display. Invoked synchronously
     * on the UI thread once during build with an empty string, then whenever the translated message
     * changes. An empty string clears the status. Does not trigger onChange; callback exceptions
     * propagate to the caller. Create the receiving controls before building this widget.
     *
     * @param callback external status handler (non-null)
     * @return this builder
     */
    public Builder onStatus(Consumer<String> callback) {
      this.onStatus = Objects.requireNonNull(callback, "callback");
      return this;
    }

    public ValueOrFieldControl build() {
      return new ValueOrFieldControl(this);
    }

    private Builder copy() {
      Builder copy =
          new Builder(parent, variables)
              .editor(editor)
              .fileFilters(extensions, filterNames)
              .fieldProvider(fieldProvider)
              .browseStrategy(browseStrategy)
              .onChange(onChange);
      copy.onStatus = onStatus;
      return copy;
    }
  }
}
