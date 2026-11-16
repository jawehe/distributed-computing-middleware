#!/bin/bash
# Compile tout le projet dans ./out
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
if command -v javac >/dev/null; then JAVAC=javac; else JAVAC="java -m jdk.compiler/com.sun.tools.javac.Main"; fi
$JAVAC -d out $(find src -name "*.java") && echo "Compilation OK -> ./out"
