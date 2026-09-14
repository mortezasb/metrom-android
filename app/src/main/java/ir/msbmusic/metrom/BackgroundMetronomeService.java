package ir.msbmusic.metrom;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

/**
 * Low-overhead local metronome used by both the verified web Studio bridge and
 * the emergency offline screen.
 *
 * Audio is streamed directly to AudioTrack from a single foreground-service
 * thread. Blocking AudioTrack writes pace the beat, so there is no polling,
 * alarm, WorkManager job, network request or database access.
 */
public final class BackgroundMetronomeService extends Service {
    public static final String ACTION_PLAY = "ir.msbmusic.metrom.action.PLAY";
    public static final String ACTION_UPDATE = "ir.msbmusic.metrom.action.UPDATE";
    public static final String ACTION_STOP = "ir.msbmusic.metrom.action.STOP";
    private static final String ACTION_CONTROL = "ir.msbmusic.metrom.action.CONTROL";

    public static final String EXTRA_BPM = "bpm";
    public static final String EXTRA_TIME_SIGNATURE = "time_signature";
    public static final String EXTRA_VOLUME = "volume";
    public static final String EXTRA_START_AT_EPOCH_MS = "start_at_epoch_ms";
    private static final String EXTRA_CONTROL_ACTION = "control_action";

    private static final String CHANNEL_ID = "metrom_metronome_playback";
    private static final int NOTIFICATION_ID = 2401;
    private static final int SAMPLE_RATE = 48_000;
    private static final int CLICK_MS = 62;

    private final Object audioLock = new Object();
    private volatile AudioTrack audioTrack;
    private volatile boolean running;
    private static volatile boolean processRunning;
    private volatile boolean focusPaused;
    private Thread audioThread;

    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private AudioManager.OnAudioFocusChangeListener legacyFocusListener;

    private volatile int bpm = 90;
    private volatile int beatsPerBar = 4;
    private volatile String timeSignature = "4/4";
    private volatile int volumePercent = 80;
    private volatile long requestedStartAtEpochMs;

