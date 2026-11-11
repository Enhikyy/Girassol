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
if [ "$status" -ne 0 ] && ! grep -q '::error::bootstrap:' "$LOG"; then
    awk '/^FAILURE:/{show=1} show{print; count++; if(count==8)exit}' "$LOG" |
        head -n 5 |
        while IFS= read -r line; do
            line=$(printf '%s' "$line" | sed 's/%/%25/g')
            printf '::error::gradle: %s\n' "$line"
        done
fi
exit "$status"
