# Validation — Android 1.0.7

Static validation covers:
- Android resource XML parsing;
- Java syntax surface checks;
- package and `/metrom/` start URL consistency;
- manifest activity/service/permission wiring;
- resource/string reference integrity;
- production signing fingerprint wiring inherited from 1.0.6;
- offline native metronome source presence;
- launch-cover/back-stack source presence;
- final archive integrity.

The real signed Android build is intentionally performed by GitHub Actions because the private signing key remains only in repository secrets.
