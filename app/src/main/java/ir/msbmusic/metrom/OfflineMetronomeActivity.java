package ir.msbmusic.metrom;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Emergency native metronome shown only when a validated internet connection is unavailable.
 *
 * The audio is generated locally by BackgroundMetronomeService. This Activity performs no
 * HTTP request, API call, database access, polling or background sync. Online Studio remains
 * the normal product; this screen is only a resilient offline fallback for keeping tempo.
 */
public final class OfflineMetronomeActivity extends Activity {
    public static final String EXTRA_RETURN_URL = "return_url";
    private static final String PREFS = "metrom_offline_metronome";
    private static final int MIN_BPM = 35;
    private static final int MAX_BPM = 240;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Deque<Long> tapTimes = new ArrayDeque<>();
    private final List<TextView> signatureChips = new ArrayList<>();
    private final List<View> beatDots = new ArrayList<>();

    private int bpm = 90;
    private int volume = 80;
    private String signature = "4/4";
    private boolean playing;
    private int visualBeat;
    private String returnUrl;

    private TextView bpmValue;
    private TextView playButton;
    private TextView connectionBadge;
    private TextView studioButton;
    private LinearLayout beatRow;

    private final Runnable beatTicker = new Runnable() {
        @Override
        public void run() {
            if (!playing) return;
            int beats = beatsPerBar();
            if (beats <= 0) beats = 4;
            setActiveBeat(visualBeat % beats);
            visualBeat = (visualBeat + 1) % beats;
            uiHandler.postDelayed(this, Math.max(120L, 60_000L / Math.max(MIN_BPM, bpm)));
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(9, 11, 17));
        getWindow().setNavigationBarColor(Color.rgb(9, 11, 17));

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        bpm = clamp(prefs.getInt("bpm", bpm), MIN_BPM, MAX_BPM);
        volume = clamp(prefs.getInt("volume", volume), 0, 100);
        String savedSignature = prefs.getString("signature", signature);
        if (savedSignature != null && savedSignature.matches("^(2/4|3/4|4/4|6/8)$")) {
            signature = savedSignature;
        }
        playing = BackgroundMetronomeService.isRunning();
        returnUrl = getIntent() == null ? null : getIntent().getStringExtra(EXTRA_RETURN_URL);
        buildUi();
        updateConnectionState();
        updatePlayState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        playing = BackgroundMetronomeService.isRunning();
        updateConnectionState();
        updatePlayState();
    }

    @Override
    protected void onDestroy() {
        uiHandler.removeCallbacks(beatTicker);
        super.onDestroy();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(9, 11, 17));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        connectionBadge = label("", 12, 0xFFFFFFFF, true);
        connectionBadge.setGravity(Gravity.CENTER);
        connectionBadge.setPadding(dp(14), dp(8), dp(14), dp(8));
        LinearLayout.LayoutParams badgeParams = wrap();
        badgeParams.gravity = Gravity.CENTER_HORIZONTAL;
        badgeParams.bottomMargin = dp(18);
        root.addView(connectionBadge, badgeParams);

