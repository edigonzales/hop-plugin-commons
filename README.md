# Hop Plugin Commons

Wiederverwendbare SWT-Komponente für **konfigurierten Wert oder Eingabespalte** in
Apache Hop Desktop **2.19.0**. Java **21**, SWT **3.134.0**, MIT-Lizenz.

| Artefakt (`ch.so.agi`, Version `0.1.0-SNAPSHOT`) | Inhalt |
| --- | --- |
| `hop-plugin-commons-parent` | Maven-Parent und Versionsverwaltung |
| `hop-plugin-commons-core` | `SourceMode`, unveränderlicher `ValueOrField`-Record; keine Laufzeitabhängigkeiten |
| `hop-plugin-commons-ui` | `ValueOrFieldControl`, Editorvarianten und Erweiterungsschnittstellen |

Die Bibliothek löst keine Variablen auf, liest keine Pipelinezeilen und entscheidet
nicht über Dateiformate, VFS-Unterstützung, Leerwerte oder Schreibverhalten.
Bestehende Plugin-Metadaten und XML-Tags können unverändert bleiben. Hop Web und
ältere Hop-Versionen werden nicht als kompatibel zugesichert.

## Build und Demo

Voraussetzungen: JDK 21 oder neuer, Maven 3.6.3 oder neuer und ein grafischer Desktop.

```sh
mvn -B -ntp clean verify
./scripts/run-demo.sh
```

Windows PowerShell: `./scripts/run-demo.ps1`. Auf Linux ohne Desktop:
`xvfb-run -a mvn -B -ntp clean verify`. macOS-Tests und Demo starten automatisch
mit `-XstartOnFirstThread`. SWT wird für Linux/Windows x86_64 sowie macOS ARM64
und Intel ausgewählt; CI prüft Linux x86_64, Windows x86_64 und macOS ARM64.

Die Demo bietet vier Editoren: Text, Datei öffnen, Datei speichern und Verzeichnis.
Sie verwendet echte Hop-Dateidialoge; die Checkbox im Startfenster schaltet auf
native Betriebssystemdialoge um. Die Fehler-Checkbox im jeweiligen Editor simuliert
fehlende Eingangsmetadaten. Im Feldmodus kann die Liste über **Refresh/Aktualisieren**
erneut geladen werden. Nur **OK** übernimmt den Zustand. **Abbrechen**, Escape oder
Schliessen verwerfen Änderungen. Nach erneutem Öffnen erscheinen die bestätigten Werte.
Der Demo-Zustand bleibt nur während des laufenden Programms erhalten.

`hop-ui-rcp` gehört ausschliesslich zum Test-/Demo-Classpath: Hop 2.19 stellt dort
Desktop-Implementierungen wie `GuiResourceImpl` bereit. Die Demo benötigt keinen
installierten Hop-Plugin und wird nicht als eigenes Artefakt veröffentlicht.

## Maven-Einbindung

```xml
<repositories>
  <repository>
    <id>sogeo-snapshots</id>
    <url>https://jars.interlis.guru/snapshots/</url>
    <releases><enabled>false</enabled></releases>
    <snapshots><enabled>true</enabled></snapshots>
  </repository>
</repositories>
<dependencies>
  <dependency>
    <groupId>ch.so.agi</groupId>
    <artifactId>hop-plugin-commons-ui</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

Core wird transitiv eingebunden. Das konsumierende Plugin deklariert seine eigenen
Hop- und plattformspezifischen SWT-Compile-Abhängigkeiten weiterhin als `provided`.
`provided`-Abhängigkeiten der Bibliothek werden nicht transitiv an den Consumer
weitergegeben. Das Skript `scripts/verify-snapshot.py` enthält einen vollständigen,
kompilierbaren Consumer mit expliziten Host-Abhängigkeiten.

## Widget verwenden

```java
import ch.so.agi.hop.commons.core.SourceMode;
import ch.so.agi.hop.commons.core.ValueOrField;
import ch.so.agi.hop.commons.ui.EditorKind;
import ch.so.agi.hop.commons.ui.ValueOrFieldControl;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Label;

// Gemeinsame Labelspalte, Eingabezeile und separate Statuszeile.
parent.setLayout(new GridLayout(2, false));
Label label = new Label(parent, SWT.NONE);
label.setText("Rasterquelle");
label.setLayoutData(new GridData(SWT.LEFT, SWT.CENTER, false, false));

// Vor build() anlegen: onStatus wird beim Aufbau einmal mit "" aufgerufen.
Label statusSpacer = new Label(parent, SWT.NONE);
statusSpacer.setLayoutData(new GridData());
Label status = new Label(parent, SWT.WRAP);
GridData statusData = new GridData(SWT.FILL, SWT.TOP, true, false);
statusData.widthHint = 0; // Meldung umbrechen, nicht die bevorzugte Dialogbreite vergrössern.
status.setLayoutData(statusData);

