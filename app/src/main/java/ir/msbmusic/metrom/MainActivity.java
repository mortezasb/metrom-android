package ir.msbmusic.metrom;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
 * Production Android shell for Metrom.
 *
 * The visible product runs as a verified Trusted Web Activity. The Activity:
 *  - keeps the Android splash visible until a trusted TWA can launch,
 *  - validates Digital Asset Links before exposing web content,
 *  - never intentionally falls back to a normal browser/custom-tab toolbar,
 *  - limits Android app-links to https://msbmusic.ir/metrom/,
 *  - provides the postMessage bridge for the native background metronome.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "MetromTWA";
    private static final String HOST = "msbmusic.ir";
    private static final String PATH_PREFIX = "/metrom/";
    private static final Uri ORIGIN = Uri.parse("https://" + HOST);
    private static final Uri DEFAULT_START_URL = Uri.parse(
            "https://" + HOST + "/metrom/login.php?next=studio.php"
    );
    private static final long TRUST_TIMEOUT_MS = 5000L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private CustomTabsClient client;
    private CustomTabsSession session;
    private boolean bound;
    private boolean twaLaunched;
    private boolean handleAllUrlsValidated;
    private boolean originValidated;
    private boolean navigationFinished;
    private boolean messageChannelRequested;
    private boolean keepLaunchSplash = true;
    private boolean leftForTwa;
    private Uri launchUrl = DEFAULT_START_URL;

    private final Runnable trustTimeout = new Runnable() {
        @Override
        public void run() {
            if (!twaLaunched && !handleAllUrlsValidated) {
                showSecureLaunchError();
            }
        }
    };

    private final CustomTabsCallback callback = new CustomTabsCallback() {
        @Override
        public void onRelationshipValidationResult(int relation, @NonNull Uri requestedOrigin,
                                                   boolean result, @Nullable Bundle extras) {
            if (!ORIGIN.equals(requestedOrigin)) return;

            if (relation == CustomTabsService.RELATION_HANDLE_ALL_URLS) {
                mainHandler.removeCallbacks(trustTimeout);
                handleAllUrlsValidated = result;
                if (result) {
                    launchTwa();
                } else {
                    showSecureLaunchError();
                }
                return;
            }

            if (relation == CustomTabsService.RELATION_USE_AS_ORIGIN) {
                originValidated = result;
                maybeOpenMessageChannel();
            }
        }

        @Override
        public void onNavigationEvent(int navigationEvent, @Nullable Bundle extras) {
            if (navigationEvent == NAVIGATION_FINISHED) {
                navigationFinished = true;
                LaunchCoverActivity.dismissActive();
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
            client.warmup(0L); // Browser process only; no page/network prefetch.
            session = client.newSession(callback);
            if (session == null) {
                showSecureLaunchError();
                return;
            }

            boolean trustRequested = session.validateRelationship(
                    CustomTabsService.RELATION_HANDLE_ALL_URLS, ORIGIN, null
            );
            session.validateRelationship(
                    CustomTabsService.RELATION_USE_AS_ORIGIN, ORIGIN, null
            );

            if (!trustRequested) {
                showSecureLaunchError();
                return;
            }

            mainHandler.removeCallbacks(trustTimeout);
            mainHandler.postDelayed(trustTimeout, TRUST_TIMEOUT_MS);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            client = null;
            session = null;
            messageChannelRequested = false;
            handleAllUrlsValidated = false;
            originValidated = false;
            navigationFinished = false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> keepLaunchSplash);
        super.onCreate(savedInstanceState);

        // The post-splash Activity has no web content of its own. Keeping its window
        // dark prevents a white frame while Chrome/TWA is being prepared.
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.metrom_background));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.metrom_background));

        launchUrl = sanitizeMetromUrl(getIntent() == null ? null : getIntent().getData());

        // Metronome must remain useful without a connection. Do not wait for
        // browser/domain verification when Android already knows the network is offline.
        if (!OfflineMetronomeActivity.hasUsableNetwork(this)) {
            keepLaunchSplash = false;
            startActivity(new Intent(this, OfflineMetronomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
            finish();
            overridePendingTransition(0, 0);
            return;
        }

        bindBrowserAndLaunch();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        leftForTwa = false;
        launchUrl = sanitizeMetromUrl(intent == null ? null : intent.getData());
        twaLaunched = false;
        navigationFinished = false;
        messageChannelRequested = false;
        if (!OfflineMetronomeActivity.hasUsableNetwork(this)) {
            keepLaunchSplash = false;
            startActivity(new Intent(this, OfflineMetronomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        if (session != null && handleAllUrlsValidated) launchTwa();
        else if (!bound) bindBrowserAndLaunch();
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
            showSecureLaunchError();
            return;
        }
        bound = CustomTabsClient.bindCustomTabsService(this, browserPackage, tabsConnection);
        if (!bound) showSecureLaunchError();
    }

    private void launchTwa() {
        if (twaLaunched || session == null || !handleAllUrlsValidated) return;
        twaLaunched = true;
        leftForTwa = false;
        mainHandler.removeCallbacks(trustTimeout);
        try {
            // Launch the verified TWA first, then immediately cover its cold-start
            // hand-off with a visually identical native launch surface. This prevents
            // a transient Custom Tab URL/toolbar flash before the first web paint.
            LaunchCoverActivity.prepareForLaunch();
            new TrustedWebActivityIntentBuilder(launchUrl)
                    .build(session)
                    .launchTrustedWebActivity(this);
            startActivity(new Intent(this, LaunchCoverActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
            overridePendingTransition(0, 0);
            keepLaunchSplash = false;
        } catch (RuntimeException ex) {
            Log.w(TAG, "Verified TWA launch failed", ex);
            twaLaunched = false;
            showSecureLaunchError();
        }
    }

    /**
     * Never open the public URL in a visible browser fallback. If the browser cannot
     * verify the production Digital Asset Links relationship, keep the user in a
     * native Metrom surface and allow a retry instead of exposing an address bar.
     */
    private void showSecureLaunchError() {
        mainHandler.removeCallbacks(trustTimeout);
        LaunchCoverActivity.dismissActive();
        keepLaunchSplash = false;
        if (isFinishing() || isDestroyed()) return;

        runOnUiThread(() -> {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER);
            root.setPadding(dp(28), dp(32), dp(28), dp(32));
            root.setBackgroundColor(ContextCompat.getColor(this, R.color.metrom_background));

            ImageView logo = new ImageView(this);
            logo.setImageResource(R.drawable.metrom_site_icon);
            logo.setContentDescription(getString(R.string.app_name));
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(96), dp(96));
            logoParams.bottomMargin = dp(22);
            root.addView(logo, logoParams);

            TextView title = new TextView(this);
            title.setText(R.string.secure_launch_title);
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            title.setGravity(Gravity.CENTER);
            title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            titleParams.bottomMargin = dp(10);
            root.addView(title, titleParams);

            TextView body = new TextView(this);
            body.setText(R.string.secure_launch_body);
            body.setTextColor(0xFFB9C0D0);
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.setGravity(Gravity.CENTER);
            body.setLineSpacing(0f, 1.2f);
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            );
            bodyParams.bottomMargin = dp(22);
            root.addView(body, bodyParams);

            Button retry = new Button(this);
            retry.setText(R.string.secure_launch_retry);
            retry.setAllCaps(false);
            retry.setOnClickListener(v -> retrySecureLaunch());
            root.addView(retry, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ));

            Button offline = new Button(this);
            offline.setText(R.string.secure_launch_offline);
            offline.setAllCaps(false);
            offline.setOnClickListener(v -> {
                startActivity(new Intent(this, OfflineMetronomeActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION));
                finish();
                overridePendingTransition(0, 0);
            });
            LinearLayout.LayoutParams offlineParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            );
            offlineParams.topMargin = dp(10);
            root.addView(offline, offlineParams);

            setContentView(root);
        });
    }

    private void retrySecureLaunch() {
        mainHandler.removeCallbacks(trustTimeout);
        twaLaunched = false;
        handleAllUrlsValidated = false;
        originValidated = false;
        navigationFinished = false;
        messageChannelRequested = false;
        session = null;
        client = null;
        if (bound) {
            try {
                unbindService(tabsConnection);
            } catch (IllegalArgumentException ignored) {
            }
            bound = false;
        }
        bindBrowserAndLaunch();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
    protected void onPause() {
        if (twaLaunched) leftForTwa = true;
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Returning here means the external TWA surface has closed. Finish this
        // invisible host immediately so Back never reveals a black/splash page.
        if (twaLaunched && leftForTwa) {
            LaunchCoverActivity.dismissActive();
            mainHandler.post(() -> {
                if (!isFinishing() && !isDestroyed()) {
                    finishAndRemoveTask();
                    overridePendingTransition(0, 0);
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(trustTimeout);
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
