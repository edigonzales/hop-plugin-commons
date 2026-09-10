#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mvn -B -ntp -DskipTests install
mvn -B -ntp -pl hop-plugin-commons-ui org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/demo-classpath.txt
classpath="hop-plugin-commons-ui/target/test-classes:hop-plugin-commons-ui/target/classes:$(cat hop-plugin-commons-ui/target/demo-classpath.txt)"
args=()
if [[ "$(uname)" == Darwin ]]; then args+=(-XstartOnFirstThread); fi
export HOP_CONFIG_FOLDER="$PWD/hop-plugin-commons-ui/target/demo-config"
mkdir -p "$HOP_CONFIG_FOLDER"
exec java "${args[@]}" "-DHOP_CONFIG_FOLDER=$HOP_CONFIG_FOLDER" "-DHOP_AUDIT_FOLDER=$PWD/hop-plugin-commons-ui/target/demo-audit" -cp "$classpath" ch.so.agi.hop.commons.ui.ValueOrFieldDemo
