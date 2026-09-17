# ANDROID_HOME + JDK 17 for this OmniBook. Source; do not exec.
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
ANDROID_HOME="${ANDROID_HOME:-${HOME}/.local/opt/android-sdk}"
export JAVA_HOME ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
PATH="${JAVA_HOME}/bin:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${ANDROID_HOME}/emulator:${PATH}"
export PATH
# Backstop if a project still asks for a 4G heap. Project gradle.properties is the real cap.
export GRADLE_OPTS="${GRADLE_OPTS:--Xmx1536m -Dorg.gradle.daemon=false}"
