package ir.msbmusic.metrom;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/**
 * Backward-compatibility shim for repositories that still contain the old
 * OfflineMetronomeActivity from an intermediate development build.
 *
 * Metrom no longer exposes a separate native offline metronome UI. Offline
 * operation is handled by the normal MainActivity/TWA flow and the cached
 * Studio, so this Activity only forwards to MainActivity and finishes.
 */
public final class OfflineMetronomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent source = getIntent();
        Intent target = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        if (source != null) {
            Uri data = source.getData();
            if (data != null) {
                target.setData(data);
            }
        }

        startActivity(target);
        finish();
    }
}
