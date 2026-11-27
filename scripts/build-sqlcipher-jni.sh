#!/usr/bin/env bash
# Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later
# Build the native library for the host JDK. Package it with the desktop app
# and set org.sqlite.lib.path to its directory before loading Xerial.
set -euo pipefail
output=${1:?Usage: build-sqlcipher-jni.sh OUTPUT_DIRECTORY}
mkdir -p "$output"
output=$(cd "$output" && pwd)
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
cd "$work"

fetch() {
    curl --fail --location --silent --show-error "$1" -o "$2"
    echo "$3  $2" | shasum -a 256 -c -
}
fetch https://codeload.github.com/sqlcipher/sqlcipher/tar.gz/refs/tags/v4.20.0 cipher.tgz 38705f51b61ac0636a5fd064f9a77fafabc7227b2aa2513f1b14c8bb1102affc
fetch https://codeload.github.com/xerial/sqlite-jdbc/tar.gz/refs/tags/3.53.4.0 jdbc.tgz 9dded7305b4f3bab596d6a40eaab0cf0d3afad60006eb80073492103b06b081c
fetch https://repo.maven.apache.org/maven2/org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar jdbc.jar bcb1f51e36f940867e83342f9efbf5968ac44a6bef4d397bb4af7b17b45cd2fb
tar xzf cipher.tgz
tar xzf jdbc.tgz
cipher=sqlcipher-4.20.0
jni=sqlite-jdbc-3.53.4.0/src/main/java/org/sqlite/core
(cd "$cipher" && ./configure --with-tempstore=yes && make sqlite3.c)
mkdir headers
"$JAVA_HOME/bin/javac" -cp jdbc.jar -d headers -h headers "$jni/NativeDB.java"
mv headers/org_sqlite_core_NativeDB.h headers/NativeDB.h

case "$(uname -s)" in
    Darwin)
        platform=darwin
        library=libsqlitejdbc.dylib
        openssl=$(brew --prefix openssl@3)
        flags=(-dynamiclib "-I$openssl/include")
        crypto=("$openssl/lib/libcrypto.a" -framework Security -framework CoreFoundation)
        ;;
    Linux)
        platform=linux
        library=libsqlitejdbc.so
        flags=(-shared -fPIC)
        crypto=(-lcrypto -ldl -lpthread -lm)
        ;;
    *) echo "Unsupported host" >&2; exit 1 ;;
esac

cc -O2 "${flags[@]}" -DSQLITE_HAS_CODEC -DSQLITE_EXTRA_INIT=sqlcipher_extra_init \
    -DSQLITE_EXTRA_SHUTDOWN=sqlcipher_extra_shutdown -DSQLITE_TEMP_STORE=2 -DSQLITE_THREADSAFE=1 \
    -DSQLITE_ENABLE_COLUMN_METADATA -DSQLITE_ENABLE_FTS3 -DSQLITE_ENABLE_FTS5 \
    -Iheaders "-I$cipher" "-I$JAVA_HOME/include" "-I$JAVA_HOME/include/$platform" \
    "$cipher/sqlite3.c" "$jni/NativeDB.c" "${crypto[@]}" -o "$library"
cp "$library" "$output/"
mkdir -p "$output/licenses"
cp "$cipher/LICENSE.md" "$output/licenses/SQLCipher.md"
cp sqlite-jdbc-3.53.4.0/LICENSE "$output/licenses/Xerial.txt"
cp sqlite-jdbc-3.53.4.0/LICENSE.zentus "$output/licenses/Zentus.txt"
if [[ "$platform" == darwin ]]; then
    cp "$openssl/LICENSE.txt" "$output/licenses/OpenSSL.txt"
fi
