#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

VERSION="${1:-}"

if [[ ! "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "Usage: ./tools/release.sh v2.1.0"
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "Working tree is not clean. Commit/push changes first."
  exit 1
fi

git pull --ff-only origin main
git tag -a "$VERSION" -m "Phoenix AI $VERSION"
git push origin "$VERSION"

echo
echo "Release tag pushed: $VERSION"
echo "GitHub Actions will build and publish the signed APK automatically."
