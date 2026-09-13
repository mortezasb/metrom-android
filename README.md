# Metrom Android 1.0.7 — Final App Shell

Production Android source for Metrom.

Key 1.0.7 behavior:
- Online entry: `https://msbmusic.ir/metrom/login.php?next=studio.php`.
- Verified TWA remains the online product surface.
- A seamless native launch cover hides transient browser/TWA cold-start chrome before the first page paint.
- Returning from the TWA immediately finishes the invisible host so no black/splash screen remains in the back stack.
- A native zero-network metronome opens automatically when Android has no validated internet connection.
- Offline mode uses the existing low-overhead foreground media service and makes no server request.
- Launcher and splash assets use the final Metrom reference artwork.

Use `Android CI` for source validation and `Build Signed Release` for the store APK/AAB.
