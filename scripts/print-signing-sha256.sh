#!/usr/bin/env bash
set -euo pipefail
KS="${1:-metrom-upload.jks}"
ALIAS="${2:-metrom}"
keytool -list -v -keystore "$KS" -alias "$ALIAS" | sed -n '/SHA256:/p'
