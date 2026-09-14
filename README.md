# Metrom Android 1.0.13 source revision

Online mode opens the unchanged production Metrom Studio in a verified TWA. Offline mode opens a Persian native emergency metronome with local AudioTrack streaming audio. No network/database/API work is performed by the offline metronome.

This revision removes the secondary native/TWA loading surfaces from normal startup and closes the bridge Activity immediately when the user exits the TWA.

Default store version remains versionCode 1 / versionName 1.0.0.

1.0.13 adds system-bar safe-area handling for the offline UI and installs the supplied Metrom master logo across Android launcher/splash/native branding.
