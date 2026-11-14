#!/usr/bin/env bash
set -euo pipefail
TERMUX_ARCH="\${1:?Termux architecture is required}"
ANDROID_ABI="\${2:?Android ABI is required}"
TERMUX_PACKAGES_REF="\${TERMUX_PACKAGES_REF:?Pinned Termux packages commit is required}"
case "$TERMUX_ARCH:$ANDROID_ABI" in
  aarch64:arm64-v8a|arm:armeabi-v7a) ;;
  *) echo "Unsupported architecture pair: $TERMUX_ARCH:$ANDROID_ABI" >&2; exit 2 ;;
esac
WORK_DIR="\${RUNNER_TEMP:-/tmp}/proot-runtime-\${TERMUX_ARCH}"
TERMUX_DIR="$WORK_DIR/termux-packages"
EXTRACT_DIR="$WORK_DIR/extracted"
OUTPUT_DIR="$GITHUB_WORKSPACE/app/src/main/jniLibs/$ANDROID_ABI"
rm -rf "$WORK_DIR" "$OUTPUT_DIR"
mkdir -p "$WORK_DIR" "$EXTRACT_DIR" "$OUTPUT_DIR"
git clone --filter=blob:none https://github.com/termux/termux-packages.git "$TERMUX_DIR"
git -C "$TERMUX_DIR" checkout --detach "$TERMUX_PACKAGES_REF"
(
  cd "$TERMUX_DIR"
  ./scripts/run-docker.sh -d ./build-package.sh -I -C -a "$TERMUX_ARCH" \
    proot libtalloc libandroid-shmem
)
find_package_deb() {
  local wanted="$1"
  local found=""
  while IFS= read -r deb; do
    [[ "$(dpkg-deb -f "$deb" Package")" == "$wanted" ]] || continue
    [[ "$(dpkg-deb -f "$deb" Architecture")" == "$TERMUX_ARCH" ]] || continue
    found="$deb"
    break
  done < <(find "$TERMUX_DIR/output" -type f -name '*.deb' -print)
  [[ -n "$found" ]] || {
    echo "Missing $wanted package for $TERMUX_ARCH in Termux build output" >&2
    exit 1
  }
  printf '%s\n' "$found"
}
for package in proot libtalloc libandroid-shmem; do
  deb="$(find_package_deb "$package")"
  dpkg-deb -x "$deb" "$EXTRACT_DIR"
done
find_one() {
  local pattern="$1"
  local result
  result="$(find "$EXTRACT_DIR" -type f -path "$pattern" -print -quit)"
  [[ -n "$result" ]] || {
    echo "Expected runtime file not found: $pattern" >&2
    exit 1
  }
  printf '%s\n' "$result"
}
PROOT_BIN="$(find_one '*/bin/proot')"
PROOT_LOADER="$(find_one '*/libexec/proot/loader')"
TALLOC_LIB="$(find_one '*/libtalloc.so.*')"
SHMEM_LIB="$(find_one '*/libandroid-shmem.so')"
install -m 755 "$PROOT_BIN" "$OUTPUT_DIR/libproot.so"
install -m 755 "$PROOT_LOADER" "$OUTPUT_DIR/libproot_loader.so"
install -m 644 "$SHMEM_LIB" "$OUTPUT_DIR/libandroid-shmem.so"
install -m 644 "$TALLOC_LIB" "$OUTPUT_DIR/libtalloc.so"
patchelf --set-soname libtalloc.so "$OUTPUT_DIR/libtalloc.so"
while IFS= read -r needed; do
  case "$needed" in
    libtalloc.so|libtalloc.so.*)
      patchelf --replace-needed "$needed" libtalloc.so "$OUTPUT_DIR/libproot.so"
      ;;
  esac
done < <(readelf -d "$OUTPUT_DIR/libproot.so" | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p')
chmod 755 "$OUTPUT_DIR/libproot.so" "$OUTPUT_DIR/libproot_loader.so"
chmod 644 "$OUTPUT_DIR/libtalloc.so" "$OUTPUT_DIR/libandroid-shmem.so"
MACHINE="$(readelf -h "$OUTPUT_DIR/libproot.so" | sed -n 's/.*Machine: *//p')"
case "$TERMUX_ARCH:$MACHINE" in
  aarch64:*AArch64*) ;;
  arm:*ARM*) ;;
  *) echo "Unexpected PRoot ELF machine for $ANDROID_ABI: $MACHINE" >&2; exit 1 ;;
esac
NEEDED="$(readelf -d "$OUTPUT_DIR/libproot.so" | sed -n 's/.*Shared library: \[\(.*\)\].*/\1/p')"
grep -qx 'libtalloc.so' <<<"$NEEDED" || {
  echo "PRoot does not resolve against the packaged libtalloc.so" >&2
  echo "$NEEDED" >&2
  exit 1
}
grep -qx 'libandroid-shmem.so' <<<"$NEEDED" || {
  echo "PRoot does not resolve against the packaged libandroid-shmem.so" >&2
  echo "$NEEDED" >&2
  exit 1
}
test -s "$OUTPUT_DIR/libproot.so"
test -s "$OUTPUT_DIR/libproot_loader.so"
test -s "$OUTPUT_DIR/libtalloc.so"
test -s "$OUTPUT_DIR/libandroid-shmem.so"
echo "Built and validated Termux PRoot runtime for $ANDROID_ABI:"
ls -l "$OUTPUT_DIR"
readelf -d "$OUTPUT_DIR/libproot.so" | grep NEEDED
