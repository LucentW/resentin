#!/usr/bin/env bash
# Builds rust/vodozemac-android for every Android ABI into app/src/main/jniLibs
# and regenerates the UniFFI Kotlin binding. Needs: rustup android targets,
# cargo-ndk, and an NDK under $ANDROID_SDK_ROOT/ndk (see README "Building").
set -euo pipefail
cd "$(dirname "$0")/../rust/vodozemac-android"
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' ../../local.properties)}}"
export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-$(ls -d "$SDK"/ndk/* | sort -V | tail -1)}"
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -t x86 \
  -o ../../app/src/main/jniLibs build --release
cargo build   # unstripped host lib: UniFFI reads the interface from its symbols
cargo run -q --bin uniffi-bindgen generate --no-format \
  --library target/debug/libvodozemac_android.so \
  --language kotlin --out-dir ../../app/src/main/java