ValueOrFieldControl path = ValueOrFieldControl.builder(parent, variables)
    .editor(EditorKind.FILE_OPEN)
    .fileFilters(new String[] {"*.tif", "*.tiff", "*"},
                 new String[] {"GeoTIFF", "TIFF", "Alle Dateien"})
    .fieldProvider(() -> {
      var row = pipelineMeta.getPrevTransformFields(variables, transformMeta);
      return row == null ? new String[0] : row.getFieldNames();
    })
    .onStatus(message -> {
      status.setText(message);
      for (Label part : new Label[] {statusSpacer, status}) {
        part.setVisible(!message.isEmpty());
        ((GridData) part.getLayoutData()).exclude = message.isEmpty();
      }
      parent.layout(true, true); // Neu anordnen, das Fenster nicht mit pack() vergrössern.
    })
    .onChange(() -> input.setChanged())
    .build();

path.moveAbove(statusSpacer); // Reihenfolge: Label, Widget, Platzhalter, Status.
path.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
path.setValue(new ValueOrField(SourceMode.CONFIGURED,
    "${PROJECT_HOME}/data/source.tif", "raster_path"));
```

Das externe Label wird zur Eingabezeile zentriert. Die separate Statuszeile liegt
unter dem Widget und verändert weder dessen Höhe noch die Position des Labels.
Beim Ausblenden werden Status und Platzhalter ausgeschlossen, sodass keine leere
Zeile zurückbleibt. Lange Meldungen umbrechen bei der verfügbaren Breite. Der Dialog
bleibt für sinnvolle Mindestgrösse und gegebenenfalls Scrollmöglichkeiten zuständig.

`onStatus(Consumer<String>)` ersetzt die interne Fehleranzeige durch die Anzeige
des Aufrufers. Der Callback erhält einmal während `build()` einen leeren String
und danach nur bei Änderungen den übersetzten Meldungstext. `""` löscht die Meldung.
Alle Aufrufe erfolgen synchron auf dem SWT-UI-Thread und lösen kein `onChange` aus.
Callback-Exceptions werden weitergereicht, nicht als Feldlade- oder Browse-Fehler
angezeigt. Der Aufrufer hält die externen Anzeigeelemente so lange wie das Widget am
Leben. Ohne `onStatus` bleibt die bisherige interne Fehleranzeige vollständig erhalten;
das oben gezeigte Muster ist für stabil ausgerichtete externe Labels vorgesehen.

Für ein Verzeichnis genügt
`.editor(EditorKind.DIRECTORY)`, für eine Ausgabedatei `FILE_SAVE`. Ohne Editorangabe
wird `TEXT` verwendet. Ohne Feldanbieter sind Feldnamen weiterhin manuell eingebbar.

Alle Widget-Methoden und Callbacks laufen auf dem SWT-UI-Thread. Der Feldanbieter
wird beim ersten Wechsel in den Feldmodus (auch durch `setValue`) geladen. Nach
Erfolg werden Vorschläge bis zu `refreshFields()` wiederverwendet. Nach Fehlern
ist ein erneuter Versuch über Aktualisieren oder einen Moduswechsel möglich.
Ein Provider liefert Vorschläge; er validiert oder ersetzt keinen eingegebenen Namen.
Er sollte zügig antworten und keine Pipelineausführung starten.

`setValue` und `refreshFields` melden keine Änderungen. `onChange` wird bei
inhaltlichen Benutzeränderungen aufgerufen, einschliesslich Moduswechseln.
`getValue()` enthält immer beide Texte; Leerzeichen und `${...}` bleiben unverändert.
`ValueOrField` normalisiert null-Texte zu `""` und verbietet einen null-Modus.
Es gibt keine automatische Deutung eines Textes als Feldreferenz.

Die Texte der Komponente folgen Hops Spracheinstellung (Deutsch/Englisch).

## Bestehende Metadaten adaptieren

Beispiel für ein Plugin mit drei vorhandenen Eigenschaften (Getter/Setter sind
illustrativ und müssen auf die tatsächlichen Metadaten abgebildet werden):

```java
boolean originallyChanged = input.hasChanged();
path.setValue(new ValueOrField(
    input.isFromField() ? SourceMode.FIELD : SourceMode.CONFIGURED,
    input.getConfiguredPath(), input.getPathField()));

// Erst in ok(), nach pluginspezifischer Validierung:
ValueOrField selection = path.getValue();
input.setFromField(selection.mode() == SourceMode.FIELD);
input.setConfiguredPath(selection.configuredValue());
input.setPathField(selection.fieldName());

