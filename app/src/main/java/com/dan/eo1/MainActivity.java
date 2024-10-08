package com.dan.eo1;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.squareup.picasso.Picasso;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    public int per_page = 500; //500 max
    public int interval = 5;
    private int currentPosition = 0;
    private Handler handler = new Handler();
    private ImageView imageView;
    private VideoView videoView;
    private String googlePhotosUrl = "";
    private int displayOption = 0;
    private int startQuietHour = -1;
    private int endQuietHour = -1;
    private List<String> googlePhotosUrls;
    private boolean isInQuietHours = false;
    private String customTag = "";
    private SensorManager mSensorManager;
    private Sensor mLightSensor;
    private float lastLightLevel;
    private boolean slideshowpaused = false;
    private ProgressBar progress;
    boolean screenon = true;
    boolean autobrightness = true;
    float brightnesslevel = 0.5f;
    int page = 1;
    int totalPages = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setContentView(R.layout.activity_main);

        File cacheDir = new File(getCacheDir(), "picasso-cache");
        if (cacheDir.exists() && cacheDir.isDirectory()) {
            for (File file : cacheDir.listFiles()) {
                file.delete();
            }
        }

        loadsettings();

        if (googlePhotosUrl.isEmpty()) {
            showSetupDialog();
        }

        imageView = findViewById(R.id.imageView);
        videoView = findViewById(R.id.videoView);
        progress = findViewById(R.id.progressBar);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mLightSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (Math.abs(event.values[0] - lastLightLevel) >= 10.0f) {
                    adjustScreenBrightness(event.values[0]);
                }
            }
            @Override
            public void onAccuracyChanged(Sensor sensor, int i) {
            }
        };
        mSensorManager.registerListener(listener, mLightSensor, SensorManager.SENSOR_DELAY_UI);

        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, new IntentFilter("MSG_RECEIVED"));

        if (quietHoursCalc()) {
            isInQuietHours = true;
            WindowManager.LayoutParams params = getWindow().getAttributes();
            params.screenBrightness = 0;
            getWindow().setAttributes(params);
            videoView.setVisibility(View.GONE);
            imageView.setVisibility(View.GONE);
        }

        super.onCreate(savedInstanceState);
    }

    boolean quietHoursCalc() {
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int normalizedStart = (startQuietHour + 24) % 24;
        int normalizedEnd = (endQuietHour + 24) % 24;
        if ((currentHour >= normalizedStart && currentHour < normalizedEnd) ||
                (normalizedStart > normalizedEnd && (currentHour >= normalizedStart || currentHour < normalizedEnd))) {
            return true;
        } else {
            return false;
        }
    }

    void loadsettings() {
        SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
        googlePhotosUrl = settings.getString("googlePhotosUrl", "");
        displayOption = settings.getInt("displayOption", 0);
        startQuietHour = settings.getInt("startQuietHour", -1);
        endQuietHour = settings.getInt("endQuietHour", -1);
        customTag = settings.getString("customTag", "");
        interval = settings.getInt("interval", 5);
        autobrightness = settings.getBoolean("autobrightness", true);
        brightnesslevel = settings.getFloat("brightnesslevel", 0.5f);
        if (displayOption != 2) customTag = "";
    }

    @Override
    protected void onResume() {
        if (!googlePhotosUrl.isEmpty()) {
            loadImagesFromGooglePhotos();
        }

        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_C) {
            showSetupDialog();
            return super.onKeyDown(keyCode, event);
        }

        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            if (!isInQuietHours) progress.setVisibility(View.VISIBLE);
            imageView.setVisibility(View.INVISIBLE);
            videoView.setVisibility(View.INVISIBLE);
            slideshowpaused = false;
            showNextImage();
            return super.onKeyDown(keyCode, event);
        }

        if (keyCode == 132 || keyCode == 134) {
            //top button pushed
            WindowManager.LayoutParams params = getWindow().getAttributes();
            if (screenon) {
                params.screenBrightness = 0;
                screenon = false;
                imageView.setVisibility(View.INVISIBLE);
                videoView.setVisibility(View.INVISIBLE);
            } else {
                params.screenBrightness = brightnesslevel;
                screenon = true;
                imageView.setVisibility(View.VISIBLE);
                videoView.setVisibility(View.VISIBLE);
            }
            getWindow().setAttributes(params);
            return super.onKeyDown(keyCode, event);
        }

        return super.onKeyDown(keyCode, event);
    }

    private void showSetupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View customLayout = getLayoutInflater().inflate(R.layout.options, null);
        builder.setView(customLayout);

        final EditText googlePhotosUrlEditText = customLayout.findViewById(R.id.editTextGooglePhotosUrl);
        final Spinner startHourSpinner = customLayout.findViewById(R.id.startHourSpinner);
        final Spinner endHourSpinner = customLayout.findViewById(R.id.endHourSpinner);
        final Button btnLoadConfig = customLayout.findViewById(R.id.btnLoadConfig);
        final EditText editTextCustomTag = customLayout.findViewById(R.id.editTextCustomTag);
        final EditText editTextInterval = customLayout.findViewById(R.id.editTextInterval);
        final CheckBox cbAutoBrightness = customLayout.findViewById(R.id.cbBrightnessAuto);
        final SeekBar sbBrightness = customLayout.findViewById(R.id.sbBrightness);

        googlePhotosUrlEditText.setText(googlePhotosUrl);
        editTextCustomTag.setText(customTag);
        editTextInterval.setText(String.valueOf(interval));
        if (autobrightness) {
            cbAutoBrightness.setChecked(true);
            sbBrightness.setVisibility(View.GONE);
        }

        RadioGroup optionsRadioGroup = customLayout.findViewById(R.id.optionsRadioGroup);
        optionsRadioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == R.id.radioOption3) {
                    editTextCustomTag.setVisibility(View.VISIBLE);
                    editTextCustomTag.setText(customTag);
                } else {
                    editTextCustomTag.setVisibility(View.GONE);
                    editTextCustomTag.setText("");
                }
            }
        });

        sbBrightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                brightnesslevel = i / 10f;
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = brightnesslevel;
                getWindow().setAttributes(params);
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        sbBrightness.setProgress((int) (brightnesslevel * 10));

        cbAutoBrightness.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                autobrightness = b;
                if (b)
                    sbBrightness.setVisibility(View.GONE);
                else
                    sbBrightness.setVisibility(View.VISIBLE);
            }
        });

        editTextCustomTag.setVisibility(View.GONE);
        if (displayOption == 0) ((RadioButton) customLayout.findViewById(R.id.radioOption1)).setChecked(true);
        if (displayOption == 1) ((RadioButton) customLayout.findViewById(R.id.radioOption2)).setChecked(true);
        if (displayOption == 2) {
            ((RadioButton) customLayout.findViewById(R.id.radioOption3)).setChecked(true);
            editTextCustomTag.setVisibility(View.VISIBLE);
            editTextCustomTag.setText(customTag);
        }

        // Set up the Spinners for start and end hour
        String[] hours = new String[24];
        for (int i = 0; i < 24; i++) {
            hours[i] = String.format("%02d", i);
        }
        ArrayAdapter<String> hourAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, hours);
        hourAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        startHourSpinner.setAdapter(hourAdapter);
        if (startQuietHour != -1) startHourSpinner.setSelection(startQuietHour);
        endHourSpinner.setAdapter(hourAdapter);
        if (endQuietHour != -1) endHourSpinner.setSelection(endQuietHour);

        btnLoadConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                File file = new File(downloadsDir, "config.txt");
                if (!file.exists()) {
                    Toast.makeText(MainActivity.this, "Can't find config.txt", Toast.LENGTH_SHORT).show();
                } else {
                    StringBuilder sb = new StringBuilder();
                    try {
                        BufferedReader br = new BufferedReader(new FileReader(file));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                        br.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    googlePhotosUrlEditText.setText(sb.toString().split("\n")[0]);
                }
            }
        });

        builder.setTitle("Setup")
                .setCancelable(false)
                .setView(customLayout)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        googlePhotosUrl = googlePhotosUrlEditText.getText().toString().trim();
                        displayOption = Util.getSelectedOptionIndex(optionsRadioGroup);
                        startQuietHour = Integer.parseInt(startHourSpinner.getSelectedItem().toString());
                        endQuietHour = Integer.parseInt(endHourSpinner.getSelectedItem().toString());
                        if (editTextCustomTag.getText().toString().trim() != customTag) page = 1;
                        customTag = editTextCustomTag.getText().toString().trim();
                        interval = Integer.parseInt(editTextInterval.getText().toString().trim());
                        autobrightness = cbAutoBrightness.isChecked();

                        if (!googlePhotosUrl.isEmpty()) {
                            SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
                            SharedPreferences.Editor editor = settings.edit();
                            editor.putString("googlePhotosUrl", googlePhotosUrl);
                            editor.putInt("displayOption", displayOption);
                            editor.putInt("startQuietHour", startQuietHour);
                            editor.putInt("endQuietHour", endQuietHour);
                            editor.putString("customTag", customTag);
                            editor.putInt("interval", interval);
                            editor.putBoolean("autobrightness", autobrightness);
                            editor.putFloat("brightnesslevel", brightnesslevel);
                            editor.apply();

                            Toast.makeText(MainActivity.this, "Saved!  Hit 'C' to come back here later.", Toast.LENGTH_SHORT).show();

                            if (quietHoursCalc()) isInQuietHours = true; else isInQuietHours = false;

                            loadImagesFromGooglePhotos();

                            if (isInQuietHours) adjustScreenBrightness(0);
                        } else {
                            Toast.makeText(MainActivity.this, "Please enter Google Photos URL", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        builder.show();
    }

    private void startSlideshow() {
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                showSlideshow();
                handler.postDelayed(this, 60000 * interval);
            }
        }, 60000 * interval);

        showSlideshow();
    }

    private void showSlideshow() {
        Toast.makeText(MainActivity.this, "showslideshow " + quietHoursCalc() + " " + autobrightness, Toast.LENGTH_LONG).show();
        if (quietHoursCalc()) {
            if (!isInQuietHours) {
                //entering quiet, turn off screen
                isInQuietHours = true;
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = 0;
                getWindow().setAttributes(params);
                videoView.setVisibility(View.GONE);
                imageView.setVisibility(View.GONE);
            }
        } else {
            if (isInQuietHours) {
                isInQuietHours = false;
            }
            if (autobrightness) {
                adjustScreenBrightness(lastLightLevel);
            } else {
                WindowManager.LayoutParams params = getWindow().getAttributes();
                params.screenBrightness = brightnesslevel;
                getWindow().setAttributes(params);
            }
            showNextImage();
        }
    }

    private void showNextImage() {
        if (googlePhotosUrls != null && !googlePhotosUrls.isEmpty() && slideshowpaused==false) {
            if (currentPosition >= googlePhotosUrls.size()) {
                currentPosition = 0;
            }

            try {
                String mediaUrl = googlePhotosUrls.get(currentPosition);

                if (!mediaUrl.contains("video")) {
                    videoView.setVisibility(View.INVISIBLE);
                    imageView.setVisibility(View.VISIBLE);

                    Picasso.get().load(mediaUrl).fit().centerInside().into(imageView);
                    progress.setVisibility(View.INVISIBLE);

                    currentPosition++;
                } else {
                    imageView.setVisibility(View.INVISIBLE);

                    MediaController mediaController = new MediaController(this);
                    mediaController.setAnchorView(videoView);
                    mediaController.setVisibility(View.INVISIBLE);

                    videoView.setMediaController(mediaController);

                    new DownloadVideoTask().execute(mediaUrl);
                }
            } catch (Exception ex) {
                progress.setVisibility(View.VISIBLE);
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        showNextImage();
                    }
                }, 10000);
            }
        }
    }

    private void loadImagesFromGooglePhotos() {
        if (!isInQuietHours) progress.setVisibility(View.VISIBLE);
        imageView.setVisibility(View.INVISIBLE);
        videoView.setVisibility(View.INVISIBLE);

        if (Util.isNetworkAvailable(this)) {
            Toast.makeText(MainActivity.this, "IP = " + Util.getIPAddress(), Toast.LENGTH_LONG).show();

            Intent serviceIntent = new Intent(this, MyMessageService.class);
            startService(serviceIntent);

            // Fetch images from Google Photos album using public URL
            new FetchGooglePhotosTask().execute(googlePhotosUrl);
        } else {
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadImagesFromGooglePhotos(); // Retry loading images after delay
                }
            }, 10000);
        }
    }

    private class FetchGooglePhotosTask extends AsyncTask<String, Void, List<String>> {
        @Override
        protected List<String> doInBackground(String... params) {
            String albumUrl = params[0];
            // Fetch image URLs from Google Photos album using the public URL
            // Implement the logic to fetch image URLs from the album
            // Return the list of image URLs
            return null; // Replace with actual implementation
        }

        @Override
        protected void onPostExecute(List<String> urls) {
            if (urls != null && !urls.isEmpty()) {
                googlePhotosUrls = urls;
                currentPosition = 0;
                startSlideshow();
            } else {
                Toast.makeText(MainActivity.this, "Failed to fetch images from Google Photos", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void adjustScreenBrightness(float lightValue){
        if (autobrightness) {
            if (!isInQuietHours) {
                // Determine the desired brightness range
                float maxBrightness = 1.0f; // Maximum brightness value (0 to 1)
                float minBrightness = 0.0f; // Minimum brightness value (0 to 1)

                // Map the light sensor value (0 to 30) to the desired brightness range (0 to 1)
                float brightness = (lightValue / 30f) * (maxBrightness - minBrightness) + minBrightness;

                // Make sure brightness is within the valid range
                brightness = Math.min(Math.max(brightness, minBrightness), maxBrightness);

                // Apply the brightness setting to the screen
                WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
                layoutParams.screenBrightness = brightness;
                getWindow().setAttributes(layoutParams);
            }
        }
        lastLightLevel = lightValue;
    }

    private class DownloadVideoTask extends AsyncTask<String, Void, String> {
        private static final int CONNECTION_TIMEOUT = 15000;
        private static final int MAX_RETRIES = 3;
        @Override
        protected String doInBackground(String... params) {
            String videoUrl = params[0];
            String videoId = videoUrl.substring(videoUrl.lastIndexOf('/') + 1);
            if (new File(getCacheDir(), videoId + ".mp4").exists()) {
                return new File(getCacheDir(), videoId + ".mp4").getPath();
            }

            Util.cacheCleanup(getCacheDir());

            int retryCount = 0;
            while (retryCount < MAX_RETRIES) {
                try {
                    // Download the video from the URL
                    URL url = new URL(videoUrl);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(CONNECTION_TIMEOUT);
                    connection.connect();

                    File tempFile = new File(getCacheDir(), videoId + ".mp4");
                    FileOutputStream outputStream = new FileOutputStream(tempFile);

                    InputStream inputStream = connection.getInputStream();
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    outputStream.close();
                    inputStream.close();

                    return tempFile.getPath();
                } catch (SocketTimeoutException e) {
                    retryCount++;
                } catch (IOException e) {
                    retryCount++;
                } catch (Exception e) {
                    retryCount++;
                }
            }
            return "ERR: Timeout";
        }

        @Override
        protected void onPostExecute(String file) {
            if (!file.startsWith("ERR")) {
                videoView.setVisibility(View.VISIBLE);
                imageView.setVisibility(View.INVISIBLE);
                progress.setVisibility(View.INVISIBLE);
                videoView.setVideoPath(file);

                videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mediaPlayer) {
                        mediaPlayer.setLooping(true);
                        currentPosition++;
                        videoView.start();
                    }
                });
                videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
                        progress.setVisibility(View.VISIBLE);
                        currentPosition++;
                        showNextImage();
                        return true;
                    }
                });
            } else {
                progress.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "ERR> " + file, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
         public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getAction() != null) {
                if (intent.getAction().equals("MSG_RECEIVED")) {
                    String type = intent.getStringExtra("type");

                    if (type.equals("options")) {
                        Toast.makeText(MainActivity.this, "Options received   level=" + intent.getFloatExtra("brightness", 1f), Toast.LENGTH_LONG).show();

                        WindowManager.LayoutParams params = getWindow().getAttributes();
                        float incomingbrightness = intent.getFloatExtra("brightness", 1f);
                        if (incomingbrightness == -1.0f) {
                            autobrightness = true;
                            adjustScreenBrightness(lastLightLevel);
                        } else {
                            autobrightness = false;
                            brightnesslevel = incomingbrightness;
                            params.screenBrightness = incomingbrightness;
                            getWindow().setAttributes(params);
                        }

                        int incominginterval = intent.getIntExtra("interval", 5);
                        int incomingStartQuietHour = intent.getIntExtra("startQuietHour", -1);
                        int incomingEndQuietHour = intent.getIntExtra("endQuietHour", -1);

                        SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("autobrightness", autobrightness);
                        editor.putFloat("brightnesslevel", incomingbrightness);
                        editor.putInt("interval", incominginterval);
                        editor.putInt("startQuietHour", incomingStartQuietHour);
                        editor.putInt("startQuietHour", incomingEndQuietHour);
                        editor.apply();

                        if (incominginterval != interval || incomingStartQuietHour != startQuietHour || incomingEndQuietHour != endQuietHour) {
                            interval = incominginterval;
                            startQuietHour = incomingStartQuietHour;
                            endQuietHour = incomingEndQuietHour;
                            if (quietHoursCalc()) isInQuietHours = true; else isInQuietHours = false;
                            loadImagesFromGooglePhotos();
                            if (isInQuietHours) adjustScreenBrightness(0);
                        }
                    }

                    if (type.equals("image") || type.equals("video")) {
                        progress.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.INVISIBLE);
                        videoView.setVisibility(View.INVISIBLE);

                        slideshowpaused = true;

                        if (isInQuietHours) {
                            isInQuietHours = false;
                            WindowManager.LayoutParams params = getWindow().getAttributes();
                            params.screenBrightness = brightnesslevel;
                            getWindow().setAttributes(params);
                        }

                        String mediaUrl = intent.getStringExtra("imageid");
                        if (type.equals("image")) {
                            loadImage(mediaUrl);
                        } else if (type.equals("video")) {
                            loadVideo(mediaUrl);
                        }
                    }

                    if (type.equals("resume")) {
                        progress.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.INVISIBLE);
                        videoView.setVisibility(View.INVISIBLE);

                        if (isInQuietHours) {
                            isInQuietHours = false;
                            WindowManager.LayoutParams params = getWindow().getAttributes();
                            params.screenBrightness = brightnesslevel;
                            getWindow().setAttributes(params);
                        }

                        slideshowpaused = false;
                        showNextImage();
                    }

                    if (type.equals("tag")) {
                        progress.setVisibility(View.VISIBLE);
                        imageView.setVisibility(View.INVISIBLE);
                        videoView.setVisibility(View.INVISIBLE);

                        customTag = intent.getStringExtra("tag");
                        if (customTag.equals("")) {
                            SharedPreferences settings = getSharedPreferences("prefs", MODE_PRIVATE);
                            displayOption = settings.getInt("displayOption", 0);
                            customTag = settings.getString("customTag", "");
                        } else {
                            displayOption = 2;
                            page = 1;
                        }
                        slideshowpaused = false;
                        loadImagesFromGooglePhotos();
                    }

                }
            }
        }
    };

    public void loadVideo(String url) {
        MediaController mediaController = new MediaController(MainActivity.this);
        mediaController.setAnchorView(videoView);
        mediaController.setVisibility(View.INVISIBLE);

        videoView.setMediaController(mediaController);

        new DownloadVideoTask().execute(url);
    }

    public void loadImage(String url) {
        videoView.setVisibility(View.INVISIBLE);
        Picasso.get().load(url).fit().centerInside().into(imageView);
        progress.setVisibility(View.INVISIBLE);
        imageView.setVisibility(View.VISIBLE);
    }

}
