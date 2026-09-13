package ir.msbmusic.metrom;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.lang.ref.WeakReference;

/**
 * A short-lived launch cover that visually continues the Android splash while
 * the verified TWA performs its first paint. It exists only to prevent a
 * transient Custom Tab toolbar/URL flash on cold starts.
 */
public final class LaunchCoverActivity extends Activity {
    private static final long MAX_COVER_MS = 4500L;
    private static WeakReference<LaunchCoverActivity> active = new WeakReference<>(null);
    private static volatile boolean dismissRequested;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = () -> {
        if (!isFinishing() && !isDestroyed()) {
            finish();
            overridePendingTransition(0, 0);
        }
    };

    public static void prepareForLaunch() {
        dismissRequested = false;
    }

    public static void dismissActive() {
        dismissRequested = true;
        LaunchCoverActivity activity = active.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            activity.finish();
            activity.overridePendingTransition(0, 0);
        });
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        active = new WeakReference<>(this);
        overridePendingTransition(0, 0);
        if (dismissRequested) {
            finish();
            return;
        }

        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.metrom_background));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.metrom_background));

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.metrom_background));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_splash_exact);
        logo.setContentDescription(getString(R.string.app_name));
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);

        int size = dp(108);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.CENTER);
        root.addView(logo, lp);
        setContentView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        handler.postDelayed(timeout, MAX_COVER_MS);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timeout);
        LaunchCoverActivity current = active.get();
        if (current == this) active.clear();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
