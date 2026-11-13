#!/usr/bin/env bash
# Compiles and runs the :core unit tests without Gradle resolving anything from the network,
# using the Kotlin compiler and JUnit jars bundled inside a local Gradle distribution.
# Only for build machines with no access to Maven Central; everyone else: ./gradlew :core:test
set -euo pipefail
L="${GRADLE_LIB:-/opt/gradle/lib}"
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
K="$(ls "$L"/kotlin-compiler-embeddable-*.jar)"
STD="$(ls "$L"/kotlin-stdlib-[0-9]*.jar)"
CP="$K:$STD:$(ls "$L"/kotlin-script-runtime-*.jar):$(ls "$L"/kotlin-reflect-*.jar):$(ls "$L"/trove4j-*.jar):$(ls "$L"/annotations-*.jar):$(ls "$L"/kotlinx-coroutines-core-jvm-*.jar):$(ls "$L"/kotlin-daemon-embeddable-*.jar)"
LIBS="$STD:$(ls "$L"/junit-4*.jar):$(ls "$L"/hamcrest-core-*.jar)"
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -jvm-target 17 \
  -cp "$LIBS" -d "$OUT" core/src/main/kotlin core/src/test/kotlin
TESTS="$(cd core/src/test/kotlin && grep -rl '@Test' . | sed 's|^\./||; s|\.kt$||; s|/|.|g')"
java -cp "$OUT:$LIBS" org.junit.runner.JUnitCore $TESTS
