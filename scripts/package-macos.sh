#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "The macOS package must be built on macOS." >&2
  exit 1
fi

project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir"

if [[ -z "${JAVA_HOME:-}" ]]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 21)"
fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if [[ "$("$JAVA_HOME/bin/javac" -version 2>&1)" != javac\ 21* ]]; then
  echo "JDK 21 is required to build and package the application." >&2
  exit 1
fi

./gradlew test installDist

version="0.1.0"
input_dir="build/install/ssh-tunnel-manager/lib"
main_jar="ssh-tunnel-manager-${version}.jar"
if [[ ! -f "$input_dir/$main_jar" ]]; then
  echo "Application JAR not found: $input_dir/$main_jar" >&2
  exit 1
fi

mkdir -p build/packages
"$JAVA_HOME/bin/jpackage" \
  --type dmg \
  --name "SSH Tunnel Manager" \
  --mac-package-name "SSH Tunnel" \
  --mac-package-identifier "it.flossconsulting.sshtunnelmanager" \
  --app-version "$version" \
  --vendor "Flossconsulting" \
  --description "Desktop SSH local port forward manager" \
  --input "$input_dir" \
  --main-jar "$main_jar" \
  --main-class "it.flossconsulting.sshtunnel.Main" \
  --dest build/packages \
  --add-modules java.base,java.desktop,java.management,java.naming,java.rmi,java.security.jgss,java.sql,java.logging,jdk.crypto.ec,jdk.unsupported

echo "DMG created in build/packages/"
