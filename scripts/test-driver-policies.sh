#!/usr/bin/env bash
set -euo pipefail
output=$(mktemp -d)
trap 'rm -rf "$output"' EXIT
javac -d "$output" \
 up-driver-next/src/main/java/com/rodriguesacai/updriver/RequestPolicy.java \
 up-driver-next/src/main/java/com/rodriguesacai/updriver/GpsPolicy.java \
 scripts/java/DriverPolicyTest.java
java -cp "$output" DriverPolicyTest
