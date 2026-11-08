#!/bin/bash
# Momo Subtitle — server toolchain bootstrap (recreated after 2026-11-05 wipe).
# Idempotent: safe to re-run; skips components already present.
set -euo pipefail

ROOT=/home/z/my-project
SDK=$ROOT/sdk
JDK=$ROOT/jdk17
mkdir -p "$SDK" "$ROOT/.gradle-home"

# ---- 1. Temurin JDK 17 (AGP 8.7.3 / jvmTarget 17) ----
if [ ! -x "$JDK/bin/java" ]; then
  echo "[setup] installing Temurin JDK 17..."
  curl -sSL -o /tmp/jdk17.tgz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf "$JDK"
  mkdir -p "$JDK"
  tar -xzf /tmp/jdk17.tgz -C "$JDK" --strip-components=1
  rm /tmp/jdk17.tgz
fi
export JAVA_HOME=$JDK
export PATH=$JDK/bin:$PATH
echo "[setup] java: $($JDK/bin/java -version 2>&1 | head -1)"

# ---- 2. Android cmdline-tools ----
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  echo "[setup] installing cmdline-tools..."
  curl -sSL -o /tmp/clt.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
  rm -rf "$SDK/cmdline-tools"
  mkdir -p "$SDK/cmdline-tools"
  cd "$SDK/cmdline-tools"
  python3 -c "import zipfile; zipfile.ZipFile('/tmp/clt.zip').extractall('.')"
  mv cmdline-tools latest
  chmod -R +x latest/bin
  rm /tmp/clt.zip
fi
SDKM=$SDK/cmdline-tools/latest/bin/sdkmanager

# ---- 3. Licenses + packages ----
yes | $SDKM --licenses >/dev/null 2>&1 || true
$SDKM --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" --sdk_root=$SDK >/dev/null
echo "[setup] base SDK done"

# NDK + CMake (large; separate steps so failures are visible)
if [ ! -d "$SDK/ndk/27.2.12479018" ]; then
  echo "[setup] installing NDK 27.2.12479018 (large download)..."
  $SDKM --install "ndk;27.2.12479018" --sdk_root=$SDK
fi
if [ ! -d "$SDK/cmake/3.22.1" ]; then
  echo "[setup] installing CMake 3.22.1..."
  $SDKM --install "cmake;3.22.1" --sdk_root=$SDK
fi

echo "[setup] done"