        TextView title = label(getString(R.string.offline_emergency_title), 25, 0xFFFFFFFF, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = matchWrap();
        titleParams.bottomMargin = dp(8);
        root.addView(title, titleParams);

        TextView subtitle = label(getString(R.string.offline_emergency_subtitle), 14, 0xFFB6BDCB, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setLineSpacing(0f, 1.22f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.bottomMargin = dp(22);
        root.addView(subtitle, subtitleParams);

        LinearLayout tempoCard = card();
        tempoCard.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.bottomMargin = dp(14);
        root.addView(tempoCard, cardParams);

        TextView small = label(getString(R.string.offline_tempo), 12, 0xFF8F98AA, true);
        small.setGravity(Gravity.CENTER);
        tempoCard.addView(small, matchWrap());

        bpmValue = label(String.valueOf(bpm), 58, 0xFFFFFFFF, true);
        bpmValue.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bpmParams = matchWrap();
        bpmParams.topMargin = dp(2);
        tempoCard.addView(bpmValue, bpmParams);

        TextView bpmUnit = label("BPM", 12, 0xFFFF5D86, true);
        bpmUnit.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams unitParams = matchWrap();
        unitParams.bottomMargin = dp(16);
        tempoCard.addView(bpmUnit, unitParams);

        LinearLayout adjustRow = new LinearLayout(this);
        adjustRow.setOrientation(LinearLayout.HORIZONTAL);
        adjustRow.setGravity(Gravity.CENTER);
        adjustRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams adjustParams = matchWrap();
        adjustParams.bottomMargin = dp(14);
        tempoCard.addView(adjustRow, adjustParams);

        TextView minus = actionButton("−", false);
        minus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        minus.setOnClickListener(v -> changeBpm(-1));
        adjustRow.addView(minus, weightedButton(1));

        TextView tap = actionButton(getString(R.string.offline_tap_tempo), false);
        tap.setOnClickListener(v -> registerTap());
        LinearLayout.LayoutParams tapParams = weightedButton(2);
        tapParams.leftMargin = dp(8);
        tapParams.rightMargin = dp(8);
        adjustRow.addView(tap, tapParams);

        TextView plus = actionButton("+", false);
        plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        plus.setOnClickListener(v -> changeBpm(1));
        adjustRow.addView(plus, weightedButton(1));

        beatRow = new LinearLayout(this);
        beatRow.setOrientation(LinearLayout.HORIZONTAL);
        beatRow.setGravity(Gravity.CENTER);
        beatRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout.LayoutParams beatParams = matchWrap();
        beatParams.bottomMargin = dp(4);
        tempoCard.addView(beatRow, beatParams);
        rebuildBeatDots();

        LinearLayout meterCard = card();
        LinearLayout.LayoutParams meterCardParams = matchWrap();
        meterCardParams.bottomMargin = dp(14);
        root.addView(meterCard, meterCardParams);

        TextView meterTitle = label(getString(R.string.offline_time_signature), 13, 0xFFD8DDEA, true);
        LinearLayout.LayoutParams meterTitleParams = matchWrap();
        meterTitleParams.bottomMargin = dp(12);
        meterCard.addView(meterTitle, meterTitleParams);

        LinearLayout signatureRow = new LinearLayout(this);
        signatureRow.setOrientation(LinearLayout.HORIZONTAL);
        signatureRow.setGravity(Gravity.CENTER);
        signatureRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        meterCard.addView(signatureRow, matchWrap());
        addSignatureChip(signatureRow, "2/4");
        addSignatureChip(signatureRow, "3/4");
        addSignatureChip(signatureRow, "4/4");
        addSignatureChip(signatureRow, "6/8");
        refreshSignatureChips();

        TextView volumeTitle = label(getString(R.string.offline_volume), 13, 0xFFD8DDEA, true);
        LinearLayout.LayoutParams volumeTitleParams = matchWrap();
        volumeTitleParams.topMargin = dp(18);
        volumeTitleParams.bottomMargin = dp(6);
        meterCard.addView(volumeTitle, volumeTitleParams);

        SeekBar volumeSeek = new SeekBar(this);
        volumeSeek.setMax(100);
        volumeSeek.setProgress(volume);
        volumeSeek.setProgressTintList(ColorStateList.valueOf(0xFFFF3D6E));
        volumeSeek.setThumbTintList(ColorStateList.valueOf(0xFFFF6B91));
        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                volume = Math.max(0, progress);
                if (fromUser) saveSettings();
                if (playing && fromUser) sendUpdate();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        meterCard.addView(volumeSeek, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        playButton = actionButton("", true);
        playButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        playButton.setOnClickListener(v -> togglePlayback());
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        playParams.bottomMargin = dp(14);
        root.addView(playButton, playParams);

        LinearLayout infoCard = new LinearLayout(this);
        infoCard.setOrientation(LinearLayout.VERTICAL);
        infoCard.setPadding(dp(18), dp(17), dp(18), dp(17));
        infoCard.setBackground(rounded(0xFF111620, 0xFF252C3A, 18, 1));
        LinearLayout.LayoutParams infoParams = matchWrap();
        infoParams.bottomMargin = dp(14);
        root.addView(infoCard, infoParams);

        TextView infoTitle = label(getString(R.string.offline_studio_title), 14, 0xFFFFFFFF, true);
        LinearLayout.LayoutParams infoTitleParams = matchWrap();
        infoTitleParams.bottomMargin = dp(7);
        infoCard.addView(infoTitle, infoTitleParams);

        TextView infoBody = label(getString(R.string.offline_studio_body), 13, 0xFFAAB2C2, false);
        infoBody.setLineSpacing(0f, 1.28f);
        infoCard.addView(infoBody, matchWrap());

        studioButton = actionButton(getString(R.string.offline_open_studio), false);
        studioButton.setOnClickListener(v -> openStudioIfOnline());
        root.addView(studioButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        TextView footnote = label(getString(R.string.offline_local_note), 11, 0xFF707A8C, false);
        footnote.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footParams = matchWrap();
        footParams.topMargin = dp(14);
        root.addView(footnote, footParams);

        setContentView(scroll);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(0xFF10141D, 0xFF202735, 22, 1));
        return card;
    }

    private void addSignatureChip(LinearLayout parent, String value) {
        TextView chip = actionButton(value, false);
        chip.setTag(value);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        chip.setOnClickListener(v -> {
            signature = String.valueOf(v.getTag());
            saveSettings();
            refreshSignatureChips();
            rebuildBeatDots();
            if (playing) sendUpdate();
        });
        LinearLayout.LayoutParams lp = weightedButton(1);
        if (parent.getChildCount() > 0) lp.leftMargin = dp(7);
        parent.addView(chip, lp);
        signatureChips.add(chip);
    }

    private void refreshSignatureChips() {
        for (TextView chip : signatureChips) {
            boolean selected = signature.equals(String.valueOf(chip.getTag()));
            chip.setTextColor(selected ? Color.WHITE : 0xFFB9C1D0);
            chip.setBackground(rounded(
                    selected ? 0xFF49304A : 0xFF171C27,
                    selected ? 0xFFFF4F7C : 0xFF2A3242,
                    13,
                    selected ? 2 : 1));
        }
    }

    private void rebuildBeatDots() {
        if (beatRow == null) return;
        beatRow.removeAllViews();
        beatDots.clear();
        int beats = beatsPerBar();
        for (int i = 0; i < beats; i++) {
            View dot = new View(this);
            int size = i == 0 ? 11 : 9;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(size), dp(size));
            if (i > 0) lp.leftMargin = dp(9);
            dot.setBackground(rounded(i == 0 ? 0xFFFF3D6E : 0xFF313847, 0, size, 0));
            beatRow.addView(dot, lp);
            beatDots.add(dot);
        }
        visualBeat = 0;
    }

    private void setActiveBeat(int active) {
        for (int i = 0; i < beatDots.size(); i++) {
            int fill;
            if (i == active) fill = i == 0 ? 0xFFFF3D6E : 0xFFFFFFFF;
            else fill = i == 0 ? 0xFF743247 : 0xFF313847;
            beatDots.get(i).setBackground(rounded(fill, 0, 12, 0));
        }
    }

    private void changeBpm(int delta) {
        bpm = clamp(bpm + delta, MIN_BPM, MAX_BPM);
        bpmValue.setText(String.valueOf(bpm));
        saveSettings();
        if (playing) {
            sendUpdate();
            restartVisualTicker();
        }
    }

    private void registerTap() {
        long now = SystemClock.elapsedRealtime();
        Long last = tapTimes.peekLast();
        if (last != null && now - last > 2000L) tapTimes.clear();
        tapTimes.addLast(now);
        while (tapTimes.size() > 5) tapTimes.removeFirst();
        if (tapTimes.size() < 2) return;

        long previous = -1L;
        long total = 0L;
        int intervals = 0;
        for (Long stamp : tapTimes) {
            if (previous > 0L) {
                long diff = stamp - previous;
                if (diff >= 250L && diff <= 1800L) {
                    total += diff;
                    intervals++;
                }
            }
            previous = stamp;
        }
        if (intervals == 0) return;
        bpm = clamp((int) Math.round(60_000d / (total / (double) intervals)), MIN_BPM, MAX_BPM);
        bpmValue.setText(String.valueOf(bpm));
        saveSettings();
        if (playing) {
            sendUpdate();
            restartVisualTicker();
        }
    }

    private void togglePlayback() {
        if (playing) {
            BackgroundMetronomeService.sendControl(
                    this, BackgroundMetronomeService.ACTION_STOP, null);
            playing = false;
            updatePlayState();
            return;
        }

        Intent play = new Intent(this, BackgroundMetronomeService.class)
                .setAction(BackgroundMetronomeService.ACTION_PLAY)
                .putExtra(BackgroundMetronomeService.EXTRA_BPM, bpm)
                .putExtra(BackgroundMetronomeService.EXTRA_TIME_SIGNATURE, signature)
                .putExtra(BackgroundMetronomeService.EXTRA_VOLUME, volume)
                .putExtra(BackgroundMetronomeService.EXTRA_START_AT_EPOCH_MS, System.currentTimeMillis() + 80L);
        ContextCompat.startForegroundService(this, play);
        playing = true;
        updatePlayState();
    }

    private void sendUpdate() {
        Bundle extras = new Bundle();
        extras.putInt(BackgroundMetronomeService.EXTRA_BPM, bpm);
        extras.putString(BackgroundMetronomeService.EXTRA_TIME_SIGNATURE, signature);
        extras.putInt(BackgroundMetronomeService.EXTRA_VOLUME, volume);
        BackgroundMetronomeService.sendControl(
                this, BackgroundMetronomeService.ACTION_UPDATE, extras);
    }

    private void updatePlayState() {
        if (playButton == null) return;
        if (playing) {
            playButton.setText(R.string.offline_stop_metronome);
            playButton.setTextColor(Color.WHITE);
            playButton.setBackground(rounded(0xFF242A36, 0xFFFF4B79, 18, 2));
            restartVisualTicker();
        } else {
            playButton.setText(R.string.offline_start_metronome);
            playButton.setTextColor(Color.WHITE);
            playButton.setBackground(rounded(0xFFFF3D6E, 0, 18, 0));
            uiHandler.removeCallbacks(beatTicker);
            visualBeat = 0;
            setActiveBeat(-1);
        }
    }

    private void restartVisualTicker() {
        uiHandler.removeCallbacks(beatTicker);
        visualBeat = 0;
        uiHandler.post(beatTicker);
    }

    private void updateConnectionState() {
        if (connectionBadge == null || studioButton == null) return;
        boolean online = hasValidatedInternet();
        connectionBadge.setText(online
                ? R.string.offline_connection_restored
                : R.string.offline_connection_badge);
        connectionBadge.setBackground(rounded(
                online ? 0xFF123225 : 0xFF2A2025,
                online ? 0xFF2EA86B : 0xFF6E3948,
                30,
                1));
        studioButton.setText(online
                ? R.string.offline_open_studio
                : R.string.offline_check_connection);
    }

    private void openStudioIfOnline() {
        updateConnectionState();
        if (!hasValidatedInternet()) {
            Toast.makeText(this, R.string.offline_still_offline, Toast.LENGTH_SHORT).show();
            return;
        }
        if (playing) {
            BackgroundMetronomeService.sendControl(
                    this, BackgroundMetronomeService.ACTION_STOP, null);
            playing = false;
        }
        Intent online = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (returnUrl != null && returnUrl.startsWith("https://msbmusic.ir/metrom")) {
            online.setData(Uri.parse(returnUrl));
        }
        startActivity(online);
        finish();
    }

    private boolean hasValidatedInternet() {
        try {
            ConnectivityManager manager =
                    (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
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

    private void saveSettings() {
        getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putInt("bpm", bpm)
                .putInt("volume", volume)
                .putString("signature", signature)
                .apply();
    }

    private int beatsPerBar() {
        int slash = signature.indexOf('/');
        try {
            return clamp(Integer.parseInt(slash > 0 ? signature.substring(0, slash) : signature), 1, 8);
        } catch (NumberFormatException ignored) {
            return 4;
        }
    }

    private TextView label(String text, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setGravity(Gravity.START);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private TextView actionButton(String text, boolean primary) {
        TextView button = label(text, 14, Color.WHITE, true);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(rounded(
                primary ? 0xFFFF3D6E : 0xFF171C27,
                primary ? 0 : 0xFF2B3342,
                16,
                primary ? 0 : 1));
        return button;
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp, int strokeDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) shape.setStroke(dp(strokeDp), stroke);
        return shape;
    }

    private LinearLayout.LayoutParams weightedButton(int weight) {
        return new LinearLayout.LayoutParams(0, dp(48), weight);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