    private final BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            String control = intent.getStringExtra(EXTRA_CONTROL_ACTION);
            if (ACTION_STOP.equals(control)) {
                stopPlaybackAndSelf();
                return;
            }
            if ((ACTION_UPDATE.equals(control) || ACTION_PLAY.equals(control)) && running) {
                readConfiguration(intent);
                updateNotification();
            }
        }
    };

    public static boolean isRunning() {
        return processRunning;
    }

    /** Send a control message to the running service without starting another service instance. */
    public static void sendControl(Context context, String action, @Nullable Bundle extras) {
        Intent control = new Intent(ACTION_CONTROL)
                .setPackage(context.getPackageName())
                .putExtra(EXTRA_CONTROL_ACTION, action);
        if (extras != null) control.putExtras(extras);
        context.sendBroadcast(control);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        createNotificationChannel();
        ContextCompat.registerReceiver(
                this,
                controlReceiver,
                new IntentFilter(ACTION_CONTROL),
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopPlaybackAndSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_UPDATE.equals(action)) {
            if (running) {
                readConfiguration(intent);
                updateNotification();
            }
            return START_NOT_STICKY;
        }

        if (!ACTION_PLAY.equals(action)) return START_NOT_STICKY;

        readConfiguration(intent);
        if (!running) {
            startInForeground();
            running = true;
            processRunning = true;
            if (!requestAudioFocus()) {
                // Some OEMs temporarily deny focus even though media playback is otherwise
                // available. Do not leave the UI in a fake "playing" state.
                stopPlaybackAndSelf();
                return START_NOT_STICKY;
            }
            startAudioThread();
        } else {
            updateNotification();
        }
        return START_NOT_STICKY;
    }

    private void readConfiguration(Intent intent) {
        bpm = clamp(intent.getIntExtra(EXTRA_BPM, bpm), 35, 240);
        volumePercent = clamp(intent.getIntExtra(EXTRA_VOLUME, volumePercent), 0, 100);
        requestedStartAtEpochMs = intent.getLongExtra(EXTRA_START_AT_EPOCH_MS, 0L);
        timeSignature = safeSignature(intent.getStringExtra(EXTRA_TIME_SIGNATURE));
        beatsPerBar = parseBeats(timeSignature);
    }

    private void startInForeground() {
        int type = Build.VERSION.SDK_INT >= 29
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type);
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 1, openIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, BackgroundMetronomeService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 2, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.metrom_logo_monochrome)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(toPersianDigits(String.valueOf(bpm)) + " ضرب در دقیقه • " + toPersianDigits(timeSignature))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.drawable.ic_notification_stop, getString(R.string.notification_stop), stopPending)
                .build();
    }

    private void updateNotification() {
        if (!running) return;
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.playback_channel_description));
        channel.setSound(null, null);
        channel.enableVibration(false);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    /**
     * Reliable streaming audio loop. Each blocking write contains exactly one beat,
     * including its click and following silence. The audio hardware therefore clocks
     * the metronome instead of a Java timer.
     */
    private void startAudioThread() {
        Thread previous = audioThread;
        if (previous != null) previous.interrupt();

        audioThread = new Thread(() -> {
            AudioTrack track = null;
            try {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();

                int minBytes = AudioTrack.getMinBufferSize(
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_OUT_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                if (minBytes <= 0) minBytes = SAMPLE_RATE / 5 * 2;
                int bufferBytes = Math.max(minBytes * 2, SAMPLE_RATE / 4 * 2);

                track = new AudioTrack(
                        attributes,
                        format,
                        bufferBytes,
                        AudioTrack.MODE_STREAM,
                        AudioManager.AUDIO_SESSION_ID_GENERATE);
                if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                    throw new IllegalStateException("AudioTrack initialization failed");
                }

                synchronized (audioLock) {
                    audioTrack = track;
                }

                long startAt = requestedStartAtEpochMs;
                long delayMs = startAt > 0L ? startAt - System.currentTimeMillis() : 0L;
                if (delayMs > 0L && delayMs < 2000L) SystemClock.sleep(delayMs);
                if (!running) return;

                track.setVolume(1.0f);
                track.play();
                int beatIndex = 0;

                while (running && !Thread.currentThread().isInterrupted()) {
                    while (focusPaused && running && !Thread.currentThread().isInterrupted()) {
                        SystemClock.sleep(20L);
                    }
                    if (!running) break;

                    int localBpm = bpm;
                    int localBeats = Math.max(1, beatsPerBar);
                    int localVolume = volumePercent;
                    if (beatIndex >= localBeats) beatIndex = 0;

                    short[] beat = createBeat(localBpm, beatIndex == 0, localVolume);
                    int offset = 0;
                    while (running && offset < beat.length) {
                        int written = track.write(
                                beat,
                                offset,
                                beat.length - offset,
                                AudioTrack.WRITE_BLOCKING);
                        if (written <= 0) throw new IllegalStateException("AudioTrack write failed: " + written);
                        offset += written;
                    }
                    beatIndex = (beatIndex + 1) % localBeats;
                }
            } catch (RuntimeException ignored) {
                running = false;
                processRunning = false;
            } finally {
                synchronized (audioLock) {
                    if (audioTrack == track) audioTrack = null;
                }
                releaseTrack(track);
                if (!running) {
                    abandonAudioFocus();
                    stopForegroundCompat();
                    stopSelf();
                }
            }
        }, "MetromAudioStream");
        audioThread.setPriority(Thread.MAX_PRIORITY);
        audioThread.start();
    }

    private static short[] createBeat(int bpm, boolean accent, int volumePercent) {
        int frames = Math.max(1, (int) Math.round(SAMPLE_RATE * 60.0 / Math.max(35, bpm)));
        short[] pcm = new short[frames];
        int clickFrames = Math.min(frames, Math.max(1, SAMPLE_RATE * CLICK_MS / 1000));
        double master = Math.max(0.0, Math.min(1.0, volumePercent / 100.0));
        double gain = (accent ? 0.78 : 0.58) * master;
        double f1 = accent ? 1760.0 : 1240.0;
        double f2 = accent ? 880.0 : 620.0;

        for (int i = 0; i < clickFrames; i++) {
            double t = i / (double) SAMPLE_RATE;
            double envelope = Math.exp(-t * 74.0);
            double attack = Math.min(1.0, i / 18.0);
            double sample = (
                    Math.sin(2.0 * Math.PI * f1 * t) * 0.72
                            + Math.sin(2.0 * Math.PI * f2 * t) * 0.28
            ) * envelope * attack * gain;
            int value = (int) Math.round(sample * Short.MAX_VALUE);
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
        return pcm;
    }

    private boolean requestAudioFocus() {
        if (audioManager == null) return true;
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();

        AudioManager.OnAudioFocusChangeListener listener = change -> {
            AudioTrack track = audioTrack;
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                stopPlaybackAndSelf();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                focusPaused = true;
                if (track != null) {
                    try { track.pause(); } catch (RuntimeException ignored) {}
                }
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                if (track != null) {
                    try { track.setVolume(0.25f); } catch (RuntimeException ignored) {}
                }
            } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                if (track != null) {
                    try {
                        track.setVolume(1.0f);
                        if (focusPaused && running) track.play();
                    } catch (RuntimeException ignored) {}
                }
                focusPaused = false;
            }
        };
        legacyFocusListener = listener;

        if (Build.VERSION.SDK_INT >= 26) {
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener(listener)
                    .setAcceptsDelayedFocusGain(false)
                    .build();
            return audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }
        return audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void abandonAudioFocus() {
        if (audioManager == null) return;
        if (Build.VERSION.SDK_INT >= 26 && focusRequest != null) {
            audioManager.abandonAudioFocusRequest(focusRequest);
            focusRequest = null;
        } else if (legacyFocusListener != null) {
            audioManager.abandonAudioFocus(legacyFocusListener);
        }
        legacyFocusListener = null;
    }

    private void stopPlaybackAndSelf() {
        running = false;
        processRunning = false;
        focusPaused = false;

        Thread thread = audioThread;
        audioThread = null;
        if (thread != null) thread.interrupt();

        AudioTrack track = audioTrack;
        if (track != null) {
            try { track.stop(); } catch (RuntimeException ignored) {}
        }
        abandonAudioFocus();
        stopForegroundCompat();
        stopSelf();
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
    }

    private static void releaseTrack(@Nullable AudioTrack track) {
        if (track == null) return;
        try { track.pause(); } catch (RuntimeException ignored) {}
        try { track.flush(); } catch (RuntimeException ignored) {}
        try { track.release(); } catch (RuntimeException ignored) {}
    }

    private static String toPersianDigits(String value) {
        if (value == null || value.isEmpty()) return "";
        final char[] en = {'0','1','2','3','4','5','6','7','8','9'};
        final char[] fa = {'۰','۱','۲','۳','۴','۵','۶','۷','۸','۹'};
        String out = value;
        for (int i = 0; i < en.length; i++) out = out.replace(en[i], fa[i]);
        return out;
    }

    private static String safeSignature(@Nullable String signature) {
        if (signature == null || !signature.matches("^[1-9][0-9]?/[1-9][0-9]?$")) return "4/4";
        return signature;
    }

    private static int parseBeats(@Nullable String signature) {
        if (signature == null) return 4;
        int slash = signature.indexOf('/');
        try {
            return clamp(Integer.parseInt(slash > 0 ? signature.substring(0, slash) : signature), 1, 12);
        } catch (NumberFormatException ignored) {
            return 4;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(controlReceiver); } catch (RuntimeException ignored) {}
        running = false;
        processRunning = false;
        focusPaused = false;
        Thread thread = audioThread;
        if (thread != null) thread.interrupt();
        AudioTrack track = audioTrack;
        audioTrack = null;
        releaseTrack(track);
        abandonAudioFocus();
        super.onDestroy();
    }
}