// In cancel(), wenn onChange input.setChanged() verwendet:
input.setChanged(originallyChanged);
```

Der Dialog darf während der Bearbeitung keine sonstigen Metadatenwerte verändern.
Beim Abbrechen wird das Widget verworfen. In Metadaten mit nur einem Textfeld
plus Flag kann der Adapter beim Bestätigen nur den aktiven Text speichern; der
inaktive Text bleibt dann lediglich während der Dialogsitzung erhalten. Eine
Migration zu zwei gespeicherten Texten ist eine separate Plugin-Entscheidung.

## Eigene Dateiauswahl

`BrowseStrategy.browse(shell, variables, currentValue, editor, extensions, names)`
liefert `Optional<String>`; `Optional.empty()` bedeutet Abbrechen. Die Strategie
kann eine `HopException` werfen, die als Meldung erscheint. Sie bekommt Kopien der
Filterarrays. Sie soll den sichtbaren Editor nicht selbst verändern.

```java
.browseStrategy((shell, vars, current, editor, extensions, names) -> {
  String chosen = myLocalOnlyPicker(shell, current); // pluginspezifischer Helfer
  return Optional.ofNullable(chosen);
})
```

Die Standardstrategie verwendet `BaseDialog.presentFileDialog` beziehungsweise
`presentDirectoryDialog` aus Hop 2.19. Dateifilter sind Auswahlhilfen, keine
Formatvalidierung. Ein VFS-fähiger Picker garantiert nicht, dass ein Reader alle
angebotenen Quellen unterstützt.

## Plugin-Paket

Core und UI als normale JARs in das `lib/`-Verzeichnis des jeweiligen Plugins legen.
Beispiel für einen zusätzlichen Maven-Assembly-DependencySet:

```xml
<dependencySet>
  <outputDirectory>plugins/transforms/my-plugin/lib</outputDirectory>
  <useProjectArtifact>false</useProjectArtifact>
  <unpack>false</unpack>
  <scope>runtime</scope>
  <includes>
    <include>ch.so.agi:hop-plugin-commons-core</include>
    <include>ch.so.agi:hop-plugin-commons-ui</include>
  </includes>
</dependencySet>
```

Hop, SWT und `hop-ui-rcp` nicht mitliefern. Ein gemeinsames manuell installiertes
GUI-Plugin ist nicht nötig. Die Bibliotheksobjekte verbleiben innerhalb des jeweiligen
Plugin-Classloaders; sie sind kein Austauschvertrag zwischen verschiedenen Plugins.

## Snapshots veröffentlichen

Der Workflow `.github/workflows/maven.yml` verwendet den zentralen Maven-Library-
Vertrag aus `hop-plugin-ci`. Er prüft Ubuntu, Windows und macOS jeweils mit Java
21 und 25. Ubuntu/Java 21 ist der kanonische Lauf und erzeugt das einzige
veröffentlichbare Artefaktbundle; die übrigen fünf Läufe prüfen nur die
Kompatibilität.

Nach erfolgreicher Matrix veröffentlicht ein Push auf `main` den geprüften Commit.
Zusätzlich ist ein manueller Aufruf auf `main` möglich. Pull Requests publizieren
nicht; GitHub-Releases sind für Commons nicht vorgesehen.

Repository-Secrets:

- `INTERLIS_MAVEN_USERNAME`
- `INTERLIS_MAVEN_TOKEN`

Die Server-ID lautet `sogeo-snapshots`, das Deployment-Ziel
`https://jars.interlis.guru/snapshots/`. Das kanonische Bundle enthält den Parent-
POM und beide Module inklusive Sources/Javadoc. Der Publish-Workflow lädt dieses
Bundle herunter und veröffentlicht exakt diese Dateien mit `deploy-file`; es gibt
keinen Neubuild im Publish-Schritt. Veröffentlichungen für `main` werden
serialisiert.

Nach dem Upload prüft `scripts/verify-snapshot.py` die veröffentlichten POMs und
JARs über normale Maven-SNAPSHOT-Auflösung mit `-U`. Das Skript kompiliert ein
separates Beispiel mit leeren Maven-Settings und einem frischen temporären Cache;
Maven wählt dabei selbst den aktuellen Snapshot aus den Repository-Metadaten.
Ergebnis:
`target/published-snapshot.json`, zusätzlich als Workflow-Artefakt archiviert.

Für einen lokalen Build genügt `mvn -U -B -ntp clean verify` (Linux ohne Desktop:
`xvfb-run -a mvn -U -B -ntp clean verify`).
Veröffentlichungen erfolgen über den geschützten Workflow. Credentials nie in POM,
Git oder Kommandozeilenargumente schreiben.
