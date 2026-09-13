package ir.msbmusic.metrom;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
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
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.customtabs.TrustedWebUtils;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;
import androidx.browser.trusted.splashscreens.SplashScreenParamKey;
import androidx.browser.trusted.splashscreens.SplashScreenVersion;
import androidx.core.content.ContextCompat;
import androidx.core.splashscreen.SplashScreen;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Production Android shell for Metrom.
 *
 * The visible product runs as a verified Trusted Web Activity. The Activity:
 *  - shows one branded Android splash, then a no-logo loading surface,
 *  - validates Digital Asset Links before exposing online web content,
 *  - allows offline-first TWA launch only after a prior successful trust check,
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
    private static final long TRUST_TIMEOUT_MS = 8000L;
    private static final String STARTUP_PREFS = "metrom_startup_v1";
    private static final String PREF_TRUST_ESTABLISHED = "trusted_twa_established";
    private static final String PREF_TRUSTED_BROWSER_PACKAGE = "trusted_browser_package";

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
    private boolean offlineTrustedLaunch;
    private boolean trustEstablishedBefore;
    private String trustedBrowserPackage;
    private boolean twaLoadingSplashReady;
    private String browserPackage;
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
                    trustEstablishedBefore = true;
                    trustedBrowserPackage = browserPackage;
                    getSharedPreferences(STARTUP_PREFS, MODE_PRIVATE)
                            .edit()
                            .putBoolean(PREF_TRUST_ESTABLISHED, true)
                            .putString(PREF_TRUSTED_BROWSER_PACKAGE, browserPackage)
                            .apply();
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
            browserPackage = name.getPackageName();
            client = connectedClient;
            client.warmup(0L); // Browser process only; no page/network prefetch.
            session = client.newSession(callback);
            if (session == null) {
                showSecureLaunchError();
                return;
            }

            prepareTwaLoadingSplash(() -> beginTrustFlow());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            client = null;
            session = null;
            browserPackage = null;
            messageChannelRequested = false;
            handleAllUrlsValidated = false;
            originValidated = false;
            navigationFinished = false;
            offlineTrustedLaunch = false;
            twaLoadingSplashReady = false;
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        splashScreen.setKeepOnScreenCondition(() -> keepLaunchSplash);
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.metrom_background));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.metrom_background));

        SharedPreferences startupPrefs = getSharedPreferences(STARTUP_PREFS, MODE_PRIVATE);
        trustEstablishedBefore = startupPrefs.getBoolean(PREF_TRUST_ESTABLISHED, false);
        trustedBrowserPackage = startupPrefs.getString(PREF_TRUSTED_BROWSER_PACKAGE, null);
        launchUrl = sanitizeMetromUrl(getIntent() == null ? null : getIntent().getData());

        // First surface: Android 12+ branded splash. Second surface: a real animated
        // loader (no duplicate logo) while the verified browser/TWA is prepared.
        showNativeLoadingSurface();
        mainHandler.post(() -> keepLaunchSplash = false);
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
            if (session != null && (handleAllUrlsValidated || offlineTrustedLaunch)) launchTwa();
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
        browserPackage = CustomTabsClient.getPackageName(this, null);
        if (browserPackage == null) {
            showSecureLaunchError();
            return;
        }
        bound = CustomTabsClient.bindCustomTabsService(this, browserPackage, tabsConnection);
        if (!bound) showSecureLaunchError();
    }

    private void beginTrustFlow() {
        if (session == null) {
            showSecureLaunchError();
            return;
        }

        // Offline-first path is allowed only after this exact installation has
        // successfully validated Digital Asset Links at least once. This mirrors
        // Chrome's recommended TWA offline-first lifecycle and avoids any first-run
        // browser/error surface that could expose the public URL.
        if (!hasValidatedInternet()) {
            // Never hand an offline URL to a browser provider that was not the one
            // previously verified online for this installation. If the provider has
            // changed, remain in our native surface so a Custom Tab/address bar can
            // never become the fallback UI.
            if (!trustEstablishedBefore || trustedBrowserPackage == null
                    || !trustedBrowserPackage.equals(browserPackage)) {
                showFirstRunOfflineError();
                return;
            }
            offlineTrustedLaunch = true;
            handleAllUrlsValidated = false;
            session.validateRelationship(
                    CustomTabsService.RELATION_USE_AS_ORIGIN, ORIGIN, null
            );
            launchTwa();
            return;
        }

        offlineTrustedLaunch = false;
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

    private boolean hasValidatedInternet() {
        try {
            ConnectivityManager manager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return false;
            Network active = manager.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities capabilities = manager.getNetworkCapabilities(active);
            return capabilities != null
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void showNativeLoadingSurface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(32), dp(28), dp(32));
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.metrom_background));

        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.metrom_pink)
        ));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        spinnerParams.bottomMargin = dp(18);
        root.addView(spinner, spinnerParams);

        TextView label = new TextView(this);
        label.setText(R.string.loading_metrom);
        label.setTextColor(0xFFD9DEEA);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setGravity(Gravity.CENTER);
        root.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        setContentView(root);
    }

    private void prepareTwaLoadingSplash(@NonNull Runnable continuation) {
        final CustomTabsSession splashSession = session;
        final String splashBrowserPackage = browserPackage;
        if (splashSession == null || splashBrowserPackage == null
                || !TrustedWebUtils.areSplashScreensSupported(
                this, splashBrowserPackage, SplashScreenVersion.V1)) {
            twaLoadingSplashReady = false;
            continuation.run();
            return;
        }

        new Thread(() -> {
            boolean ready = false;
            try {
                File dir = new File(getFilesDir(), "twa_splash");
                if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("splash dir");
                File image = new File(dir, "metrom-loading-v1.png");
                if (!image.isFile() || image.length() == 0L) copyLoadingSplash(image);
                ready = TrustedWebUtils.transferSplashImage(
                        this, image, getPackageName() + ".fileprovider",
                        splashBrowserPackage, splashSession
                );
            } catch (Exception ex) {
                Log.w(TAG, "TWA loading splash preparation failed", ex);
            }
            final boolean prepared = ready;
            mainHandler.post(() -> {
                twaLoadingSplashReady = prepared;
                continuation.run();
            });
        }, "MetromTwaSplash").start();
    }

    private void copyLoadingSplash(@NonNull File destination) throws Exception {
        try (InputStream input = getResources().openRawResource(R.raw.twa_loading_ring);
             FileOutputStream output = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.flush();
        }
    }

    private Bundle buildTwaSplashParams() {
        Bundle params = new Bundle();
        params.putString(SplashScreenParamKey.KEY_VERSION, SplashScreenVersion.V1);
        params.putInt(SplashScreenParamKey.KEY_BACKGROUND_COLOR,
                ContextCompat.getColor(this, R.color.metrom_background));
        params.putInt(SplashScreenParamKey.KEY_FADE_OUT_DURATION_MS, 160);
        params.putInt(SplashScreenParamKey.KEY_SCALE_TYPE, ImageView.ScaleType.CENTER.ordinal());
        return params;
    }

    private void launchTwa() {
        if (twaLaunched || session == null || (!handleAllUrlsValidated && !offlineTrustedLaunch)) return;
        twaLaunched = true;
        mainHandler.removeCallbacks(trustTimeout);
        keepLaunchSplash = false;
        try {
            TrustedWebActivityIntentBuilder builder = new TrustedWebActivityIntentBuilder(launchUrl);
            if (twaLoadingSplashReady) builder.setSplashScreenParams(buildTwaSplashParams());
            builder.build(session).launchTrustedWebActivity(this);
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
        showLaunchError(R.string.secure_launch_title, R.string.secure_launch_body);
    }

    private void showFirstRunOfflineError() {
        showLaunchError(R.string.offline_first_run_title, R.string.offline_first_run_body);
    }

    private void showLaunchError(int titleRes, int bodyRes) {
        mainHandler.removeCallbacks(trustTimeout);
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
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(88), dp(88));
            logoParams.bottomMargin = dp(20);
            root.addView(logo, logoParams);

            TextView title = new TextView(this);
            title.setText(titleRes);
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
            body.setText(bodyRes);
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

            setContentView(root);
        });
    }

    private void retrySecureLaunch() {
        mainHandler.removeCallbacks(trustTimeout);
        showNativeLoadingSurface();
        twaLaunched = false;
        handleAllUrlsValidated = false;
        originValidated = false;
        navigationFinished = false;
        messageChannelRequested = false;
        offlineTrustedLaunch = false;
        twaLoadingSplashReady = false;
        SharedPreferences startupPrefs = getSharedPreferences(STARTUP_PREFS, MODE_PRIVATE);
        trustEstablishedBefore = startupPrefs.getBoolean(PREF_TRUST_ESTABLISHED, false);
        trustedBrowserPackage = startupPrefs.getString(PREF_TRUSTED_BROWSER_PACKAGE, null);
        session = null;
        client = null;
        browserPackage = null;
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
