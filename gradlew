#!/bin/sh
#
# Placeholder Gradle wrapper script.
#
# This Demo repository ships *without* `gradle/wrapper/gradle-wrapper.jar`
# (binary artifacts shouldn't be committed by an LLM). On first checkout,
# run the following ONCE to materialize the real wrapper:
#
#     gradle wrapper --gradle-version 8.5
#
# That will overwrite this file with the standard Gradle-generated
# `gradlew` script and also generate `gradle/wrapper/gradle-wrapper.jar`.
#
# After that, use `./gradlew` as normal.

set -e
if [ ! -f "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" ]; then
  echo "[gradlew] gradle-wrapper.jar is missing."
  echo "[gradlew] Please run once:    gradle wrapper --gradle-version 8.5"
  exit 1
fi
exec java -classpath "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" \
    org.gradle.wrapper.GradleWrapperMain "$@"
