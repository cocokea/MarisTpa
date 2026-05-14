#!/bin/sh
set -e
APP_HOME=$(cd "${0%/*}" && pwd -P)
GRADLE_VERSION=9.1.0
DIST_DIR="$APP_HOME/.gradle-dist"
GRADLE_HOME="$DIST_DIR/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_HOME/bin/gradle" ]; then
  mkdir -p "$DIST_DIR"
  ZIP="$DIST_DIR/gradle-$GRADLE_VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    else
      echo "curl or wget is required to download Gradle $GRADLE_VERSION" >&2
      exit 1
    fi
  fi
  unzip -q "$ZIP" -d "$DIST_DIR"
fi
exec "$GRADLE_HOME/bin/gradle" "$@"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
