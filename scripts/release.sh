#!/bin/sh
# Builds both release APKs and signs them with your release key.
#
# The fdroid APK is built unsigned, exactly as F-Droid builds it, and signed afterwards with
# apksigner (v2/v3 only, so the APK's contents stay byte-identical). F-Droid rebuilds it from the
# tagged source and publishes your signed copy only if the two match (reproducible builds).
#
# Key: ~/keys/listen-here-release.properties (storeFile, storePassword, keyAlias), or set
# LISTENHERE_SIGNING to another properties file. Back the key up: without it you can't ship updates.
set -eu
cd "$(dirname "$0")/.."

# The APK records the commit it was built from (META-INF/version-control-info.textproto), so it
# only matches F-Droid's build when made from the exact release tag with nothing uncommitted.
git diff --quiet && git diff --cached --quiet || { echo "Uncommitted changes: commit first." >&2; exit 1; }
TAG=$(git describe --exact-match --tags HEAD 2>/dev/null) || { echo "HEAD isn't tagged: tag the release (vX.Y.Z) first." >&2; exit 1; }

PROPS=${LISTENHERE_SIGNING:-$HOME/keys/listen-here-release.properties}
prop() { sed -n "s/^$1=//p" "$PROPS"; }
STORE=$(prop storeFile)
ALIAS=$(prop keyAlias)
export LISTENHERE_KS_PASS="$(prop storePassword)"

SDK=${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties)}
APKSIGNER=$(ls -d "$SDK"/build-tools/*/apksigner | sort -V | tail -1)
VERSION=$(sed -n 's/.*versionName "\(.*\)"/\1/p' app/build.gradle)
[ "$TAG" = "v$VERSION" ] || { echo "Tag $TAG doesn't match versionName $VERSION." >&2; exit 1; }

./gradlew --no-daemon clean assembleFdroidRelease assembleFullRelease

mkdir -p dist
OUT=dist/listen-here-$VERSION-fdroid.apk
"$APKSIGNER" sign --ks "$STORE" --ks-key-alias "$ALIAS" --ks-pass env:LISTENHERE_KS_PASS \
    --v1-signing-enabled false --v4-signing-enabled false --alignment-preserved --out "$OUT" app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk
cp app/build/outputs/apk/full/release/app-full-release.apk "dist/listen-here-$VERSION.apk"

for apk in dist/listen-here-$VERSION-fdroid.apk dist/listen-here-$VERSION.apk; do
    "$APKSIGNER" verify --print-certs "$apk" | grep -m1 "SHA-256" | sed "s|^|$apk: |"
done
