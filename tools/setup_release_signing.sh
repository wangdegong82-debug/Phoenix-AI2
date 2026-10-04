#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="wangdegong82-debug/Phoenix-AI2"
STATE_DIR="$HOME/.phoenix-ai2"
KEYSTORE="$STATE_DIR/phoenix-release.jks"
PASS_FILE="$STATE_DIR/signing.env"
ALIAS="phoenix-release"

echo "== Phoenix AI release-signing setup =="
echo "This is a one-time operation."
echo

command -v gh >/dev/null || { echo "gh is required"; exit 1; }

if ! command -v keytool >/dev/null; then
  echo "Installing OpenJDK 17..."
  pkg install openjdk-17 -y
fi

if ! command -v openssl >/dev/null; then
  pkg install openssl-tool -y 2>/dev/null || pkg install openssl -y
fi

mkdir -p "$STATE_DIR"
chmod 700 "$STATE_DIR"

if [ ! -f "$KEYSTORE" ]; then
  PASSWORD="$(openssl rand -hex 24)"
  keytool -genkeypair \
    -v \
    -keystore "$KEYSTORE" \
    -storetype JKS \
    -storepass "$PASSWORD" \
    -keypass "$PASSWORD" \
    -alias "$ALIAS" \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Phoenix AI, OU=Phoenix, O=Phoenix AI, L=Kunming, ST=Yunnan, C=CN"

  cat > "$PASS_FILE" <<EOF
PHOENIX_STORE_PASSWORD=$PASSWORD
PHOENIX_KEY_ALIAS=$ALIAS
PHOENIX_KEY_PASSWORD=$PASSWORD
EOF
  chmod 600 "$PASS_FILE"
else
  if [ ! -f "$PASS_FILE" ]; then
    echo "Keystore exists but $PASS_FILE is missing."
    echo "Stop here to avoid losing future update-signing ability."
    exit 1
  fi
  # shellcheck disable=SC1090
  source "$PASS_FILE"
  PASSWORD="$PHOENIX_STORE_PASSWORD"
fi

echo "Uploading encrypted repository secrets to GitHub..."
base64 "$KEYSTORE" | tr -d '\n' | gh secret set PHOENIX_RELEASE_KEYSTORE_B64 -R "$REPO"
printf '%s' "$PASSWORD" | gh secret set PHOENIX_STORE_PASSWORD -R "$REPO"
printf '%s' "$ALIAS" | gh secret set PHOENIX_KEY_ALIAS -R "$REPO"
printf '%s' "$PASSWORD" | gh secret set PHOENIX_KEY_PASSWORD -R "$REPO"

echo
echo "Release signing is configured."
echo "KEEP THIS DIRECTORY SAFE:"
echo "  $STATE_DIR"
echo
echo "If the keystore is lost, future APKs cannot update over installed releases."
