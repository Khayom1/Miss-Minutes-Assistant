#!/usr/bin/env bash
set -euo pipefail

manifest="app/src/main/AndroidManifest.xml"
gradle_file="app/build.gradle.kts"

require_match() {
  local pattern="$1"
  local file="$2"
  if ! grep -Eq "$pattern" "$file"; then
    echo "Android platform policy failed: expected /$pattern/ in $file" >&2
    exit 1
  fi
}

require_match 'compileSdk[[:space:]]*=[[:space:]]*37' "$gradle_file"
require_match 'targetSdk[[:space:]]*=[[:space:]]*37' "$gradle_file"
require_match 'minSdk[[:space:]]*=[[:space:]]*26' "$gradle_file"
require_match 'VERSION_17' "$gradle_file"
require_match 'android:allowBackup="false"' "$manifest"
require_match 'android:usesCleartextTraffic="false"' "$manifest"
require_match 'android:foregroundServiceType="microphone"' "$manifest"
require_match 'android:foregroundServiceType="specialUse"' "$manifest"
require_match 'android:exported="false"' "$manifest"
require_match 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' "$manifest"

source_files="app/src/main"
if grep -RInE \
  --include='*.kt' --include='*.java' --include='*.xml' \
  '(DexClassLoader|PathClassLoader|InMemoryDexClassLoader|URLClassLoader|ScriptEngine|System[.]load(Library)?[[:space:]]*[(]|Runtime[.]getRuntime[(][)][.]exec|ProcessBuilder|Class[.]forName)' \
  "$source_files"; then
  echo "Android platform policy failed: dynamic code loading or reflective execution is forbidden." >&2
  exit 1
fi

if grep -RInE \
  --include='*.kt' --include='*.java' --include='*.xml' \
  '(ACCESS_LOCAL_NETWORK|NsdManager|MulticastLock|NetworkInterface|ServerSocket|DatagramSocket)' \
  "$source_files"; then
  echo "Android platform policy failed: LAN discovery/listening is not part of the product contract." >&2
  exit 1
fi

echo "Android 17 platform policy checks passed."
