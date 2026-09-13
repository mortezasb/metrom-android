# Validation — Android 1.0.6

Static validation covers XML/JSON/YAML parsing, package/start URL consistency, production signing fingerprint wiring, exact source icon identity, removal of normal browser fallback, and release archive integrity.

A real signed Android build is intentionally performed by the repository's `Build Signed Release` GitHub Action because the private keystore remains only in GitHub Secrets and must never be bundled into this source archive.
