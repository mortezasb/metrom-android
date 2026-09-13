package ir.msbmusic.metrom;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Thin Android shell for Metrom.
 *
 * The visible product stays in the TWA. This Activity only:
 *  - binds/warmups the user's Custom Tabs provider,
 *  - verifies msbmusic.ir with Digital Asset Links,
 *  - launches only URLs under /metrom/,
 *  - provides the postMessage bridge used by the native background metronome.
 *
 * It deliberately does not poll the website or prefetch studio.php.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "MetromTWA";
    private static final String HOST = "msbmusic.ir";
    private static final String PATH_PREFIX = "/metrom/";
    private static final Uri ORIGIN = Uri.parse("https://" + HOST);
    private static final Uri DEFAULT_START_URL = Uri.parse("https://" + HOST + "/metrom/login.php?next=studio.php");

    private CustomTabsClient client;
    private CustomTabsSession session;
    private boolean bound;
    private boolean twaLaunched;
    private boolean originValidated;
    private boolean navigationFinished;
    private boolean messageChannelRequested;
    private Uri launchUrl = DEFAULT_START_URL;

    private final CustomTabsCallback callback = new CustomTabsCallback() {
        @Override
        public void onRelationshipValidationResult(int relation, @NonNull Uri requestedOrigin,
                                                   boolean result, @Nullable Bundle extras) {
            if (relation == CustomTabsService.RELATION_USE_AS_ORIGIN && ORIGIN.equals(requestedOrigin)) {
                originValidated = result;
                maybeOpenMessageChannel();
            }
        }

        @Override
        public void onNavigationEvent(int navigationEvent, @Nullable Bundle extras) {
            if (navigationEvent == NAVIGATION_FINISHED) {
                navigationFinished = true;
                maybeOpenMessageChannel();
            }
        }

        @Override
        public void onMessageChannelReady(@Nullable Bundle extras) {
            sendHelloToWeb();
        }

        @Override
        public void onPostMessage(@NonNull String message, @Nullable Bundle extras) {
            handleWebMessage(message);
        }
    };

    private final CustomTabsServiceConnection tabsConnection = new CustomTabsServiceConnection() {
        @Override
        public void onCustomTabsServiceConnected(@NonNull ComponentName name,
                                                 @NonNull CustomTabsClient connectedClient) {
            client = connectedClient;
            client.warmup(0L); // Browser process warmup only; no network prefetch.
            session = client.newSession(callback);
            if (session == null) {
                openBrowserFallback(launchUrl);
                return;
            }
            session.validateRelationship(CustomTabsService.RELATION_USE_AS_ORIGIN, ORIGIN, null);
            launchTwa();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            client = null;
            session = null;
            messageChannelRequested = false;
            originValidated = false;
            navigationFinished = false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // AndroidX SplashScreen gives Android 6+ one consistent, platform-native
        // launch surface and avoids a duplicate custom Splash Activity on Android 12+.
        SplashScreen.installSplashScreen(this);
        super.onCreate(savedInstanceState);
        launchUrl = sanitizeMetromUrl(getIntent() == null ? null : getIntent().getData());
        bindBrowserAndLaunch();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Uri next = sanitizeMetromUrl(intent == null ? null : intent.getData());
        if (!next.equals(launchUrl)) {
            launchUrl = next;
            twaLaunched = false;
            navigationFinished = false;
            messageChannelRequested = false;
            if (session != null) launchTwa();
        }
    }

    private Uri sanitizeMetromUrl(@Nullable Uri candidate) {
        if (candidate == null) return DEFAULT_START_URL;
        String candidatePath = candidate.getPath();
        boolean safe = "https".equalsIgnoreCase(candidate.getScheme())
                && HOST.equalsIgnoreCase(candidate.getHost())
                && candidatePath != null
                && ("/metrom".equals(candidatePath) || candidatePath.startsWith(PATH_PREFIX));
        return safe ? candidate : DEFAULT_START_URL;
    }

    private void bindBrowserAndLaunch() {
        String browserPackage = CustomTabsClient.getPackageName(this, null);
        if (browserPackage == null) {
            openBrowserFallback(launchUrl);
            return;
        }
        bound = CustomTabsClient.bindCustomTabsService(this, browserPackage, tabsConnection);
        if (!bound) openBrowserFallback(launchUrl);
    }

    private void launchTwa() {
        if (twaLaunched || session == null) return;
        twaLaunched = true;
        try {
            new TrustedWebActivityIntentBuilder(launchUrl)
                    .build(session)
                    .launchTrustedWebActivity(this);
        } catch (RuntimeException ex) {
            Log.w(TAG, "TWA launch failed; opening browser fallback", ex);
            openBrowserFallback(launchUrl);
        }
    }

    private void openBrowserFallback(Uri url) {
        try {
            Intent browser = new Intent(Intent.ACTION_VIEW, url);
            startActivity(browser);
        } catch (RuntimeException ex) {
            Log.e(TAG, "No browser available", ex);
        }
    }

    private void maybeOpenMessageChannel() {
        if (session == null || !originValidated || !navigationFinished || messageChannelRequested) return;
        messageChannelRequested = session.requestPostMessageChannel(ORIGIN, ORIGIN, new Bundle());
        Log.d(TAG, "postMessage channel requested=" + messageChannelRequested);
    }

    private void sendHelloToWeb() {
        if (session == null) return;
        try {
            JSONObject activation = new JSONObject()
                    .put("scheme", "metrom")
                    .put("host", "playback")
                    .put("package", getPackageName());
            JSONObject hello = new JSONObject()
                    .put("namespace", "metrom-native")
                    .put("bridge", 1)
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("capabilities", new org.json.JSONArray()
                            .put("background-metronome")
                            .put("foreground-activation-intent"))
                    .put("activation", activation);
            session.postMessage(hello.toString(), null);
        } catch (JSONException ignored) {
        }
    }

    private void handleWebMessage(String raw) {
        try {
            JSONObject root = new JSONObject(raw);
            if (!"metrom".equals(root.optString("namespace")) || root.optInt("bridge", 0) != 1) return;
            String type = root.optString("type", "");
            JSONObject payload = root.optJSONObject("payload");
            if (payload == null) payload = new JSONObject();

            if ("playback".equals(type)) {
                handlePlayback(payload);
            } else if ("update".equals(type)) {
                handleUpdate(payload);
            }
        } catch (JSONException ex) {
            Log.w(TAG, "Ignoring malformed web message", ex);
        }
    }

    private void handlePlayback(JSONObject payload) {
        String action = payload.optString("action", "");
        boolean metronome = payload.optBoolean("metronome", true);

        if ("stop".equals(action) || "pause".equals(action) || !metronome) {
            BackgroundMetronomeService.sendControl(this, BackgroundMetronomeService.ACTION_STOP, null);
            return;
        }
        if (!"play".equals(action)) return;

        Intent update = serviceIntent(BackgroundMetronomeService.ACTION_UPDATE, payload);
        BackgroundMetronomeService.sendControl(this, BackgroundMetronomeService.ACTION_UPDATE, update.getExtras());

        // Android 12+ may reject a foreground-service start from this host Activity
        // because the visible TWA belongs to the browser package. The 5.5.3 web bridge
        // therefore opens PlaybackBridgeActivity from the actual Play tap. On Android
        // 11 and older, direct start is also safe as a backwards-compatible fallback.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            try {
                ContextCompat.startForegroundService(this,
                        serviceIntent(BackgroundMetronomeService.ACTION_PLAY, payload));
            } catch (RuntimeException ex) {
                Log.w(TAG, "Legacy direct FGS start failed", ex);
            }
        }
    }

    private void handleUpdate(JSONObject payload) {
        boolean metronome = payload.optBoolean("metronome", true);
        if (!metronome) {
            BackgroundMetronomeService.sendControl(this, BackgroundMetronomeService.ACTION_STOP, null);
            return;
        }
        Intent update = serviceIntent(BackgroundMetronomeService.ACTION_UPDATE, payload);
        BackgroundMetronomeService.sendControl(this, BackgroundMetronomeService.ACTION_UPDATE, update.getExtras());
    }

    private Intent serviceIntent(String action, JSONObject payload) {
        Intent intent = new Intent(this, BackgroundMetronomeService.class).setAction(action);
        intent.putExtra(BackgroundMetronomeService.EXTRA_BPM,
                clamp(payload.optInt("bpm", 90), 35, 240));
        intent.putExtra(BackgroundMetronomeService.EXTRA_TIME_SIGNATURE,
                payload.optString("timeSignature", "4/4"));
        intent.putExtra(BackgroundMetronomeService.EXTRA_VOLUME,
                clamp(payload.optInt("masterVolume", 80), 0, 100));
        intent.putExtra(BackgroundMetronomeService.EXTRA_START_AT_EPOCH_MS,
                payload.optLong("startAtEpochMs", 0L));
        return intent;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            try {
                unbindService(tabsConnection);
            } catch (IllegalArgumentException ignored) {
            }
            bound = false;
        }
        super.onDestroy();
    }
}
