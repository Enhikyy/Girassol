#!/bin/sh
set -eu
EXPECTED_COMMIT=e3f6d168fd030585c6aa4531ee488c7149ca2554
EXPECTED_BASE=529df6e6dd677d6833c6b1a660a2bdeb473fa718
FINAL_BRANCH=feat/girassol-native-rebuild

if [ ! -f rebuild.bundle.b64 ]; then
    echo "This temporary wrapper requires the verified rebuild bundle." >&2
    exit 1
fi

MAIN_BEFORE=$(git ls-remote origin refs/heads/main | cut -f1)
echo "Main before import: $MAIN_BEFORE"
git fetch --no-tags --depth=1 origin "$EXPECTED_BASE"
base64 -d rebuild.bundle.b64 > /tmp/rebuild.bundle
git bundle verify /tmp/rebuild.bundle
git fetch /tmp/rebuild.bundle feat/girassol-native-rebuild:refs/remotes/rebuild/native
test "$(git rev-parse refs/remotes/rebuild/native)" = "$EXPECTED_COMMIT"
git merge-base --is-ancestor "$EXPECTED_BASE" "$EXPECTED_COMMIT"
git push origin "$EXPECTED_COMMIT:refs/heads/$FINAL_BRANCH"
test "$(git ls-remote origin "refs/heads/$FINAL_BRANCH" | cut -f1)" = "$EXPECTED_COMMIT"
test "$(git ls-remote origin refs/heads/main | cut -f1)" = "$MAIN_BEFORE"
git checkout --detach "$EXPECTED_COMMIT"

if command -v sdkmanager >/dev/null 2>&1; then
    yes | sdkmanager --licenses >/dev/null 2>&1 || true
    sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
fi

exec sh ./gradlew "$@"
