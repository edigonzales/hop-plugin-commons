#!/usr/bin/env python3
"""Verify the public snapshot and compile a consumer using a fresh Maven cache."""
import hashlib
import io
import json
import os
from pathlib import Path
import platform
import subprocess
import tempfile
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NS = '{http://maven.apache.org/POM/4.0.0}'
VERSION = ET.parse(ROOT / 'pom.xml').getroot().find(NS + 'version').text
BASE = 'https://jars.interlis.guru/snapshots/ch/so/agi'
ARTIFACTS = ['hop-plugin-commons-parent', 'hop-plugin-commons-core', 'hop-plugin-commons-ui']


def read(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return response.read()


published = {}
for artifact in ARTIFACTS:
    base = f'{BASE}/{artifact}/{VERSION}'
    metadata = ET.fromstring(read(f'{base}/maven-metadata.xml'))
    entries = {
        (s.findtext('extension'), s.findtext('classifier', '')): s.findtext('value')
        for s in metadata.findall('versioning/snapshotVersions/snapshotVersion')
    }
    required = [('pom', '')]
    if artifact != ARTIFACTS[0]:
        required += [('jar', ''), ('jar', 'sources'), ('jar', 'javadoc')]
    published[artifact] = {}
    for extension, classifier in required:
        resolved = entries[(extension, classifier)]
        suffix = '-' + classifier if classifier else ''
        url = f'{base}/{artifact}-{resolved}{suffix}.{extension}'
        data = read(url)
        expected = read(url + '.sha1').decode().split()[0]
        assert hashlib.sha1(data).hexdigest() == expected, f'Checksum mismatch: {url}'
        if extension == 'jar':
            names = zipfile.ZipFile(io.BytesIO(data)).namelist()
            assert names, f'Empty JAR: {url}'
            if not classifier:
                assert not any(n.startswith(('org/apache/hop/', 'org/eclipse/', 'org/geotools/')) for n in names)
        else:
            ET.fromstring(data)
        published[artifact][extension + (':' + classifier if classifier else '')] = resolved

system = platform.system()
if system == 'Darwin':
    swt = 'org.eclipse.swt.cocoa.macosx.' + ('aarch64' if platform.machine() == 'arm64' else 'x86_64')
elif system == 'Windows':
    swt = 'org.eclipse.swt.win32.win32.x86_64'
else:
    swt = 'org.eclipse.swt.gtk.linux.x86_64'

with tempfile.TemporaryDirectory(prefix='hop-commons-consumer-') as temporary:
    project = Path(temporary)
    dependencies = f'''<dependency><groupId>ch.so.agi</groupId><artifactId>hop-plugin-commons-ui</artifactId><version>{VERSION}</version></dependency>'''
    for artifact in ['hop-core', 'hop-ui']:
        dependencies += f'''<dependency><groupId>org.apache.hop</groupId><artifactId>{artifact}</artifactId><version>2.19.0</version><scope>provided</scope><exclusions><exclusion><groupId>org.eclipse.platform</groupId><artifactId>*</artifactId></exclusion></exclusions></dependency>'''
    dependencies += f'''<dependency><groupId>org.eclipse.platform</groupId><artifactId>{swt}</artifactId><version>3.134.0</version><scope>provided</scope><exclusions><exclusion><groupId>org.eclipse.platform</groupId><artifactId>org.eclipse.swt</artifactId></exclusion></exclusions></dependency>'''
    (project / 'pom.xml').write_text(f'''<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
      <groupId>example</groupId><artifactId>snapshot-consumer</artifactId><version>1.0</version>
      <properties><maven.compiler.release>21</maven.compiler.release></properties>
      <repositories><repository><id>interlis-guru</id><url>https://jars.interlis.guru/snapshots/</url><releases><enabled>false</enabled></releases><snapshots><enabled>true</enabled></snapshots></repository></repositories>
      <dependencies>{dependencies}</dependencies>
      <build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId><version>3.14.0</version></plugin></plugins></build>
      </project>''')
    source = project / 'src/main/java/example/Consumer.java'
    source.parent.mkdir(parents=True)
    source.write_text('''package example;
import ch.so.agi.hop.commons.core.*;
import ch.so.agi.hop.commons.ui.*;
import org.apache.hop.core.variables.IVariables;
import org.eclipse.swt.widgets.Composite;
public class Consumer {
  public ValueOrFieldControl create(Composite parent, IVariables variables) {
    ValueOrFieldControl control = ValueOrFieldControl.builder(parent, variables).editor(EditorKind.FILE_OPEN).build();
    control.setValue(new ValueOrField(SourceMode.FIELD, "${PROJECT_HOME}/a.tif", "path"));
    return control;
  }
}''')
    # Empty settings prevent mirrors or developer-local repositories from masking a missing upload.
    settings = project / 'settings.xml'
    settings.write_text('<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"/>')
    subprocess.run(['mvn.cmd' if os.name == 'nt' else 'mvn', '-B', '-ntp', '-U',
                    '-s', str(settings), '-gs', str(settings),
                    f'-Dmaven.repo.local={project / "repository"}', 'compile'], cwd=project, check=True)

(ROOT / 'target').mkdir(exist_ok=True)
(ROOT / 'target/published-snapshot.json').write_text(json.dumps(published, indent=2) + '\n')
print(json.dumps(published, indent=2))
