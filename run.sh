#!/usr/bin/env zsh
set -e
cd "$(dirname "$0")"

JAVA_HOME_DIR="/opt/homebrew/Cellar/openjdk/26.0.1/libexec/openjdk.jdk/Contents/Home"
if [ ! -d "$JAVA_HOME_DIR" ]; then
    JAVA_HOME_DIR="/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home"
fi

M2_REPO="$HOME/.m2/repository"
JAVAFX_MP="$M2_REPO/org/openjfx/javafx-controls/21.0.2/javafx-controls-21.0.2-mac-aarch64.jar:$M2_REPO/org/openjfx/javafx-graphics/21.0.2/javafx-graphics-21.0.2-mac-aarch64.jar:$M2_REPO/org/openjfx/javafx-base/21.0.2/javafx-base-21.0.2-mac-aarch64.jar"
CLASSPATH="target/classes:$M2_REPO/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar:$M2_REPO/com/h2database/h2/2.2.224/h2-2.2.224.jar"

mkdir -p target/classes /tmp/openjfx-cache

# Fast incremental compile: only recompile if any .java file is newer than compiled classes
if [ ! -f target/classes/com/supermarket/SupermarketApp.class ] || [ -n "$(find src/main/java -name '*.java' -newer target/classes/com/supermarket/SupermarketApp.class 2>/dev/null)" ]; then
    "$JAVA_HOME_DIR/bin/javac" --release 21 -cp "$JAVAFX_MP:$CLASSPATH" -d target/classes src/main/java/com/supermarket/*.java
fi

exec "$JAVA_HOME_DIR/bin/java" \
    -Djavafx.cachedir=/tmp/openjfx-cache \
    --module-path "$JAVAFX_MP" \
    --add-modules javafx.controls,javafx.graphics,javafx.base \
    --enable-native-access=javafx.graphics \
    -cp "$CLASSPATH" \
    com.supermarket.SupermarketApp "$@"
