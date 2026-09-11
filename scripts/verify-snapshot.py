#!/usr/bin/env python3
"""Resolve and verify the public snapshot through Maven's normal resolver."""

from __future__ import annotations

import io
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import xml.etree.ElementTree as ET
import zipfile


ROOT = Path(__file__).resolve().parents[1]
NS = "{http://maven.apache.org/POM/4.0.0}"
VERSION = ET.parse(ROOT / "pom.xml").getroot().find(NS + "version").text
GROUP_ID = "ch.so.agi"
REPOSITORY = "sogeo-snapshots::default::https://jars.interlis.guru/snapshots/"
ARTIFACTS = {
    "hop-plugin-commons-parent": [("pom", "")],
    "hop-plugin-commons-core": [("pom", ""), ("jar", ""), ("jar", "sources"), ("jar", "javadoc")],
    "hop-plugin-commons-ui": [("pom", ""), ("jar", ""), ("jar", "sources"), ("jar", "javadoc")],
}


def maven_command(settings: Path, local_repository: Path, *arguments: str) -> list[str]:
    return [
        "mvn",
        "-U",
        "-B",
        "-ntp",
        "-s",
        str(settings),
        "-gs",
        str(settings),
        f"-Dmaven.repo.local={local_repository}",
        *arguments,
    ]


def inspect_downloaded(repository: Path) -> dict[str, dict[str, str]]:
    published: dict[str, dict[str, str]] = {}
    for artifact, files in ARTIFACTS.items():
        directory = repository / Path(GROUP_ID.replace(".", "/")) / artifact / VERSION
        if not directory.is_dir():
            raise AssertionError(f"Maven did not resolve {GROUP_ID}:{artifact}:{VERSION}")
        published[artifact] = {}
        for extension, classifier in files:
            suffix = f"-{classifier}" if classifier else ""
            path = directory / f"{artifact}-{VERSION}{suffix}.{extension}"
            if not path.is_file():
                raise AssertionError(f"Maven did not resolve {path}")
            data = path.read_bytes()
            if extension == "jar":
                names = zipfile.ZipFile(io.BytesIO(data)).namelist()
                if not names:
                    raise AssertionError(f"Empty JAR: {path}")
                if not classifier:
                    if "META-INF/LICENSE" not in names:
                        raise AssertionError(f"Missing license: {path}")
                    if any(name.startswith(("org/apache/hop/", "org/eclipse/", "org/geotools/")) for name in names):
                        raise AssertionError(f"Runtime dependency classes embedded in {path}")
            else:
                ET.fromstring(data)
            published[artifact][extension + (":" + classifier if classifier else "")] = path.name
    return published


def main() -> int:
    system = platform.system()
    if system == "Darwin":
        swt = "org.eclipse.swt.cocoa.macosx." + ("aarch64" if platform.machine() == "arm64" else "x86_64")
    elif system == "Windows":
        swt = "org.eclipse.swt.win32.win32.x86_64"
    else:
        swt = "org.eclipse.swt.gtk.linux.x86_64"

    with tempfile.TemporaryDirectory(prefix="hop-commons-consumer-") as temporary:
        project = Path(temporary)
        repository = project / "repository"
        dependencies = (
            f"<dependency><groupId>{GROUP_ID}</groupId>"
            f"<artifactId>hop-plugin-commons-ui</artifactId><version>{VERSION}</version></dependency>"
        )
        for artifact in ["hop-core", "hop-ui"]:
            dependencies += (
                f"<dependency><groupId>org.apache.hop</groupId><artifactId>{artifact}</artifactId>"
                f"<version>2.19.0</version><scope>provided</scope>"
                "<exclusions><exclusion><groupId>org.eclipse.platform</groupId>"
                "<artifactId>*</artifactId></exclusion></exclusions></dependency>"
            )
        dependencies += (
            f"<dependency><groupId>org.eclipse.platform</groupId><artifactId>{swt}</artifactId>"
            "<version>3.134.0</version><scope>provided</scope>"
            "<exclusions><exclusion><groupId>org.eclipse.platform</groupId>"
            "<artifactId>org.eclipse.swt</artifactId></exclusion></exclusions></dependency>"
        )
        (project / "pom.xml").write_text(
            f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
      <groupId>example</groupId><artifactId>snapshot-consumer</artifactId><version>1.0</version>
      <properties><maven.compiler.release>21</maven.compiler.release></properties>
      <repositories><repository><id>sogeo-snapshots</id><url>https://jars.interlis.guru/snapshots/</url>
      <releases><enabled>false</enabled></releases><snapshots><enabled>true</enabled><updatePolicy>always</updatePolicy><checksumPolicy>fail</checksumPolicy></snapshots></repository></repositories>
      <dependencies>{dependencies}</dependencies>
      <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><version>3.14.0</version></plugin></plugins></build>
      </project>''',
            encoding="utf-8",
        )
        source = project / "src/main/java/example/Consumer.java"
        source.parent.mkdir(parents=True)
        source.write_text(
            """package example;
import ch.so.agi.hop.commons.core.*;
import ch.so.agi.hop.commons.ui.*;
import org.apache.hop.core.variables.IVariables;
import org.eclipse.swt.widgets.Composite;
public class Consumer {
  public ValueOrFieldControl create(Composite parent, IVariables variables) {
    ValueOrFieldControl control = ValueOrFieldControl.builder(parent, variables).editor(EditorKind.FILE_OPEN).onStatus(message -> {}).build();
    control.setValue(new ValueOrField(SourceMode.FIELD, \"${PROJECT_HOME}/a.tif\", \"path\"));
    return control;
  }
}""",
            encoding="utf-8",
        )
        settings = project / "settings.xml"
        settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"/>', encoding="utf-8")
        subprocess.run(
            maven_command(settings, repository, "dependency:go-offline", "compile"),
            cwd=project,
            check=True,
        )
        for artifact, files in ARTIFACTS.items():
            for extension, classifier in files:
                coordinate = f"{GROUP_ID}:{artifact}:{VERSION}:{extension}"
                if classifier:
                    coordinate += f":{classifier}"
                subprocess.run(
                    maven_command(
                        settings,
                        repository,
                        "org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get",
                        f"-Dartifact={coordinate}",
                        "-Dtransitive=false",
                        f"-DremoteRepositories={REPOSITORY}",
                    ),
                    cwd=project,
                    check=True,
                )
        published = inspect_downloaded(repository)

    (ROOT / "target").mkdir(exist_ok=True)
    result = {"version": VERSION, "artifacts": published}
    (ROOT / "target/published-snapshot.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
