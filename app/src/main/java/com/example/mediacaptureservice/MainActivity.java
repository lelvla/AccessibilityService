package com.example.mediacaptureservice;

import android.media.MediaPlayer;
import android.media.MediaRecorder;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    public static String fileName;
    // Service 呼叫 startRecordingA/stopRecordingA 會用到的全域 recorder/player
    public static MediaRecorder recorder;
    public static MediaPlayer player;
}
