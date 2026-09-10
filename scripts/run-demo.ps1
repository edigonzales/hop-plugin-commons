$ErrorActionPreference = 'Stop'
Set-Location (Join-Path $PSScriptRoot '..')
mvn -B -ntp -DskipTests install
if ($LASTEXITCODE -ne 0) { throw 'Build failed' }
mvn -B -ntp -pl hop-plugin-commons-ui org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/demo-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Classpath resolution failed' }
$deps = (Get-Content hop-plugin-commons-ui/target/demo-classpath.txt -Raw).Trim()
$classpath = "hop-plugin-commons-ui/target/test-classes;hop-plugin-commons-ui/target/classes;$deps"
$env:HOP_CONFIG_FOLDER = Join-Path $PWD 'hop-plugin-commons-ui/target/demo-config'
New-Item -ItemType Directory -Force $env:HOP_CONFIG_FOLDER | Out-Null
java "-DHOP_CONFIG_FOLDER=$env:HOP_CONFIG_FOLDER" "-DHOP_AUDIT_FOLDER=$PWD/hop-plugin-commons-ui/target/demo-audit" -cp $classpath ch.so.agi.hop.commons.ui.ValueOrFieldDemo
if ($LASTEXITCODE -ne 0) { throw 'Demo failed' }
