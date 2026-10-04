#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="wangdegong82-debug/Phoenix-AI2"
VERSION="v2.2.0"

cd "$HOME/Phoenix-AI2"

echo "== Phoenix AI 2.2 publish =="
echo "1/5 Commit and push main"

git add .
if ! git diff --cached --quiet; then
  git commit -m "Phoenix AI 2.2 live match lab and Phoenix Chat"
fi
git push origin main

HEAD_SHA="$(git rev-parse HEAD)"

echo
echo "2/5 Wait for android-build workflow"
RUN_ID=""
for _ in $(seq 1 30); do
  ROW="$(gh run list -R "$REPO" --workflow android-build.yml --branch main --limit 5 \
    --json databaseId,headSha,status,conclusion \
    --jq ".[] | select(.headSha==\"$HEAD_SHA\") | .databaseId" | head -n1 || true)"
  if [ -n "$ROW" ]; then
    RUN_ID="$ROW"
    break
  fi
  sleep 3
done

if [ -z "$RUN_ID" ]; then
  echo "Could not find the android-build run for $HEAD_SHA"
  exit 1
fi

gh run watch "$RUN_ID" -R "$REPO" --exit-status

echo
echo "3/5 Android main build passed"

if git ls-remote --tags origin "refs/tags/$VERSION" | grep -q .; then
  echo "Tag $VERSION already exists. Stop to avoid rewriting a release tag."
  exit 1
fi

echo
echo "4/5 Create release tag $VERSION"
git tag -a "$VERSION" -m "Phoenix AI $VERSION"
git push origin "$VERSION"

echo
echo "5/5 Wait for signed release APK"
RELEASE_RUN=""
for _ in $(seq 1 30); do
  ROW="$(gh run list -R "$REPO" --workflow android-release.yml --limit 10 \
    --json databaseId,headBranch,status \
    --jq ".[] | select(.headBranch==\"$VERSION\") | .databaseId" | head -n1 || true)"
  if [ -n "$ROW" ]; then
    RELEASE_RUN="$ROW"
    break
  fi
  sleep 3
done

if [ -z "$RELEASE_RUN" ]; then
  echo "Release workflow was not found."
  exit 1
fi

gh run watch "$RELEASE_RUN" -R "$REPO" --exit-status

echo
echo "Phoenix AI $VERSION published successfully."
gh release view "$VERSION" -R "$REPO" --json name,url,assets \
  --jq '{name: .name, url: .url, apk: (.assets[] | select(.name|endswith(".apk")) | .name)}'
