package ir.msbmusic.metrom;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fully native, zero-network metronome fallback.
 * It uses the same low-overhead BackgroundMetronomeService as the online studio.
 */
public final class OfflineMetronomeActivity extends ComponentActivity {
    private static final int MIN_BPM = 35;
    private static final int MAX_BPM = 240;
    private static final long EXIT_WINDOW_MS = 1900L;
    private static final String PREFS = "metrom_offline_metronome";

    private final List<Long> tapTimes = new ArrayList<>();
    private int bpm = 90;
    private String signature = "4/4";
    private boolean playing;
    private long lastBackAt;

    private TextView bpmValue;
    private Button playButton;
    private Button meterButton;
    private SeekBar bpmSeek;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.metrom_background));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.metrom_background));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        bpm = clamp(prefs.getInt("bpm", 90));
        signature = safeSignature(prefs.getString("signature", "4/4"));

        setContentView(buildUi());

        // AndroidX bridges classic Back and Android 13+ / Android 16 predictive Back
        // through one lifecycle-aware callback, so the same double-back-to-exit
        // behavior works consistently from API 23 through API 36+.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBack();
            }
        });
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(24), dp(22), dp(28));
        root.setBackgroundColor(ContextCompat.getColor(this, R.color.metrom_background));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.metrom_site_icon);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(logo, lp(dp(78), dp(78), Gravity.CENTER_HORIZONTAL, 0, 0, 0, dp(12)));

        TextView title = text(getString(R.string.app_name), 22, Color.WHITE, true);
        root.addView(title, lpMatch(dpWrap(), 0, 0, 0, dp(2)));

        TextView subtitle = text(getString(R.string.offline_mode_title), 13, 0xFFB7BECE, false);
        root.addView(subtitle, lpMatch(dpWrap(), 0, 0, 0, dp(22)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(18), dp(20), dp(18), dp(20));
        card.setBackgroundResource(R.drawable.offline_card_bg);
        root.addView(card, lpMatch(dpWrap(), 0, 0, 0, dp(18)));

        TextView label = text("BPM", 12, 0xFF8F97AA, true);
        card.addView(label, lpMatch(dpWrap(), 0, 0, 0, dp(4)));

        bpmValue = text(String.valueOf(bpm), 52, Color.WHITE, true);
        card.addView(bpmValue, lpMatch(dpWrap(), 0, 0, 0, dp(14)));

        LinearLayout bpmRow = new LinearLayout(this);
        bpmRow.setOrientation(LinearLayout.HORIZONTAL);
        bpmRow.setGravity(Gravity.CENTER);
        Button minus = actionButton("−");
        Button plus = actionButton("+");
        bpmSeek = new SeekBar(this);
        bpmSeek.setMax(MAX_BPM - MIN_BPM);
        bpmSeek.setProgress(bpm - MIN_BPM);
        bpmSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) setBpm(MIN_BPM + progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        minus.setOnClickListener(v -> setBpm(bpm - 1));
        plus.setOnClickListener(v -> setBpm(bpm + 1));
        bpmRow.addView(minus, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout.LayoutParams seekLp = new LinearLayout.LayoutParams(0, dp(54), 1f);
        seekLp.setMargins(dp(10), 0, dp(10), 0);
        bpmRow.addView(bpmSeek, seekLp);
        bpmRow.addView(plus, new LinearLayout.LayoutParams(dp(54), dp(54)));
        card.addView(bpmRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        actionRow.setPadding(0, dp(16), 0, 0);

        Button tap = wideButton(getString(R.string.offline_tap));
        tap.setOnClickListener(v -> tapTempo());
        meterButton = wideButton(signature);
        meterButton.setOnClickListener(v -> cycleMeter());
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, dp(52), 1f);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, dp(52), 1f);
        half2.setMargins(dp(10), 0, 0, 0);
        actionRow.addView(tap, half);
        actionRow.addView(meterButton, half2);
        card.addView(actionRow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        playButton = wideButton(getString(R.string.offline_play));
        playButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        playButton.setOnClickListener(v -> togglePlayback());
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        playLp.topMargin = dp(12);
        card.addView(playButton, playLp);

        TextView note = text(getString(R.string.offline_mode_body), 13, 0xFFADB5C6, false);
        note.setGravity(Gravity.CENTER);
        note.setLineSpacing(0f, 1.18f);
        root.addView(note, lpMatch(dpWrap(), 0, 0, 0, dp(18)));

        Button retry = wideButton(getString(R.string.offline_retry_online));
        retry.setOnClickListener(v -> retryOnline());
        root.addView(retry, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ContextCompat.getColor(this, R.color.metrom_background));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private void togglePlayback() {
        if (playing) {
            BackgroundMetronomeService.sendControl(this, BackgroundMetronomeService.ACTION_STOP, null);
            playing = false;
        } else {
            Intent intent = new Intent(this, BackgroundMetronomeService.class)
                    .setAction(BackgroundMetronomeService.ACTION_PLAY)
                    .putExtra(BackgroundMetronomeService.EXTRA_BPM, bpm)
                    .putExtra(BackgroundMetronomeService.EXTRA_TIME_SIGNATURE, signature)
                    .putExtra(BackgroundMetronomeService.EXTRA_VOLUME, 82)
                    .putExtra(BackgroundMetronomeService.EXTRA_START_AT_EPOCH_MS, System.currentTimeMillis() + 70L);
            ContextCompat.startForegroundService(this, intent);
            playing = true;
        }
        updatePlayButton();
    }

    private void updatePlayButton() {
        if (playButton == null) return;
        playButton.setText(playing ? R.string.offline_stop : R.string.offline_play);
        playButton.setBackgroundResource(playing ? R.drawable.offline_button_stop_bg : R.drawable.offline_button_play_bg);
    }

    private void setBpm(int value) {
        bpm = clamp(value);
        if (bpmValue != null) bpmValue.setText(String.valueOf(bpm));
        if (bpmSeek != null && bpmSeek.getProgress() != bpm - MIN_BPM) bpmSeek.setProgress(bpm - MIN_BPM);
        persist();
        if (playing) sendUpdate();
    }

    private void cycleMeter() {
        String[] meters = {"4/4", "3/4", "2/4", "6/8"};
        int index = 0;
        for (int i = 0; i < meters.length; i++) if (meters[i].equals(signature)) index = i;
        signature = meters[(index + 1) % meters.length];
        if (meterButton != null) meterButton.setText(signature);
        persist();
        if (playing) sendUpdate();
    }

    private void tapTempo() {
        long now = SystemClock.elapsedRealtime();
        if (!tapTimes.isEmpty() && now - tapTimes.get(tapTimes.size() - 1) > 2200L) tapTimes.clear();
        tapTimes.add(now);
        if (tapTimes.size() > 8) tapTimes.remove(0);
        if (tapTimes.size() < 2) return;

        List<Long> intervals = new ArrayList<>();
        for (int i = 1; i < tapTimes.size(); i++) intervals.add(tapTimes.get(i) - tapTimes.get(i - 1));
        Collections.sort(intervals);
        long median = intervals.get(intervals.size() / 2);
        if (median > 0) setBpm((int) Math.round(60_000.0 / median));
    }

    private void sendUpdate() {
        Bundle extras = new Bundle();
        extras.putInt(BackgroundMetronomeService.EXTRA_BPM, bpm);
        extras.putString(BackgroundMetronomeService.EXTRA_TIME_SIGNATURE, signature);
        extras.putInt(BackgroundMetronomeService.EXTRA_VOLUME, 82);
        extras.putLong(BackgroundMetronomeService.EXTRA_START_AT_EPOCH_MS, 0L);
        BackgroundMetronomeService.sendControl(this, BackgroundMetronomeService.ACTION_UPDATE, extras);
    }

    private void persist() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt("bpm", bpm).putString("signature", signature).apply();
    }

    private void retryOnline() {
        if (!hasUsableNetwork(this)) {
            Toast.makeText(this, R.string.offline_still_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
        overridePendingTransition(0, 0);
    }

    public static boolean hasUsableNetwork(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= 23) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        }
        return false;
    }

    private void handleBack() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastBackAt <= EXIT_WINDOW_MS) {
            finishAndRemoveTask();
            overridePendingTransition(0, 0);
            return;
        }
        lastBackAt = now;
        Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();
    }

    private void persistAndStopUi() {
        persist();
    }

    @Override
    protected void onPause() {
        persistAndStopUi();
        super.onPause();
    }

    private int clamp(int value) { return Math.max(MIN_BPM, Math.min(MAX_BPM, value)); }
    private String safeSignature(@Nullable String value) {
        if ("2/4".equals(value) || "3/4".equals(value) || "6/8".equals(value)) return value;
        return "4/4";
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setGravity(Gravity.CENTER);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private Button actionButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.offline_button_bg);
        return button;
    }

    private Button wideButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.offline_button_bg);
        return button;
    }

    private LinearLayout.LayoutParams lp(int width, int height, int gravity, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(width, height);
        p.gravity = gravity;
        p.setMargins(l, t, r, b);
        return p;
    }

    private LinearLayout.LayoutParams lpMatch(int height, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        p.setMargins(l, t, r, b);
        return p;
    }

    private int dpWrap() { return ViewGroup.LayoutParams.WRAP_CONTENT; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }


}
