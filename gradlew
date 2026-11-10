#!/bin/sh
set -eu
ORIGINAL_COMMIT=e3f6d168fd030585c6aa4531ee488c7149ca2554
EXPECTED_COMMIT=91ff4c0d1d465226d26892bd511340b46b01d95f
EXPECTED_TREE=44e74001067ce8824fece67b81c33e31a6c8ca7e
EXPECTED_BASE=529df6e6dd677d6833c6b1a660a2bdeb473fa718
EXPECTED_MAIN=52a3f11afce7b009d121163244c3b34860628203
IMPORT_SOURCE=8ea9157a4e256ebbe3b3a9d788689748259f1191
EXPECTED_BUNDLE_SHA256=c1fd28441826faf42ee5485acc3754d4355a857ddf3f6828f78d1c578f366e13
FINAL_BRANCH=feat/girassol-native-rebuild
PARTS="rebuild.bundle.b64.part.00 rebuild.bundle.b64.part.01 rebuild.bundle.b64.part.02 rebuild.bundle.b64.part.03 rebuild.bundle.b64.part.04 rebuild.bundle.b64.part.05 rebuild.bundle.b64.part.06 rebuild.bundle.b64.part.07 rebuild.bundle.b64.part.07b rebuild.bundle.b64.part.08 rebuild.bundle.b64.part.09a rebuild.bundle.b64.part.09b rebuild.bundle.b64.part.10a rebuild.bundle.b64.part.10b"
STAGE=initialization
on_exit() {
    code=$?
    trap - EXIT
    if [ "$code" -ne 0 ]; then
        echo "::error title=Native rebuild import::Stage $STAGE failed with exit code $code"
    fi
    exit "$code"
}
trap on_exit EXIT
STAGE=verify-main-before
MAIN_BEFORE=$(git ls-remote origin refs/heads/main | cut -f1)
test "$MAIN_BEFORE" = "$EXPECTED_MAIN"
echo "Main before import: $MAIN_BEFORE"
STAGE=fetch-transport-commit
git fetch --no-tags --depth=1 origin "$IMPORT_SOURCE"
test "$(git rev-parse FETCH_HEAD)" = "$IMPORT_SOURCE"
STAGE=reconstruct-and-hash-bundle
: > /tmp/rebuild.bundle.b64
for part in $PARTS; do
    git cat-file blob "$IMPORT_SOURCE:$part" >> /tmp/rebuild.bundle.b64
done
base64 -d /tmp/rebuild.bundle.b64 > /tmp/rebuild.bundle
echo "$EXPECTED_BUNDLE_SHA256  /tmp/rebuild.bundle" | sha256sum -c -
STAGE=fetch-bundle-base
git fetch --no-tags --depth=1 origin "$EXPECTED_BASE"
STAGE=verify-git-bundle
git bundle verify /tmp/rebuild.bundle
STAGE=import-and-verify-original-commit
git fetch /tmp/rebuild.bundle feat/girassol-native-rebuild:refs/remotes/rebuild/original
test "$(git rev-parse refs/remotes/rebuild/original)" = "$ORIGINAL_COMMIT"
git merge-base --is-ancestor "$EXPECTED_BASE" "$ORIGINAL_COMMIT"
STAGE=construct-commit-with-unchanged-workflow
export GIT_INDEX_FILE=/tmp/girassol-publish.index
rm -f "$GIT_INDEX_FILE"
git read-tree "$ORIGINAL_COMMIT"
git show "$EXPECTED_BASE:.github/workflows/android.yml" > /tmp/girassol-base-android.yml
WORKFLOW_BLOB=$(git hash-object -w /tmp/girassol-base-android.yml)
git update-index --cacheinfo "100644,$WORKFLOW_BLOB,.github/workflows/android.yml"
ACTUAL_TREE=$(git write-tree)
test "$ACTUAL_TREE" = "$EXPECTED_TREE"
unset GIT_INDEX_FILE
ACTUAL_COMMIT=$(printf '%s\n' 'Rebuild Girassol as an integrated native Android lab' | GIT_AUTHOR_NAME=agent GIT_AUTHOR_EMAIL=agent@example.com GIT_AUTHOR_DATE=2026-11-10T18:50:44+08:00 GIT_COMMITTER_NAME=agent GIT_COMMITTER_EMAIL=agent@example.com GIT_COMMITTER_DATE=2026-11-10T18:50:44+08:00 git commit-tree "$EXPECTED_TREE" -p "$EXPECTED_BASE")
test "$ACTUAL_COMMIT" = "$EXPECTED_COMMIT"
test "$(git show -s --format=%P "$EXPECTED_COMMIT")" = "$EXPECTED_BASE"
test -z "$(git diff --name-only "$EXPECTED_BASE" "$EXPECTED_COMMIT" -- .github/workflows)"
STAGE=publish-final-branch
if ! git push origin "$EXPECTED_COMMIT:refs/heads/$FINAL_BRANCH" >/tmp/girassol-push.log 2>&1; then
    DETAIL=$(grep -E 'refusing to allow|workflow|permission|denied|protected|failed to push|error:' /tmp/girassol-push.log | tr '\r\n' '  ' | cut -c1-900 || true)
    test -n "$DETAIL" || DETAIL='Git rejected the branch push; no matching public error detail was emitted.'
    echo "::error title=Git push detail::$DETAIL"
    exit 1
fi
STAGE=verify-final-branch-and-main
test "$(git ls-remote origin "refs/heads/$FINAL_BRANCH" | cut -f1)" = "$EXPECTED_COMMIT"
test "$(git ls-remote origin refs/heads/main | cut -f1)" = "$EXPECTED_MAIN"
STAGE=checkout-imported-commit
git checkout --detach "$EXPECTED_COMMIT"
STAGE=install-android-35
if command -v sdkmanager >/dev/null 2>&1; then
    yes | sdkmanager --licenses >/dev/null 2>&1 || true
    sdkmanager --install "platforms;android-35" "build-tools;35.0.0" "platform-tools"
fi
STAGE=assemble-imported-commit
exec sh ./gradlew "$@"
