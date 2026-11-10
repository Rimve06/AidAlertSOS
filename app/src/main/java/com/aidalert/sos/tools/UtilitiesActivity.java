package com.aidalert.sos.tools;

import android.Manifest;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import com.aidalert.sos.data.Prefs;
import com.aidalert.sos.engine.SosGuardService;
import com.aidalert.sos.ui.BaseActivity;
import com.aidalert.sos.ui.Ui;
import com.aidalert.sos.util.AppExecutors;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Emergency utilities that need no internet: flashlight and strobe, siren, spoken help message, fake call,
 * audio recording, video recording, and the accident-detection switch.
 */
public class UtilitiesActivity extends BaseActivity {
    private LinearLayout list;
    private TextView btnTorch, btnStrobe, btnSiren, btnVoice, btnRec;
    private TextView txtRec;

    private CameraManager cam;
    private String camId;
    private boolean torchOn, strobe;
    private final Runnable strobeTick = new Runnable() {
        @Override
        public void run() {
            if (!strobe) return;
            setTorch(!torchOn);
            AppExecutors.mainDelayed(this, 250);
        }
    };

    private volatile boolean sirenOn;
    private Thread sirenThread;

    private TextToSpeech tts;
    private boolean ttsReady, voiceOn;
    private final Runnable voiceTick = new Runnable() {
        @Override
        public void run() {
            if (!voiceOn || !ttsReady) return;
            android.os.Bundle p = new android.os.Bundle();
            p.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_ALARM);
            tts.speak(VOICE_TEXT, TextToSpeech.QUEUE_FLUSH, p, "aid");
            AppExecutors.mainDelayed(this, 9000);
        }
    };
    private static final String VOICE_TEXT = "Emergency. I need help. Please call nine nine nine, or the police.";

    private MediaRecorder recorder;
    private File recFile;

    private final ActivityResultLauncher<String> micPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), ok -> {
                if (ok) toggleRecording();
                else Ui.toast(this, "Microphone not allowed, so recording cannot start.");
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), dp(14), dp(14), dp(24));
        sv.addView(list);
        setContentView(sv);
        setTitle("Emergency utilities");

        btnTorch = card("Flashlight", "Turn the phone's torch on or off.", "Turn on", v -> setTorch(!torchOn));
        btnStrobe = card("Strobe light", "Flashing torch to be seen from far away.", "Start", v -> toggleStrobe());
        btnSiren = card("Siren", "Loud alarm to attract attention. Turn the phone volume up first.", "Start", v -> toggleSiren());
        btnVoice = card("Voice alert", "The phone repeatedly says out loud that you need help and to call 999.", "Start", v -> toggleVoice());
        card("Fake call", "Pretend to receive a call to leave an uncomfortable situation.", "Set up", v -> fakeCallDialog());
        btnRec = card("Audio recording", "Record sound as evidence. Saved on this phone only.", "Start", v -> toggleRecording());
        txtRec = (TextView) list.getChildAt(list.getChildCount() - 1).findViewWithTag("desc");
        card("Video recording", "Opens your camera app to record video.", "Record", v -> recordVideo());
        accidentCard();

        initTorch();
        tts = new TextToSpeech(this, status -> ttsReady = status == TextToSpeech.SUCCESS);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** Adds a card with a title, a description and one action button; returns the button. */
    private TextView card(String title, String desc, String action, View.OnClickListener click) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(com.aidalert.sos.R.drawable.bg_card);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        c.setLayoutParams(lp);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setTextColor(0xFF111827);
        TextView d = new TextView(this);
        d.setText(desc);
        d.setTag("desc");
        d.setTextSize(13);
        d.setTextColor(0xFF667085);
        com.google.android.material.button.MaterialButton b = new com.google.android.material.button.MaterialButton(this);
        b.setText(action);
        b.setOnClickListener(click);
        c.addView(t);
        c.addView(d);
        c.addView(b);
        list.addView(c);
        return b;
    }

    private void accidentCard() {
        final TextView b = card("Accident detection",
                "If the phone feels a hard impact and then stays still, it asks \"Are you OK?\" and sends SOS after 30 seconds "
                        + "unless you answer. A dropped phone can trigger it too. Uses the motion sensor; works only while the SOS guard runs.",
                Prefs.of(this).impactOn() ? "Turn off" : "Turn on", null);
        b.setOnClickListener(v -> {
            Prefs p = Prefs.of(this);
            p.impactOn(!p.impactOn());
            SosGuardService.start(this);        // re-reads the setting
            b.setText(p.impactOn() ? "Turn off" : "Turn on");
            Ui.toast(this, p.impactOn() ? "Accident detection is on." : "Accident detection is off.");
        });
    }

    // ------------------------------------------------------------ flashlight
    private void initTorch() {
        cam = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String id : cam.getCameraIdList()) {
                Boolean flash = cam.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(flash)) {
                    camId = id;
                    break;
                }
            }
        } catch (CameraAccessException | RuntimeException e) {
            camId = null;
        }
        if (camId == null) {
            btnTorch.setEnabled(false);
            btnStrobe.setEnabled(false);
            btnTorch.setText("No flash on this phone");
            return;
        }
        cam.registerTorchCallback(new CameraManager.TorchCallback() {
            @Override
            public void onTorchModeChanged(String id, boolean enabled) {
                if (!id.equals(camId)) return;
                torchOn = enabled;
                btnTorch.setText(enabled ? "Turn off" : "Turn on");
            }
        }, null);
    }

    private void setTorch(boolean on) {
        if (camId == null) return;
        try {
            cam.setTorchMode(camId, on);
            torchOn = on;
        } catch (CameraAccessException | RuntimeException e) {
            Ui.toast(this, "Could not use the flashlight (another app may be using the camera).");
            strobe = false;
        }
    }

    private void toggleStrobe() {
        strobe = !strobe;
        btnStrobe.setText(strobe ? "Stop" : "Start");
        if (strobe) AppExecutors.mainDelayed(strobeTick, 0);
        else setTorch(false);
    }

    // ------------------------------------------------------------ siren
    private void toggleSiren() {
        if (sirenOn) {
            stopSiren();
            return;
        }
        sirenOn = true;
        btnSiren.setText("Stop");
        sirenThread = new Thread(() -> {
            final int rate = 22050;
            short[] pcm = SirenTone.cycle(rate);
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(pcm.length * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC).build();
            track.write(pcm, 0, pcm.length);
            track.setLoopPoints(0, pcm.length, -1);
            track.play();
            while (sirenOn) {
                try {
                    Thread.sleep(150);
                } catch (InterruptedException e) {
                    break;
                }
            }
            track.stop();
            track.release();
        }, "siren");
        sirenThread.start();
    }

    private void stopSiren() {
        sirenOn = false;
        btnSiren.setText("Start");
    }

    // ------------------------------------------------------------ voice alert
    private void toggleVoice() {
        if (!ttsReady) {
            Ui.toast(this, "The speech engine is not ready. Try again in a moment.");
            return;
        }
        voiceOn = !voiceOn;
        btnVoice.setText(voiceOn ? "Stop" : "Start");
        if (voiceOn) AppExecutors.mainDelayed(voiceTick, 0);
        else tts.stop();
    }

    // ------------------------------------------------------------ recording
    private void toggleRecording() {
        if (recorder != null) {
            stopRecording();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "recordings");
        if (!dir.exists() && !dir.mkdirs()) {
            Ui.toast(this, "Cannot create the recordings folder.");
            return;
        }
        recFile = new File(dir, "rec_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".m4a");
        recorder = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(this) : new MediaRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(recFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            btnRec.setText("Stop recording");
            if (txtRec != null) txtRec.setText("Recording...");
        } catch (IOException | RuntimeException e) {
            recorder.release();
            recorder = null;
            Ui.toast(this, "Could not start recording.");
        }
    }

    private void stopRecording() {
        try {
            recorder.stop();
        } catch (RuntimeException e) {
            // stopped too quickly: the file is empty
        }
        recorder.release();
        recorder = null;
        btnRec.setText("Start");
        if (txtRec != null) txtRec.setText("Saved: " + (recFile == null ? "" : recFile.getName())
                + "\nFolder: Android/data/com.aidalert.sos/files/Music/recordings");
    }

    private void recordVideo() {
        try {
            startActivity(new Intent(MediaStore.ACTION_VIDEO_CAPTURE));
        } catch (RuntimeException e) {
            Ui.toast(this, "No camera app found.");
        }
    }

    // ------------------------------------------------------------ fake call
    private void fakeCallDialog() {
        final String[] items = {"Ring now", "Ring in 10 seconds", "Ring in 30 seconds", "Ring in 1 minute"};
        final int[] secs = {0, 10, 30, 60};
        new AlertDialog.Builder(this).setTitle("Fake call: caller is \"Mom\"")
                .setItems(items, (d, i) -> startActivity(new Intent(this, FakeCallActivity.class)
                        .putExtra(FakeCallActivity.EXTRA_DELAY, secs[i]).putExtra(FakeCallActivity.EXTRA_NAME, "Mom")))
                .show();
    }

    @Override
    protected void onDestroy() {
        strobe = false;
        AppExecutors.cancelMain(strobeTick);
        voiceOn = false;
        AppExecutors.cancelMain(voiceTick);
        stopSiren();
        if (recorder != null) stopRecording();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
