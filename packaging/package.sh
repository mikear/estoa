#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="${1:-$ROOT/src/orion-app/target/estoa.jar}"
OUT="${2:-$ROOT/dist}"

if [[ "$OUT" != /* ]]; then
  OUT="$ROOT/$OUT"
fi
if [[ "$OUT" == "$ROOT" || "$OUT" == "/" ]]; then
  echo "Salida no permitida: $OUT" >&2
  exit 1
fi
if [[ ! -f "$JAR" ]]; then
  echo "No existe el JAR: $JAR" >&2
  exit 1
fi

VERSION_FILE="$ROOT/src/orion-app/target/classes/version.properties"
if [[ ! -f "$VERSION_FILE" ]]; then
  echo "No existe $VERSION_FILE. Compila antes con Maven." >&2
  exit 1
fi
VERSION="$(sed -n 's/^version=//p' "$VERSION_FILE" | head -n 1 | tr -d '[:space:]')"
if [[ -z "$VERSION" ]]; then
  echo "No se pudo leer la version" >&2
  exit 1
fi

OS="$(uname -s)"
case "$OS" in
  Linux)
    FX_GLOB='BOOT-INF/lib/javafx-*-linux.jar'
    NATIVE_GLOB='*.so'
    INSTALLER_TYPE='deb'
    ICON="$ROOT/packaging/estoa.png"
    SUMFILE='SHA256SUMS-linux.txt'
    ;;
  Darwin)
    FX_GLOB='BOOT-INF/lib/javafx-*-mac.jar'
    NATIVE_GLOB='*.dylib'
    INSTALLER_TYPE='dmg'
    ICON="$ROOT/packaging/estoa.icns"
    SUMFILE='SHA256SUMS-macos.txt'
    ;;
  *)
    echo "SO no soportado: $OS" >&2
    exit 1
    ;;
esac

JPACKAGE="${JAVA_HOME:+$JAVA_HOME/bin/jpackage}"
if [[ -z "${JPACKAGE:-}" || ! -x "$JPACKAGE" ]]; then
  JPACKAGE="$(command -v jpackage || true)"
fi
if [[ -z "$JPACKAGE" || ! -x "$JPACKAGE" ]]; then
  echo "jpackage no esta disponible. Define JAVA_HOME con un JDK 21+." >&2
  exit 1
fi
if ! command -v unzip >/dev/null 2>&1; then
  echo "unzip es necesario para extraer los nativos JavaFX." >&2
  exit 1
fi

WORK="$ROOT/build/packaging"
rm -rf "$WORK" "$OUT"
mkdir -p "$WORK/staging" "$WORK/jfx" "$WORK/app-image" "$OUT"
cp "$JAR" "$WORK/staging/estoa.jar"

unzip -o -j -q "$JAR" "$FX_GLOB" -d "$WORK/jfx"
if ! compgen -G "$WORK/jfx/*.jar" >/dev/null; then
  echo "El JAR no contiene JavaFX para $OS. Revisa el perfil Maven." >&2
  exit 1
fi

mkdir -p "$WORK/staging/natives"
for jar in "$WORK"/jfx/*.jar; do
  unzip -o -j -q "$jar" "$NATIVE_GLOB" -d "$WORK/staging/natives" || true
done
COUNT="$(find "$WORK/staging/natives" -type f | wc -l | tr -d ' ')"
if [[ "$COUNT" -eq 0 ]]; then
  echo "No se extrajeron nativos JavaFX." >&2
  exit 1
fi
echo "Nativos JavaFX: $COUNT"

MODULES='java.base,java.desktop,java.logging,java.sql,java.naming,java.management,java.instrument,java.net.http,java.xml,jdk.crypto.ec,jdk.unsupported,jdk.zipfs'
APP_ARGS=(
  --type app-image
  --name Estoa
  --input "$WORK/staging"
  --main-jar estoa.jar
  --main-class org.springframework.boot.loader.launch.JarLauncher
  --java-options '-Djava.library.path=$APPDIR/natives'
  --java-options --enable-native-access=ALL-UNNAMED
  --add-modules "$MODULES"
  --app-version "$VERSION"
  --vendor Estoa
  --dest "$WORK/app-image"
)
if [[ -f "$ICON" ]]; then
  APP_ARGS+=(--icon "$ICON")
fi
"$JPACKAGE" "${APP_ARGS[@]}"

if [[ "$OS" == 'Darwin' ]]; then
  APP_IMAGE="$WORK/app-image/Estoa.app"
else
  APP_IMAGE="$WORK/app-image/Estoa"
fi
if [[ ! -e "$APP_IMAGE" ]]; then
  echo "No se genero la app image: $APP_IMAGE" >&2
  exit 1
fi

if [[ "$OS" == 'Darwin' ]]; then
  ditto -c -k --sequesterRsrc --keepParent "$APP_IMAGE" "$OUT/Estoa-macos-x64-portable.zip"
else
  tar -C "$WORK/app-image" -czf "$OUT/estoa-linux-x64-portable.tar.gz" Estoa
fi

INSTALLER_ARGS=(
  --type "$INSTALLER_TYPE"
  --name Estoa
  --app-image "$APP_IMAGE"
  --dest "$OUT"
  --app-version "$VERSION"
  --vendor Estoa
)
if [[ -f "$ICON" ]]; then
  INSTALLER_ARGS+=(--icon "$ICON")
fi
if [[ "$OS" == 'Linux' ]]; then
  INSTALLER_ARGS+=(--linux-shortcut --linux-package-name estoa)
else
  INSTALLER_ARGS+=(--mac-package-identifier com.estoa.app --mac-package-name Estoa)
fi
"$JPACKAGE" "${INSTALLER_ARGS[@]}"

if [[ "$OS" == 'Linux' ]]; then
  BUILT="$(find "$OUT" -type f -name '*.deb' | sort | head -n 1)"
  if [[ -n "$BUILT" ]]; then
    mv "$BUILT" "$OUT/estoa-linux-x64.deb"
  fi
else
  BUILT="$(find "$OUT" -type f -name '*.dmg' | sort | head -n 1)"
  if [[ -n "$BUILT" ]]; then
    mv "$BUILT" "$OUT/Estoa-macos-x64.dmg"
  fi
fi

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1"
  else
    shasum -a 256 "$1"
  fi
}

shopt -s nullglob
FILES=("$OUT"/*.deb "$OUT"/*.dmg "$OUT"/*.tar.gz "$OUT"/*.zip)
(
  cd "$OUT"
  for f in "${FILES[@]}"; do
    hash_file "$(basename "$f")"
  done
) | sort > "$OUT/$SUMFILE"

echo "Portable e instalador generados en $OUT"
