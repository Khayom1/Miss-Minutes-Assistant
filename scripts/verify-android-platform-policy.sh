#!/usr/bin/env bash
set -euo pipefail

manifest="app/src/main/AndroidManifest.xml"
gradle_file="app/build.gradle.kts"
root_gradle_file="build.gradle.kts"
workflow_file=".github/workflows/android.yml"

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
require_match 'kotlin-gradle-plugin:2[.]4[.]21' "$root_gradle_file"
require_match 'gradle-version: "9[.]5[.]0"' "$workflow_file"
require_match 'platforms;android-37[.]0' "$workflow_file"
require_match 'system-images;android-37[.]0;default;x86_64' "$workflow_file"
if grep -Eq 'id\("org[.]jetbrains[.]kotlin[.]android"\)' "$root_gradle_file" "$gradle_file"; then
  echo "Android platform policy failed: use AGP built-in Kotlin, not the legacy Android Kotlin plugin." >&2
  exit 1
fi
require_match 'android:allowBackup="false"' "$manifest"
require_match 'android:usesCleartextTraffic="false"' "$manifest"
require_match 'android:foregroundServiceType="microphone"' "$manifest"
require_match 'android:foregroundServiceType="specialUse"' "$manifest"
require_match 'android:exported="false"' "$manifest"
require_match 'android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE' "$manifest"

source_files="app/src/main"
speech_input_file="$source_files/java/com/khayom1/missminutes/ai/AndroidSpeechInputProvider.kt"
wake_service_file="$source_files/java/com/khayom1/missminutes/voice/MissMinutesWakeWordService.kt"
provider_file="$source_files/java/com/khayom1/missminutes/ai/AiProvider.kt"
deepseek_file="$source_files/java/com/khayom1/missminutes/ai/DirectDeepSeekProvider.kt"

# Speech must stay on-device; EXTRA_PREFER_OFFLINE alone is not a security boundary.
require_match 'SpeechRecognizer[.]createOnDeviceSpeechRecognizer' "$speech_input_file"
require_match 'SpeechRecognizer[.]createOnDeviceSpeechRecognizer' "$wake_service_file"
if grep -RInE --include='*.kt' 'SpeechRecognizer[.]createSpeechRecognizer[(]' "$source_files"; then
  echo "Android platform policy failed: network-capable speech recognizers are forbidden." >&2
  exit 1
fi

# BYOK credentials must not be forwarded across HTTP redirects or to a proxy.
require_match 'followRedirects[(]false[)]' "$provider_file"
require_match 'followSslRedirects[(]false[)]' "$provider_file"
require_match 'https://api[.]deepseek[.]com/chat/completions' "$deepseek_file"

if grep -RInE \
  --include='*.kt' --include='*.java' --include='*.xml' \
  '(DexClassLoader|PathClassLoader|InMemoryDexClassLoader|URLClassLoader|ClassLoader|ScriptEngine|System[.]load(Library)?[[:space:]]*[(]|Runtime[.]getRuntime[(][)][.]exec|ProcessBuilder|Class[.]forName|java[.]lang[.]reflect|MethodHandles|Method[.]invoke)' \
  "$source_files"; then
  echo "Android platform policy failed: dynamic code loading or reflective execution is forbidden." >&2
  exit 1
fi

if grep -RInE \
  --include='*.kt' --include='*.java' --include='*.xml' \
  '(ACCESS_LOCAL_NETWORK|NsdManager|MulticastLock|NetworkInterface|ServerSocket|DatagramSocket|127[.]0[.]0[.]1|localhost)' \
  "$source_files"; then
  echo "Android platform policy failed: LAN discovery/listening is not part of the product contract." >&2
  exit 1
fi

echo "Android 17 platform policy checks passed."
