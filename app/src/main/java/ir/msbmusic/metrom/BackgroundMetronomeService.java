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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

/**
 * Low-overhead native metronome for screen-off playback.
 *
 * A single PCM bar is generated only when tempo/meter/volume changes and is
 * looped by AudioTrack.MODE_STATIC. There is no timer polling, wake lock,
 * WorkManager job or network request in this service.
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
    private static final int CLICK_MS = 52;

    private final Object audioLock = new Object();
    private AudioTrack audioTrack;
    private AudioManager audioManager;
    private AudioFocusRequest focusRequest;
    private AudioManager.OnAudioFocusChangeListener legacyFocusListener;
    private Thread buildThread;
    private boolean running;
    private boolean focusPaused;
    private int generation;

    private int bpm = 90;
    private int beatsPerBar = 4;
    private String timeSignature = "4/4";
    private int volumePercent = 80;
    private long requestedStartAtEpochMs;

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
                boolean changed = readConfiguration(intent);
                updateNotification();
                if (changed) rebuildLoop(false);
            }
        }
    };

    /** Send a control message to an already-running service without starting a new background service. */
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
        IntentFilter filter = new IntentFilter(ACTION_CONTROL);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
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
                boolean changed = readConfiguration(intent);
                updateNotification();
                if (changed) rebuildLoop(false);
            }
            return START_NOT_STICKY;
        }
        if (!ACTION_PLAY.equals(action)) return START_NOT_STICKY;

        boolean changed = readConfiguration(intent);
        if (!running) {
            // Promote first, then request focus. This ordering is important on newer Android releases.
            startInForeground();
            running = true;
            if (!requestAudioFocus()) {
                stopPlaybackAndSelf();
                return START_NOT_STICKY;
            }
            rebuildLoop(true);
        } else {
            updateNotification();
            if (changed) rebuildLoop(true);
        }
        return START_NOT_STICKY;
    }

    private boolean readConfiguration(Intent intent) {
        int oldBpm = bpm;
        int oldBeats = beatsPerBar;
        int oldVolume = volumePercent;
        String oldSignature = timeSignature;

        bpm = clamp(intent.getIntExtra(EXTRA_BPM, bpm), 35, 240);
        volumePercent = clamp(intent.getIntExtra(EXTRA_VOLUME, volumePercent), 0, 100);
        requestedStartAtEpochMs = intent.getLongExtra(EXTRA_START_AT_EPOCH_MS, 0L);
        String incomingSignature = intent.getStringExtra(EXTRA_TIME_SIGNATURE);
        timeSignature = safeSignature(incomingSignature);
        beatsPerBar = parseBeats(timeSignature);
        return oldBpm != bpm || oldBeats != beatsPerBar || oldVolume != volumePercent || !oldSignature.equals(timeSignature);
    }

    private void startInForeground() {
        Notification notification = buildNotification();
        int type = Build.VERSION.SDK_INT >= 29
                ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                : 0;
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type);
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
                .setSmallIcon(R.drawable.ic_stat_metronome)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(bpm + " BPM • " + timeSignature)
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
        manager.notify(NOTIFICATION_ID, buildNotification());
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
        manager.createNotificationChannel(channel);
    }

    /** Generate one bar off the main thread and atomically swap the static AudioTrack loop. */
    private void rebuildLoop(boolean honorRequestedStart) {
        final int localGeneration = ++generation;
        final int localBpm = bpm;
        final int localBeats = beatsPerBar;
        final int localVolume = volumePercent;
        final long localStartAt = honorRequestedStart ? requestedStartAtEpochMs : 0L;

        Thread previous = buildThread;
        if (previous != null) previous.interrupt();

        buildThread = new Thread(() -> {
            AudioTrack candidate = null;
            try {
                short[] bar = createBar(localBpm, localBeats, localVolume);
                if (!running || localGeneration != generation || Thread.currentThread().isInterrupted()) return;

                int bufferBytes = bar.length * 2;
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                AudioFormat format = new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build();

                candidate = new AudioTrack(
                        attributes,
                        format,
                        bufferBytes,
                        AudioTrack.MODE_STATIC,
                        AudioManager.AUDIO_SESSION_ID_GENERATE);
                if (candidate.getState() != AudioTrack.STATE_INITIALIZED) return;
                int written = candidate.write(bar, 0, bar.length, AudioTrack.WRITE_BLOCKING);
                if (written != bar.length) return;
                candidate.setLoopPoints(0, bar.length, -1);

                long delayMs = localStartAt > 0 ? localStartAt - System.currentTimeMillis() : 0L;
                if (delayMs > 0 && delayMs < 2_000L) Thread.sleep(delayMs);
                if (!running || localGeneration != generation || Thread.currentThread().isInterrupted()) return;

                AudioTrack old;
                synchronized (audioLock) {
                    old = audioTrack;
                    audioTrack = candidate;
                    candidate = null;
                    audioTrack.play();
                    focusPaused = false;
                }
                releaseTrack(old);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException ignored) {
                if (localGeneration == generation) stopPlaybackAndSelf();
            } finally {
                releaseTrack(candidate);
            }
        }, "MetromLoopBuilder");
        buildThread.setPriority(Thread.NORM_PRIORITY + 1);
        buildThread.start();
    }

    private static short[] createBar(int bpm, int beats, int volumePercent) {
        double framesPerBeat = SAMPLE_RATE * 60.0 / Math.max(35, bpm);
        int frames = Math.max(1, (int) Math.round(framesPerBeat * Math.max(1, beats)));
        short[] pcm = new short[frames];
        int clickFrames = Math.max(1, SAMPLE_RATE * CLICK_MS / 1_000);
        double master = Math.max(0.0, Math.min(1.0, volumePercent / 100.0));

        for (int beat = 0; beat < beats; beat++) {
            int start = (int) Math.round(beat * framesPerBeat);
            boolean accent = beat == 0;
            double frequency = accent ? 1_560.0 : 1_050.0;
            double amplitude = (accent ? 0.42 : 0.29) * master;
            int available = Math.min(clickFrames, pcm.length - start);
            for (int i = 0; i < available; i++) {
                double t = i / (double) SAMPLE_RATE;
                double envelope = Math.exp(-t * 92.0);
                double sample = Math.sin(2.0 * Math.PI * frequency * t) * envelope * amplitude;
                int mixed = pcm[start + i] + (int) Math.round(sample * Short.MAX_VALUE);
                pcm[start + i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, mixed));
            }
        }
        return pcm;
    }

    private boolean requestAudioFocus() {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();

        AudioManager.OnAudioFocusChangeListener listener = change -> {
            synchronized (audioLock) {
                if (change == AudioManager.AUDIOFOCUS_LOSS) {
                    stopPlaybackAndSelf();
                } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    if (audioTrack != null && audioTrack.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                        try { audioTrack.pause(); } catch (RuntimeException ignored) {}
                        focusPaused = true;
                    }
                } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                    if (audioTrack != null) {
                        try { audioTrack.setVolume(0.25f); } catch (RuntimeException ignored) {}
                    }
                } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                    if (audioTrack != null) {
                        try {
                            audioTrack.setVolume(1.0f);
                            if (focusPaused && running) audioTrack.play();
                        } catch (RuntimeException ignored) {}
                    }
                    focusPaused = false;
                }
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
                listener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
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
        if (!running && audioTrack == null) {
            stopSelf();
            return;
        }
        running = false;
        focusPaused = false;
        generation++;

        Thread thread = buildThread;
        buildThread = null;
        if (thread != null) thread.interrupt();

        AudioTrack track;
        synchronized (audioLock) {
            track = audioTrack;
            audioTrack = null;
        }
        releaseTrack(track);
        abandonAudioFocus();
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            //noinspection deprecation
            stopForeground(true);
        }
        stopSelf();
    }

    private static void releaseTrack(@Nullable AudioTrack track) {
        if (track == null) return;
        try { track.pause(); } catch (RuntimeException ignored) {}
        try { track.flush(); } catch (RuntimeException ignored) {}
        try { track.release(); } catch (RuntimeException ignored) {}
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
        focusPaused = false;
        generation++;
        Thread thread = buildThread;
        if (thread != null) thread.interrupt();
        AudioTrack track;
        synchronized (audioLock) {
            track = audioTrack;
            audioTrack = null;
        }
        releaseTrack(track);
        abandonAudioFocus();
        super.onDestroy();
    }
}
