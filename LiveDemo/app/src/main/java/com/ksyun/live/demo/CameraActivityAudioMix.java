package com.ksyun.live.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.support.v4.content.PermissionChecker;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.Chronometer;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.ksy.recordlib.service.core.KSYStreamer;
import com.ksy.recordlib.service.core.KSYStreamerConfig;
import com.ksy.recordlib.service.stats.OnLogEventListener;
import com.ksy.recordlib.service.stats.StreamStatusEventHandler;
import com.ksy.recordlib.service.streamer.OnStatusListener;
import com.ksy.recordlib.service.streamer.RecorderConstants;
import com.ksy.recordlib.service.util.audio.KSYBgmPlayer;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivityAudioMix extends Activity {

    private static final String TAG = "CameraActivityAudioMix";

    private GLSurfaceView mCameraPreview;

    private SeekBar seek_mix;

    private KSYStreamer mStreamer;
    private KSYBgmPlayer mKsyBgmPlayer;

    private Handler mHandler;

    private LinearLayout layout_seek;

    private final ButtonObserver mObserverButton = new ButtonObserver();

    private Chronometer chronometer;
    private View mDeleteView;
    private View mSwitchCameraView;
    private View mFlashView;

    private CheckBox mBgm;
    private CheckBox mEarMirror;
    private CheckBox mMuteAudio;
    private TextView mShootingText;
    private boolean recording = false;
    private boolean isFlashOpened = false;
    private boolean startAuto = false;
    private boolean audio_mix = false;
    private boolean mute_audio = false;
    private boolean earMirror = false;
    private boolean landscape = false;
    private boolean printDebugInfo = false;
    private float bgmVolume = 0.6f;
    private long lastPipClickTime = 0;
    private String mUrl, mDebugInfo = "";
    private String mBgmPath = "/sdcard/test.mp3";
    private static final String START_STRING = "开始直播";
    private static final String STOP_STRING = "停止直播";
    private TextView mUrlTextView, mDebugInfoTextView;
    private volatile boolean mAcitivityResumed = false;
    private KSYStreamerConfig.ENCODE_METHOD encode_method = KSYStreamerConfig.ENCODE_METHOD.SOFTWARE;
    public final static String URL = "url";
    public final static String FRAME_RATE = "framerate";
    public final static String VIDEO_BITRATE = "video_bitrate";
    public final static String AUDIO_BITRATE = "audio_bitrate";
    public final static String VIDEO_RESOLUTION = "video_resolution";
    public final static String LANDSCAPE = "landscape";
    public final static String ENCDODE_METHOD = "ENCDODE_METHOD";
    public final static String START_ATUO = "start_auto";
    public final static String MANUAL_FOCUS = "manual_focus";
    public static final String SHOW_DEBUGINFO = "SHOW_DEBUGINFO";

    Timer timer;
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    public static void startActivity(Context context, int fromType,
                                     String rtmpUrl, int frameRate, int videoBitrate, int audioBitrate,
                                     int videoResolution, boolean isLandscape, KSYStreamerConfig.ENCODE_METHOD encodeMethod, boolean startAuto,
                                     boolean manualFocus, boolean showDebugInfo, int audiohz) {
        Intent intent = new Intent(context, CameraActivityAudioMix.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("type", fromType);
        intent.putExtra(URL, rtmpUrl);
        intent.putExtra(FRAME_RATE, frameRate);
        intent.putExtra(VIDEO_BITRATE, videoBitrate);
        intent.putExtra(AUDIO_BITRATE, audioBitrate);
        intent.putExtra(VIDEO_RESOLUTION, videoResolution);
        intent.putExtra(LANDSCAPE, isLandscape);
        intent.putExtra(ENCDODE_METHOD, encodeMethod);
        intent.putExtra(START_ATUO, startAuto);
        intent.putExtra(MANUAL_FOCUS, manualFocus);
        intent.putExtra(SHOW_DEBUGINFO, showDebugInfo);
        intent.putExtra("audiohz",audiohz);
        context.startActivity(intent);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.camera_activity_audio_mix);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        mCameraPreview = (GLSurfaceView) findViewById(R.id.camera_preview);
        mUrlTextView = (TextView) findViewById(R.id.url);

        layout_seek = (LinearLayout)findViewById(R.id.layout_seek);
        layout_seek.setVisibility(View.INVISIBLE);

        seek_mix = (SeekBar)findViewById(R.id.seek_mix);
        seek_mix.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                mKsyBgmPlayer.setVolume(seekBar.getProgress()/100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        mBgm = (CheckBox) findViewById(R.id.bgm);
        mEarMirror = (CheckBox) findViewById(R.id.ear_mirror);
        mMuteAudio = (CheckBox) findViewById(R.id.mute);

        audio_mix = mBgm.isChecked();
        earMirror = mEarMirror.isChecked();
        mute_audio = mMuteAudio.isChecked();

        KSYStreamerConfig.Builder builder = new KSYStreamerConfig.Builder();
        mHandler = new Handler() {

            @Override
            public void handleMessage(Message msg) {
                if (msg != null && msg.obj != null) {
                    String content = msg.obj.toString();
                    switch (msg.what) {
                        case RecorderConstants.KSYVIDEO_ENCODED_FRAMES_FAILED:
                        case RecorderConstants.KSYVIDEO_CODEC_OPEN_FAILED:
                        case RecorderConstants.KSYVIDEO_CONNECT_FAILED:
                        case RecorderConstants.KSYVIDEO_DNS_PARSE_FAILED:
                        case RecorderConstants.KSYVIDEO_RTMP_PUBLISH_FAILED:
                        case RecorderConstants.KSYVIDEO_CONNECT_BREAK:
                        case RecorderConstants.KSYVIDEO_AUDIO_INIT_FAILED:
                        case RecorderConstants.KSYVIDEO_OPEN_CAMERA_FAIL:
                        case RecorderConstants.KSYVIDEO_CAMERA_PARAMS_ERROR:
                        case RecorderConstants.KSYVIDEO_AUDIO_START_FAILED:
                            Toast.makeText(CameraActivityAudioMix.this, content,
                                    Toast.LENGTH_LONG).show();
                            chronometer.stop();
                            mShootingText.setText(START_STRING);
                            mShootingText.postInvalidate();
                            break;
                        case RecorderConstants.KSYVIDEO_OPEN_STREAM_SUCC:
                            chronometer.setBase(SystemClock.elapsedRealtime());
                            // 开始计时
                            chronometer.start();
                            mShootingText.setText(STOP_STRING);
                            mShootingText.postInvalidate();
                            beginInfoUploadTimer();
                            break;
                        case RecorderConstants.KSYVIDEO_INIT_DONE:
                            if (mShootingText != null)
                                mShootingText.setEnabled(true);
                            Toast.makeText(getApplicationContext(), "初始化完成", Toast.LENGTH_SHORT).show();
                            checkPermission();
                            if (startAuto && mStreamer.startStream()) {
                                mShootingText.setText(STOP_STRING);
                                mShootingText.postInvalidate();
                                recording = true;
                            }
                            break;
                        default:
                            Toast.makeText(CameraActivityAudioMix.this, content,
                                    Toast.LENGTH_SHORT).show();
                    }
                }
            }

        };

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            String url = bundle.getString(URL);
            if (!TextUtils.isEmpty(url)) {
                builder.setmUrl(url);
                mUrl = url;
                mUrlTextView.setText(mUrl);
            }

            int frameRate = bundle.getInt(FRAME_RATE, 0);
            if (frameRate > 0) {
                builder.setFrameRate(frameRate);
            }

            int audiohz = bundle.getInt("audiohz",0);
            builder.setSampleAudioRateInHz(audiohz);

            int videoBitrate = bundle.getInt(VIDEO_BITRATE, 0);
            if (videoBitrate > 0) {
                //设置最高码率，即目标码率
                builder.setMaxAverageVideoBitrate(videoBitrate);
                //设置最低码率
                builder.setMinAverageVideoBitrate(videoBitrate * 2 / 8);
                //设置初始码率
                builder.setInitAverageVideoBitrate(videoBitrate * 5 / 8);
            }

            int audioBitrate = bundle.getInt(AUDIO_BITRATE, 0);
            if (audioBitrate > 0) {
                builder.setAudioBitrate(audioBitrate);
            }

            int videoResolution = bundle.getInt(VIDEO_RESOLUTION, 0);
            builder.setVideoResolution(videoResolution);

            encode_method = (KSYStreamerConfig.ENCODE_METHOD) bundle.get(ENCDODE_METHOD);
            builder.setEncodeMethod(encode_method);

            builder.setSampleAudioRateInHz(44100);
            builder.setEnableStreamStatModule(true);

            landscape = bundle.getBoolean(LANDSCAPE, false);
            builder.setDefaultLandscape(landscape);

            if (landscape) {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            } else {
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }
            startAuto = bundle.getBoolean(START_ATUO, false);
            boolean focus_manual = bundle.getBoolean(MANUAL_FOCUS, false);
            builder.setManualFocus(focus_manual);
            printDebugInfo = bundle.getBoolean(SHOW_DEBUGINFO, false);

            builder.setIsSlightBeauty(false);
        }

        //可以在这里做权限检查,若没有audio和camera权限,进一步引导用户做权限设置
        checkPermission();
        mStreamer = new KSYStreamer(this);
        mStreamer.setConfig(builder.build());
        mStreamer.setDisplayPreview(mCameraPreview);
        //老的状态回调机制
        mStreamer.setOnStatusListener(mOnErrorListener);

        //新的状态回调机制
        StreamStatusEventHandler.getInstance().addOnStatusErrorListener(mOnStatusErrorListener);
        StreamStatusEventHandler.getInstance().addOnStatusInfoListener(mOnStatusInfoListener);

        mStreamer.setOnLogListener(mOnLogListener);
        mStreamer.enableDebugLog(true);
        mStreamer.setMuteAudio(mute_audio);
        mStreamer.setEnableEarMirror(earMirror);

        mBgm.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mKsyBgmPlayer = KSYBgmPlayer.getInstance();
                    mKsyBgmPlayer.setOnBgmPlayerListener(mBgmListener);
                    mKsyBgmPlayer.setVolume(bgmVolume);
                    mKsyBgmPlayer.setMute(earMirror);
                    mStreamer.setBgmPlayer(mKsyBgmPlayer);
                    mStreamer.startMixMusic(mBgmPath, true);
                    mStreamer.setHeadsetPlugged(true);
                    layout_seek.setVisibility(View.VISIBLE);
                } else {
                    layout_seek.setVisibility(View.INVISIBLE);
                    mStreamer.stopMixMusic();
                }
            }
        });



        mEarMirror.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                earMirror = isChecked;
                if (mKsyBgmPlayer != null) {
                    mKsyBgmPlayer.setMute(isChecked);
                }
                mStreamer.setEnableEarMirror(isChecked);
            }
        });

        mMuteAudio.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mStreamer.setMuteAudio(isChecked);
            }
        });

        mShootingText = (TextView) findViewById(R.id.click_to_shoot);
        mShootingText.setClickable(true);
        mShootingText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (recording) {
                    if (mStreamer.stopStream()) {
                        chronometer.stop();
                        mShootingText.setText(START_STRING);
                        mShootingText.postInvalidate();
                        recording = false;
                    } else {
                        Log.e(TAG, "操作太频繁");
                    }
                } else {
                    checkPermission();
                    if (mStreamer.startStream()) {
                        mShootingText.setText(STOP_STRING);
                        mShootingText.postInvalidate();
                        recording = true;
                    } else {
                        Log.e(TAG, "操作太频繁");
                    }
                }

            }
        });

        if (startAuto) {
            mShootingText.setEnabled(false);
        }

        mDeleteView = findViewById(R.id.backoff);
        mDeleteView.setOnClickListener(mObserverButton);
        mDeleteView.setEnabled(true);

        mSwitchCameraView = findViewById(R.id.switch_cam);
        mSwitchCameraView.setOnClickListener(mObserverButton);
        mSwitchCameraView.setEnabled(true);

        mFlashView = findViewById(R.id.flash);
        mFlashView.setOnClickListener(mObserverButton);
        mFlashView.setEnabled(true);

        chronometer = (Chronometer) this.findViewById(R.id.chronometer);
        mDebugInfoTextView = (TextView) this.findViewById(R.id.debuginfo);
    }

    private void beginInfoUploadTimer() {
        if (printDebugInfo && timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    updateDebugInfo();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mDebugInfoTextView.setText(mDebugInfo);
                        }
                    });
                }
            }, 100, 3000);
        }
    }


    //update debug info
    private void updateDebugInfo() {
        if (mStreamer == null) return;
        mDebugInfo = String.format("RtmpHostIP()=%s DroppedFrameCount()=%d \n " +
                        "ConnectTime()=%d DnsParseTime()=%d \n " +
                        "UploadedKB()=%d EncodedFrames()=%d \n" +
                        "CurrentBitrate=%f Version()=%s",
                mStreamer.getRtmpHostIP(), mStreamer.getDroppedFrameCount(),
                mStreamer.getConnectTime(), mStreamer.getDnsParseTime(),
                mStreamer.getUploadedKBytes(), mStreamer.getEncodedFrames(),
                mStreamer.getCurrentBitrate(), mStreamer.getVersion());
    }


    @Override
    public void onResume() {
        super.onResume();
        //可以在这里做权限检查,若没有audio和camera权限,进一步引导用户做权限设置
        checkPermission();
        if (mKsyBgmPlayer != null) {
            mKsyBgmPlayer.resume();
        }
        mStreamer.onResume();
        mAcitivityResumed = true;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mKsyBgmPlayer != null) {
            mKsyBgmPlayer.pause();
        }
        mStreamer.onPause();
        mAcitivityResumed = false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                new AlertDialog.Builder(CameraActivityAudioMix.this).setCancelable(true)
                        .setTitle("结束直播?")
                        .setNegativeButton("取消", new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {

                            }
                        })
                        .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface arg0, int arg1) {
                                mStreamer.stopStream(true);
                                chronometer.stop();
                                recording = false;
                                CameraActivityAudioMix.this.finish();
                            }
                        }).show();
                break;

            default:
                break;
        }
        return true;
    }

    //推流失败,需要退出重新启动推流,建议做log输出,方便快速定位问题
    public StreamStatusEventHandler.OnStatusErrorListener mOnStatusErrorListener = new StreamStatusEventHandler.OnStatusErrorListener() {
        @Override
        public void onError(int what, int arg1, int arg2, String msg) {
            switch (what) {
                case RecorderConstants.KSYVIDEO_ENCODED_FRAMES_FAILED:
                    //视频编码失败,发生在软解,会停止推流
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_ENCODED_FRAMES_FAILED");
                    break;
                case RecorderConstants.KSYVIDEO_CODEC_OPEN_FAILED:
                    //编码器初始化失败,发生在推流开始时
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_CODEC_OPEN_FAILED");
                    break;
                case RecorderConstants.KSYVIDEO_CONNECT_FAILED:
                    //打开编码器的输入输出文件失败,发生在推流开始时,会停止推流
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_CONNECT_FAILED");
                    break;
                case RecorderConstants.KSYVIDEO_DNS_PARSE_FAILED:
                    //打开编码器的输入输出文件失败,发生在推流开始时,会停止推流
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_CONNECT_FAILED");
                    break;
                case RecorderConstants.KSYVIDEO_RTMP_PUBLISH_FAILED:
                    //打开编码器的输入输出文件失败,发生在推流开始时,会停止推流
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_CONNECT_FAILED");
                    break;
                case RecorderConstants.KSYVIDEO_CONNECT_BREAK:
                    //写入一个音视频文件失败
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_CONNECT_BREAK");
                    break;
                case RecorderConstants.KSYVIDEO_AUDIO_INIT_FAILED:
                    //软编,音频初始化失败
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_AUDIO_INIT_FAILED");
                    break;
                case RecorderConstants.KSYVIDEO_AUDIO_COVERT_FAILED:
                    //软编,音频转码失败
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_AUDIO_COVERT_FAILED");
                    break;
                case RecorderConstants.KSYVIDEO_OPEN_CAMERA_FAIL:
                    //openCamera失败
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_OPEN_CAMERA_FAIL");
                    break;
                case RecorderConstants.KSYVIDEO_CAMERA_PARAMS_ERROR:
                    //获取不到camera参数,android 6.0 以下没有camera权限时可能发生
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_CAMERA_PARAMS_ERROR");
                    break;
                case RecorderConstants.KSYVIDEO_AUDIO_START_FAILED:
                    //audio startRecord 失败
                    Log.e(TAG, "the streaming stopped because KSYVIDEO_AUDIO_START_FAILED");
                    break;
                default:
                    break;
            }

//            if (mHandler != null) {
//                mHandler.obtainMessage(what, msg).sendToTarget();
//            }
        }
    };

    public StreamStatusEventHandler.OnStatusInfoListener mOnStatusInfoListener = new StreamStatusEventHandler.OnStatusInfoListener() {
        @Override
        public void onInfo(int what, int arg1, int arg2, String msg) {
            switch (what) {
                case RecorderConstants.KSYVIDEO_OPEN_STREAM_SUCC:
                    //推流成功
                    Log.d("TAG", "KSYVIDEO_OPEN_STREAM_SUCC");
//                    mHandler.obtainMessage(what, "start stream succ")
//                            .sendToTarget();
                    break;
                case RecorderConstants.KSYVIDEO_FRAME_DATA_SEND_SLOW:
                    //网络状况不佳
//                    if (mHandler != null) {
//                        mHandler.obtainMessage(what, "network not good").sendToTarget();
//                    }
                    break;
                case RecorderConstants.KSYVIDEO_EST_BW_RAISE:
                    //码率上调
                    Log.d("TAG", "KSYVIDEO_EST_BW_RAISE");
                    break;
                case RecorderConstants.KSYVIDEO_EST_BW_DROP:
                    //码率下调
                    Log.d("TAG", "KSYVIDEO_EST_BW_DROP");
                    break;
                case RecorderConstants.KSYVIDEO_INIT_DONE:
                    //video preview init done
                    Log.d("TAG", "KSYVIDEO_INIT_DONE");
                    break;
                case RecorderConstants.KSYVIDEO_PIP_EXCEPTION:
                    Log.d("TAG", "KSYVIDEO_PIP_EXCEPTION");
//                    mHandler.obtainMessage(what, "pip exception")
//                            .sendToTarget();
                    break;
                case RecorderConstants.KSYVIDEO_RENDER_EXCEPTION:
                    Log.d("TAG", "KSYVIDEO_RENDER_EXCEPTION");
//                    mHandler.obtainMessage(what, "renderer exception")
//                            .sendToTarget();
                    break;
                default:
                    break;

            }
        }
    };

    public OnStatusListener mOnErrorListener = new OnStatusListener() {
        @Override
        public void onStatus(int what, int arg1, int arg2, String msg) {
            // msg may be null
            switch (what) {
                case RecorderConstants.KSYVIDEO_OPEN_STREAM_SUCC:
                    // 推流成功
                    Log.d("TAG", "KSYVIDEO_OPEN_STREAM_SUCC");
                    mHandler.obtainMessage(what, "start stream succ")
                            .sendToTarget();
                    break;
                case RecorderConstants.KSYVIDEO_ENCODED_FRAMES_FAILED:
                    //编码失败
                    Log.e(TAG, "---------KSYVIDEO_ENCODED_FRAMES_FAILED");
                    break;
                case RecorderConstants.KSYVIDEO_WLD_UPLOAD:
                    break;
                case RecorderConstants.KSYVIDEO_FRAME_DATA_SEND_SLOW:
                    //网络状况不佳
                    if (mHandler != null) {
                        mHandler.obtainMessage(what, "network not good").sendToTarget();
                    }
                    break;
                case RecorderConstants.KSYVIDEO_EST_BW_DROP:
                    //编码码率下降状态通知
                    break;
                case RecorderConstants.KSYVIDEO_EST_BW_RAISE:
                    //编码码率上升状态通知
                    break;
                case RecorderConstants.KSYVIDEO_AUDIO_INIT_FAILED:
                    Log.e("CameraActivityAudioMix", "init audio failed");
                    //音频录制初始化失败回调
                    break;
                case RecorderConstants.KSYVIDEO_INIT_DONE:
                    mHandler.obtainMessage(what, "init done")
                            .sendToTarget();
                    break;
                case RecorderConstants.KSYVIDEO_PIP_EXCEPTION:
                    mHandler.obtainMessage(what, "pip exception")
                            .sendToTarget();
                    break;
                case RecorderConstants.KSYVIDEO_RENDER_EXCEPTION:
                    mHandler.obtainMessage(what, "renderer exception")
                            .sendToTarget();
                    break;
                case RecorderConstants.KSYVIDEO_AUDIO_START_FAILED:
                    Log.e("CameraActivityAudioMix", "-------audio start failed");
                    break;
                case RecorderConstants.KSYVIDEO_CAMERA_PARAMS_ERROR:
                    Log.e("CameraActivityAudioMix", "-------camera param is null");
                    break;
                case RecorderConstants.KSYVIDEO_OPEN_CAMERA_FAIL:
                    Log.e("CameraActivityAudioMix", "-------open camera failed");
                    break;
                default:
                    if (msg != null) {
                        // 可以在这里处理断网重连的逻辑
                        if (TextUtils.isEmpty(mUrl)) {
                            mStreamer
                                    .updateUrl("rtmp://test.uplive.ksyun.com/live/androidtest");
                        } else {
                            mStreamer.updateUrl(mUrl);
                        }
                        if (!executorService.isShutdown()) {
                            executorService.submit(new Runnable() {

                                @Override
                                public void run() {
                                    boolean needReconnect = true;
                                    try {
                                        while (needReconnect) {
                                            Thread.sleep(3000);
                                            //只在Activity对用户可见时重连
                                            if (mAcitivityResumed) {
                                                if (mStreamer.startStream()) {
                                                    recording = true;
                                                    needReconnect = false;
                                                }
                                            }
                                        }
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }

                            });
                        }
                    }
                    if (mHandler != null) {
                        mHandler.obtainMessage(what, msg).sendToTarget();
                    }
            }
        }

    };

    private KSYBgmPlayer.OnBgmPlayerListener mBgmListener = new KSYBgmPlayer.OnBgmPlayerListener() {
        @Override
        public void onCompleted() {
            Log.d(TAG, "End of the currently playing music");
        }

        @Override
        public void onError(int err) {
            Log.e(TAG, "onMusicError: " + err);
        }
    };

    private OnLogEventListener mOnLogListener = new OnLogEventListener() {
        @Override
        public void onLogEvent(StringBuffer singleLogContent) {
            Log.d(TAG, "***onLogEvent : " + singleLogContent.toString());
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        //新的状态回调机制
        StreamStatusEventHandler.getInstance().removeStatusErrorListener(mOnStatusErrorListener);
        StreamStatusEventHandler.getInstance().removeStatusInfoListener(mOnStatusInfoListener);

        if (mKsyBgmPlayer != null) {
            mKsyBgmPlayer.release();
            mKsyBgmPlayer = null;
        }

        mStreamer.onDestroy();
        executorService.shutdownNow();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
            mHandler = null;
        }
        if (timer != null) {
            timer.cancel();
        }
    }

    private boolean clearState() {
        if (clearBackoff()) {
            return true;
        }
        return false;
    }

    private long lastClickTime = 0;

    private void onSwitchCamClick() {
        long curTime = System.currentTimeMillis();
        if (curTime - lastClickTime < 1000) {
            return;
        }
        lastClickTime = curTime;

        if (clearState()) {
            return;
        }
        mStreamer.switchCamera();

    }

    private void onFlashClick() {
        if (mStreamer.isFrontCamera())
            isFlashOpened = true;

        if (isFlashOpened) {
            mStreamer.toggleTorch(false);
            isFlashOpened = false;
        } else {
            mStreamer.toggleTorch(true);
            isFlashOpened = true;
        }
    }

    private boolean clearBackoff() {
        if (mDeleteView.isSelected()) {
            mDeleteView.setSelected(false);
            return true;
        }
        return false;
    }

    private void onBackoffClick() {

        new AlertDialog.Builder(CameraActivityAudioMix.this).setCancelable(true)
                .setTitle("结束直播?")
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {

                    }
                })
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface arg0, int arg1) {
                        mStreamer.stopStream(true);
                        chronometer.stop();
                        recording = false;
                        CameraActivityAudioMix.this.finish();
                    }
                }).show();
    }

    private class ButtonObserver implements View.OnClickListener {
        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.switch_cam:
                    onSwitchCamClick();
                    break;
                case R.id.backoff:
                    onBackoffClick();
                    break;
                case R.id.flash:
                    onFlashClick();
                    break;
                default:
                    break;
            }
        }
    }

    private boolean checkPermission() {
        try {
            int pRecordAudio = PermissionChecker.checkCallingOrSelfPermission(this, "android.permission.RECORD_AUDIO");
            int pCamera = PermissionChecker.checkCallingOrSelfPermission(this, "android.permission.CAMERA");

            if (pRecordAudio != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "do not have AudioRecord permission, please check");
                Toast.makeText(this, "do not have AudioRecord permission, please check", Toast.LENGTH_LONG).show();
                return false;
            }
            if (pCamera != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "do not have CAMERA permission, please check");
                Toast.makeText(this, "do not have CAMERA permission, please check", Toast.LENGTH_LONG).show();
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

}
