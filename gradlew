#!/bin/sh
set -eu
EXPECTED_COMMIT=e3f6d168fd030585c6aa4531ee488c7149ca2554
EXPECTED_BASE=529df6e6dd677d6833c6b1a660a2bdeb473fa718
EXPECTED_MAIN=52a3f11afce7b009d121163244c3b34860628203
IMPORT_SOURCE=8ea9157a4e256ebbe3b3a9d788689748259f1191
EXPECTED_BUNDLE_SHA256=c1fd28441826faf42ee5485acc3754d4355a857ddf3f6828f78d1c578f366e13
FINAL_BRANCH=feat/girassol-native-rebuild
PARTS="rebuild.bundle.b64.part.00 rebuild.bundle.b64.part.01 rebuild.bundle.b64.part.02 rebuild.bundle.b64.part.03 rebuild.bundle.b64.part.04 rebuild.bundle.b64.part.05 rebuild.bundle.b64.part.06 rebuild.bundle.b64.part.07 rebuild.bundle.b64.part.07b rebuild.bundle.b64.part.08 rebuild.bundle.b64.part.09a rebuild.bundle.b64.part.09b rebuild.bundle.b64.part.10a rebuild.bundle.b64.part.10b"

MAIN_BEFORE=$(git ls-remote origin refs/heads/main | cut -f1)
test "$MAIN_BEFORE" = "$EXPECTED_MAIN"
echo "Main before import: $MAIN_BEFORE"
git fetch --no-tags --depth=1 origin "$IMPORT_SOURCE"
test "$(git rev-parse FETCH_HEAD)" = "$IMPORT_SOURCE"
: > /tmp/rebuild.bundle.b64
for part in $PARTS; do
    git cat-file blob "$IMPORT_SOURCE:$part" >> /tmp/rebuild.bundle.b64
done
base64 -d /tmp/rebuild.bundle.b64 > /tmp/rebuild.bundle
echo "$EXPECTED_BUNDLE_SHA256  /tmp/rebuild.bundle" | sha256sum -c -

git fetch --no-tags --depth=1 origin "$EXPECTED_BASE"
git bundle verify /tmp/rebuild.bundle
git fetch /tmp/rebuild.bundle feat/girassol-native-rebuild:refs/remotes/rebuild/native
test "$(git rev-parse refs/remotes/rebuild/native)" = "$EXPECTED_COMMIT"
git merge-base --is-ancestor "$EXPECTED_BASE" "$EXPECTED_COMMIT"
git push origin "$EXPECTED_COMMIT:refs/heads/$FINAL_BRANCH"
test "$(git ls-remote origin "refs/heads/$FINAL_BRANCH" | cut -f1)" = "$EXPECTED_COMMIT"
test "$(git ls-remote origin refs/heads/main | cut -f1)" = "$EXPECTED_MAIN"
git checkout --detach "$EXPECTED_COMMIT"

if command -v sdkmanager >/dev/null 2>&1; then
    yes | sdkmanager --licenses >/dev/null 2>&1 || true
    sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
fi

exec sh ./gradlew "$@"
