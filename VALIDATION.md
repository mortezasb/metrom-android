# Validation — Android source revision 1.0.7

Static validation covers XML/JSON/YAML parsing, first-release versionCode/versionName defaults, package/start URL consistency, production signing wiring, exact source icon identity, no browser fallback, offline-first trust gating, local loading surfaces, FileProvider scope and release archive integrity.

The public store release is still `versionCode=1` / `versionName=1.0.0`. A real signed Android build is intentionally performed by the repository's `Build Signed Release` GitHub Action because the private keystore remains only in GitHub Secrets and must never be bundled into this source archive.

- Offline TWA launch is allowed only after prior trust and only with the same verified browser provider, preventing an unverified offline browser fallback.
