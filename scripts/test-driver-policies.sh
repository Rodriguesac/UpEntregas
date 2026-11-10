#!/usr/bin/env bash
set -euo pipefail
output=$(mktemp -d)
trap 'rm -rf "$output"' EXIT
javac -d "$output" \
 up-driver-next/src/main/java/com/rodriguesacai/updriver/RequestPolicy.java \
 up-driver-next/src/main/java/com/rodriguesacai/updriver/GpsPolicy.java \
 up-driver-next/src/main/java/com/rodriguesacai/updriver/OfferPushPolicy.java \
 scripts/java/DriverPolicyTest.java \
 scripts/java/OfferPushPolicyTest.java
java -cp "$output" DriverPolicyTest
java -cp "$output" OfferPushPolicyTest
