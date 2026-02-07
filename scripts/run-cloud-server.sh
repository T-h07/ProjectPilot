#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v mvn >/dev/null 2>&1; then
  echo "Maven not found. Install Maven or add it to PATH."
  exit 1
fi

echo "Building server..."
mvn -q -DskipTests package

echo "Copying dependencies..."
mvn -q dependency:copy-dependencies -DoutputDirectory=target/server/lib -DincludeScope=runtime

JAR="$(ls target/ProjectPilot-*.jar | head -n 1)"
if [[ -z "${JAR:-}" ]]; then
  echo "Build failed: no jar found in target/"
  exit 1
fi

echo "Starting ProjectPilot Cloud Server..."
java -cp "$JAR:target/server/lib/*" com.projectpilot.server.CloudServerMain
