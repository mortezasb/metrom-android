# Validation — Android 1.0.5

- Package: `ir.msbmusic.metrom`
- Host: `msbmusic.ir`
- App Link path: exact `/metrom` plus prefix `/metrom/`
- TWA start: `https://msbmusic.ir/metrom/login.php?next=studio.php`
- Release certificate expected in root `/.well-known/assetlinks.json`
- Exact source website icon SHA-256:
  `ba3a36f5fa76a75c11a62e6ddbff9f3f0491e537a978fd54e034b32195ca742b`
- Build signing DSL uses `releaseKeyPassword` to avoid Groovy String.call collision.
