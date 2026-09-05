package com.example.audioconvertertest;

import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

// AudioConverter লাইব্রেরির ইম্পোর্ট। app/libs ফোল্ডারে .aar ফাইল রাখার পর এটি রিজলভ হবে।
import com.audioconverter.AudioConverter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * খুবই সাদামাটা টেস্ট অ্যাক্টিভিটি।
 * ধাপ ১: ইউজার একটি অডিও ফাইল সিলেক্ট করে (Storage Access Framework দিয়ে)।
 * ধাপ ২: ফাইলটি cacheDir এ কপি করে সেই কপিকে AudioConverter দিয়ে MP3 তে কনভার্ট করা হয়।
 * ধাপ ৩: কনভার্ট হওয়া আউটপুট ফাইলটি MediaPlayer দিয়ে প্লে করা হয়।
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "AudioConverterTest";

    private TextView tvStatus;
    private Button btnPick;
    private Button btnConvert;
    private Button btnPlay;

    private File inputFile;
    private File outputFile;
    private MediaPlayer mediaPlayer;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<String[]> filePicker =
            registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    handlePickedFile(uri);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnPick = findViewById(R.id.btnPick);
        btnConvert = findViewById(R.id.btnConvert);
        btnPlay = findViewById(R.id.btnPlay);

        btnConvert.setEnabled(false);
        btnPlay.setEnabled(false);

        // কিছু ডিভাইস/ফাইল ম্যানেজার WAV ফাইলের mime type সঠিকভাবে রিপোর্ট করে না,
        // ফলে "audio/*" ফিল্টার দিলে সেগুলো তালিকায় দেখা যায় না। তাই "*/*" দিয়ে
        // সব ফাইল দেখানো হচ্ছে — ইউজার নিজেই সঠিক অডিও ফাইলটি বেছে নেবেন।
        btnPick.setOnClickListener(v -> filePicker.launch(new String[]{"*/*"}));
        btnConvert.setOnClickListener(v -> startConversion());
        btnPlay.setOnClickListener(v -> playOutput());
    }

    private void handlePickedFile(Uri uri) {
        String name = queryFileName(uri);
        setStatus("সিলেক্ট করা হয়েছে: " + name + "\nফাইল কপি করা হচ্ছে...");
        btnConvert.setEnabled(false);
        btnPlay.setEnabled(false);

        executor.execute(() -> {
            try {
                String ext = "";
                int dot = name.lastIndexOf('.');
                if (dot >= 0) {
                    ext = name.substring(dot);
                }
                if (ext.isEmpty()) {
                    ext = ".tmp";
                }

                File dest = new File(getCacheDir(), "input" + ext);
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(dest)) {
                    if (in == null) {
                        throw new IOException("ইনপুট স্ট্রিম খোলা যায়নি");
                    }
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
                inputFile = dest;

                runOnUiThread(() -> {
                    setStatus("ইনপুট প্রস্তুত: " + inputFile.getName()
                            + "\nএখন কনভার্ট করুন।");
                    btnConvert.setEnabled(true);
                });
            } catch (IOException e) {
                Log.e(TAG, "ফাইল কপি করতে ব্যর্থ", e);
                runOnUiThread(() -> setStatus("ফাইল কপি করতে সমস্যা হয়েছে: " + e.getMessage()));
            }
        });
    }

    private String queryFileName(Uri uri) {
        String result = "audio_file";
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    result = cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {
            // নাম না পেলে ডিফল্ট নাম ব্যবহার হবে
        }
        return result;
    }

    private void startConversion() {
        if (inputFile == null) {
            Toast.makeText(this, "আগে একটি অডিও ফাইল সিলেক্ট করুন", Toast.LENGTH_SHORT).show();
            return;
        }

        btnConvert.setEnabled(false);
        setStatus("কনভার্ট হচ্ছে, একটু অপেক্ষা করুন...");

        executor.execute(() -> {
            outputFile = new File(getCacheDir(), "output.mp3");
            boolean success;
            String errorMessage = null;

            try {
                // AudioConverter লাইব্রেরির মূল API কল
                success = AudioConverter.create()
                        .setInput(inputFile)
                        .setOutput(outputFile)
                        .setBitrate(192)
                        .setSampleRate(44100)
                        .setChannels(2)
                        .start();
            } catch (Throwable t) {
                // এখানে ইচ্ছাকৃতভাবে Exception এর বদলে Throwable ধরা হচ্ছে।
                // কারণ, নেটিভ/JNI লাইব্রেরি লোড ব্যর্থ হলে (যেমন UnsatisfiedLinkError,
                // NoClassDefFoundError) সেগুলো Exception নয়, বরং Error টাইপের —
                // শুধু "catch (Exception e)" দিয়ে এগুলো ধরা পড়ে না এবং পুরো অ্যাপ
                // ক্র্যাশ করে। Throwable ধরলে অ্যাপ ক্র্যাশ না করে স্ক্রিনে আসল
                // কারণটা দেখানো সম্ভব হবে (Logcat-এও পুরো স্ট্যাক-ট্রেস যাবে)।
                Log.e(TAG, "কনভার্সন ব্যর্থ হয়েছে", t);
                success = false;
                errorMessage = t.getClass().getSimpleName()
                        + (t.getMessage() != null ? ": " + t.getMessage() : "");
            }

            boolean finalSuccess = success;
            String finalError = errorMessage;
            runOnUiThread(() -> {
                btnConvert.setEnabled(true);
                if (finalSuccess) {
                    setStatus("✅ কনভার্সন সফল হয়েছে!\nআউটপুট: " + outputFile.getAbsolutePath());
                    btnPlay.setEnabled(true);
                } else {
                    setStatus("❌ কনভার্সন ব্যর্থ হয়েছে।"
                            + (finalError != null ? "\nকারণ: " + finalError : ""));
                }
            });
        });
    }

    private void playOutput() {
        if (outputFile == null || !outputFile.exists()) {
            Toast.makeText(this, "কোনো আউটপুট ফাইল পাওয়া যায়নি", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            releasePlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(outputFile.getAbsolutePath());
            mediaPlayer.setOnCompletionListener(mp -> setStatus("প্লেব্যাক শেষ হয়েছে।"));
            mediaPlayer.prepare();
            mediaPlayer.start();
            setStatus("আউটপুট ফাইল প্লে হচ্ছে...");
        } catch (IOException | IllegalStateException | IllegalArgumentException e) {
            // MediaPlayer শুধু IOException না, আউটপুট ফাইল করাপ্ট/অসম্পূর্ণ হলে
            // IllegalStateException বা IllegalArgumentException-ও ছুঁড়তে পারে।
            Log.e(TAG, "প্লে করতে ব্যর্থ", e);
            Toast.makeText(this, "প্লে করতে সমস্যা হয়েছে: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void setStatus(String message) {
        tvStatus.setText(message);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        releasePlayer();
        executor.shutdown();
    }
}
