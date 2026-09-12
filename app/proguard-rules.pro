# Manifest components are automatically kept by R8; these rules make the
# native bridge entry points explicit and keep JSON-facing component names stable.
-keep class ir.msbmusic.metrom.MainActivity { *; }
-keep class ir.msbmusic.metrom.PlaybackBridgeActivity { *; }
-keep class ir.msbmusic.metrom.BackgroundMetronomeService { *; }
