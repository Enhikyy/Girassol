#!/bin/sh
# Wrapper temporário de diagnóstico para o runner Linux; o wrapper completo será restaurado.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 1
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar
if [ -n "$JAVA_HOME" ]; then
    JAVACMD=$JAVA_HOME/bin/java
else
    JAVACMD=java
fi
if [ ! -x "$JAVACMD" ] && ! command -v "$JAVACMD" >/dev/null 2>&1; then
    echo "::error::gradle: Java 21 não encontrado"
    exit 1
fi
set -- \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain \
    "$@"
LOG=/tmp/girassol-gradle.log
: > "$LOG"
echo "::notice::gradlew: iniciando o Gradle Wrapper"
"$JAVACMD" "$@" > "$LOG" 2>&1
status=$?
cat "$LOG"
if [ "$status" -ne 0 ]; then
    grep -Ei 'fatal:|error:|failed|exception|invalid|not found|denied|could not|cannot|checksum|sha256|patch|curl:' "$LOG" |
        grep -Ev '::error::|What went wrong|FAILURE:|Build file|Where:|Try:|Run with|stacktrace|Daemon' |
        head -n 8 > /tmp/girassol-failure.txt
    if [ ! -s /tmp/girassol-failure.txt ]; then
        grep -Ev '^::|^[[:space:]]*$|^> Task|^Download|Daemon|Incubating' "$LOG" |
            tail -n 8 > /tmp/girassol-failure.txt
    fi
    while IFS= read -r line; do
        line=$(printf '%s' "$line" | tr -d '\r' | sed 's/%/%25/g' | cut -c1-600)
        printf '::error::ci: %s\n' "$line"
    done < /tmp/girassol-failure.txt
fi
exit "$status"
