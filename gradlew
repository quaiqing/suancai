#!/usr/bin/env sh
set -eu
VERSION="8.9"
BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/wrapper/dists/timetap/gradle-${VERSION}"
DIST="$BASE/gradle-${VERSION}"
if [ ! -x "$DIST/bin/gradle" ]; then
  mkdir -p "$BASE"
  ZIP="$BASE/gradle-${VERSION}-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --connect-timeout 15 "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-${VERSION}-bin.zip"
    else
      echo "需要 curl 或 wget 才能下载 Gradle ${VERSION}" >&2
      exit 1
    fi
  fi
  unzip -q -o "$ZIP" -d "$BASE"
fi
exec "$DIST/bin/gradle" "$@"
