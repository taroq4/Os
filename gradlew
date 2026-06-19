#!/bin/sh
APP_NAME="Gradle"
APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'
set -- \
        "-classpath" "$CLASSPATH" \
        org.gradle.wrapper.GradleWrapperMain \
        "$@"
exec "$JAVACMD" "$@"
