package ir.msbmusic.metrom;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * A transparent, user-gesture entry point for Android 12+ foreground-service rules.
 * The web bridge opens metrom://playback/start from the user's Play tap. This Activity
 * starts the mediaPlayback foreground service while this app has a visible Activity,
 * then immediately returns to the TWA.
 */
public final class PlaybackBridgeActivity extends Activity {
    private static final int REQ_NOTIFICATIONS = 7001;
    private static final String PREFS = "metrom_native";
    private static final String KEY_NOTIFICATION_ASKED = "notification_permission_asked";

    private Intent pendingServiceIntent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pendingServiceIntent = buildServiceIntent(getIntent() == null ? null : getIntent().getData());
        if (pendingServiceIntent == null) {
            finishCleanly();
            return;
        }

        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
                && !getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_NOTIFICATION_ASKED, false)) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_NOTIFICATION_ASKED, true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
            return;
        }
        startPlaybackAndFinish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_NOTIFICATIONS) startPlaybackAndFinish();
    }

    @Nullable
    private Intent buildServiceIntent(@Nullable Uri uri) {
        if (uri == null || !"metrom".equalsIgnoreCase(uri.getScheme())
                || !"playback".equalsIgnoreCase(uri.getHost())
                || uri.getPath() == null
                || !uri.getPath().startsWith("/start")) {
            return null;
        }
        Intent intent = new Intent(this, BackgroundMetronomeService.class)
                .setAction(BackgroundMetronomeService.ACTION_PLAY);
        intent.putExtra(BackgroundMetronomeService.EXTRA_BPM,
                clamp(parseInt(uri.getQueryParameter("bpm"), 90), 35, 240));
        intent.putExtra(BackgroundMetronomeService.EXTRA_TIME_SIGNATURE,
                safeSignature(uri.getQueryParameter("sig")));
        intent.putExtra(BackgroundMetronomeService.EXTRA_VOLUME,
                clamp(parseInt(uri.getQueryParameter("vol"), 80), 0, 100));
        intent.putExtra(BackgroundMetronomeService.EXTRA_START_AT_EPOCH_MS,
                parseLong(uri.getQueryParameter("startAt"), System.currentTimeMillis() + 80L));
        return intent;
    }

    private void startPlaybackAndFinish() {
        try {
            ContextCompat.startForegroundService(this, pendingServiceIntent);
        } catch (RuntimeException ignored) {
        }
        finishCleanly();
    }

    private void finishCleanly() {
        finish();
        overridePendingTransition(0, 0);
    }

    private static String safeSignature(@Nullable String value) {
        if (value == null || !value.matches("^[1-9][0-9]?/[1-9][0-9]?$")) return "4/4";
        return value;
    }

    private static int parseInt(@Nullable String value, int fallback) {
        try { return Integer.parseInt(value == null ? "" : value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static long parseLong(@Nullable String value, long fallback) {
        try { return Long.parseLong(value == null ? "" : value); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
