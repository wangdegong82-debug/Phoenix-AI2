# Phoenix AI Android Release Guide

## What v2.1 changes

Phoenix AI now uses GitHub Actions as the APK build/release pipeline.

- Every push to `main`: creates a temporary debug APK artifact.
- Every version tag such as `v2.1.0`: creates a signed release APK and a GitHub Release.
- The Android home screen can query the latest public GitHub Release and open the APK download link.

## One-time signing setup on Termux

Run:

```bash
cd ~/Phoenix-AI2
chmod +x tools/setup_release_signing.sh tools/release.sh
./tools/setup_release_signing.sh
```

The signing keystore is kept under:

```text
~/.phoenix-ai2/
```

Back it up securely. Do not commit it to GitHub. If the signing key is lost, future APKs will not be able to update over an already installed release.

## Publish v2.1.0

First commit and push the code:

```bash
git add .
git commit -m "Phoenix AI 2.1 automatic APK updates"
git push
```

After the Android build on `main` succeeds:

```bash
./tools/release.sh v2.1.0
```

GitHub Actions will create:

```text
PhoenixAI-v2.1.0.apk
```

under the GitHub Releases page.

## Updating later

For v2.2.0:

1. Change `versionCode` and `versionName` in `android/app/build.gradle.kts`.
2. Commit and push.
3. Run `./tools/release.sh v2.2.0`.
4. Existing v2.1 installations can use the app's "检查更新" button.

## Important migration note

Older locally built Phoenix APKs may have a different signing certificate. The first switch to the new GitHub-signed release may require uninstalling the older APK once. After v2.1.0 is installed, keep using the same release keystore so later versions can install directly over it.
