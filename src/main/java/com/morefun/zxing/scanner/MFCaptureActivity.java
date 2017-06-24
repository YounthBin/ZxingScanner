package com.morefun.zxing.scanner;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.support.annotation.LayoutRes;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.morefun.zxing.scanner.camera.CameraManager;
import com.morefun.zxing.scanner.decoding.CaptureActivityHandler;
import com.morefun.zxing.scanner.decoding.InactivityTimer;
import com.morefun.zxing.scanner.view.ViewfinderView;

import java.io.IOException;
import java.util.Vector;

/**
 * Initial the camera 扫描中界面
 *
 * @author YounthBin
 */
public abstract class MFCaptureActivity extends Activity implements Callback {
    private final float BEEP_VOLUME = 0.10f;
    private final String TAG = MFCaptureActivity.class.getSimpleName();
    private CaptureActivityHandler handler;
    private ViewfinderView viewfinderView;
    private boolean hasSurface;
    private Vector<BarcodeFormat> decodeFormats;
    private String characterSet;
    private InactivityTimer inactivityTimer;
    private MediaPlayer mediaPlayer;
    private boolean playBeep;
    private boolean vibrate;
    protected LinearLayout mButtonBack; // 返回btn
    private TextView textTitle;

    private String result; // 图片上传 返回值
    private String resultString;


    private MyOrientationDetector myOrientationDetector;
    private int status = 1;


    /**
     * 开启闪光灯按钮
     */
    private CheckBox btnFlashlight;
    /**
     * 警示
     */
    private CheckBox btnWarning;
    /**
     * 全屏扫描、小窗口扫描切换按钮
     */
    private CheckBox btnScanSize;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        onSetContentViewBefore();
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//		setContentView(R.layout.lib_scan_capture);
        setContentView(setLayoutResID());
        SharedPreferences sharedPreferences = getSharedPreferences(
                CameraManager.CAMERA_MANAGER, MODE_PRIVATE);

        btnFlashlight = (CheckBox) findViewById(R.id.btnFlashlight);
        btnWarning = (CheckBox) findViewById(R.id.btnWarning);
        btnScanSize = (CheckBox) findViewById(R.id.btnScanSize);
        if (sharedPreferences.getBoolean(CameraManager.KEY_FRONT_LIGHT, false)) {
            btnFlashlight.setChecked(true);
        } else {
            btnFlashlight.setChecked(false);
        }
        if (sharedPreferences.getBoolean(CameraManager.KEY_FRONT_SCANMODE, false)) {
            btnScanSize.setChecked(true);
        } else {
            btnScanSize.setChecked(false);
        }
        btnFlashlight.setOnClickListener(onClickListener);
//		btnWarning.setOnClickListener(onClickListener);
        btnScanSize.setOnClickListener(onClickListener);
        myOrientationDetector = new MyOrientationDetector(getApplication());

        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 当前为横屏， 在此处添加额外的处理代码
            ViewfinderView.isHorizontal = true;
            ViewfinderView.isFirst = false;
            // a = 2;
        } else if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            ViewfinderView.isHorizontal = false;
            ViewfinderView.isFirst = false;
            // a = 1;
            // 当前为竖屏， 在此处添加额外的处理代码
        }

        CameraManager.init(getApplication(), MFCaptureActivity.this);
        viewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
        // surfaceView = (SurfaceView) findViewById(R.id.preview_view);

        hasSurface = false;
        inactivityTimer = new InactivityTimer(this);

    }

    OnClickListener onClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {

            if (v.getId() == R.id.btnFlashlight) {
                if (btnFlashlight.isChecked()) {
                    CameraManager.get().openLight();
                } else {
                    CameraManager.get().offLight();
                }
            } else if (v.getId() == R.id.btnScanSize) {
                if (btnScanSize.isChecked()) {
//			        ToastUtils.show(MFCaptureActivity.this, "切换大屏");
                    viewfinderView.needChangePre = false;
                } else {
//			        ToastUtils.show(MFCaptureActivity.this, "切换小屏");
//			        CameraManager.get().changeScanScreen = true;
                    viewfinderView.needChangePre = true;
//			        CameraManager.get().setFlag(true);
//			        change(); 
                }
            }
        }
    };

    public abstract
    @LayoutRes
    int setLayoutResID();

    public abstract void onSetContentViewBefore();

    @Override
    protected void onResume() {
        super.onResume();
        init();
    }

    private void init() {
        myOrientationDetector.enable();
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        myOrientationDetector.disable();
        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onStop() {
        super.onStop();
        CameraManager.get().closeDriver();
    }

    @Override
    protected void onDestroy() {
        inactivityTimer.shutdown();
        super.onDestroy();
    }

    /**
     * 处理扫描结果
     *
     * @param result
     * @param barcode
     */
    public void handleDecode(Result result, Bitmap barcode) {
        inactivityTimer.onActivity();
        playBeepSoundAndVibrate();

        ScanCodebyCamera(result, barcode);
    }

    public abstract void ScanCodebyCamera(Result result, Bitmap barcode);


    private void initCamera(SurfaceHolder surfaceHolder) {
        try {
            CameraManager.get().openDriver(surfaceHolder);
        } catch (IOException ioe) {
            return;
        } catch (RuntimeException e) {
            return;
        }
        if (handler == null) {
            handler = new CaptureActivityHandler(this, decodeFormats,
                    characterSet);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (!hasSurface) {
            hasSurface = true;
            initCamera(holder);
        }

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        hasSurface = false;

    }

    public ViewfinderView getViewfinderView() {
        return viewfinderView;
    }

    public Handler getHandler() {
        return handler;
    }

    public void drawViewfinder() {
        viewfinderView.drawViewfinder();

    }

    private void initBeepSound() {
        if (playBeep && mediaPlayer == null) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mediaPlayer.setOnCompletionListener(beepListener);

            AssetFileDescriptor file = getResources().openRawResourceFd(
                    R.raw.beep);
            try {
                mediaPlayer.setDataSource(file.getFileDescriptor(),
                        file.getStartOffset(), file.getLength());
                file.close();
                mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
                mediaPlayer.prepare();
            } catch (IOException e) {
                mediaPlayer = null;
            }
        }
    }

    private static final long VIBRATE_DURATION = 200L;

    private void playBeepSoundAndVibrate() {
        if (playBeep && mediaPlayer != null) {
            mediaPlayer.start();
        }
        if (vibrate) {
            Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            vibrator.vibrate(VIBRATE_DURATION);
        }
    }

    /**
     * When the beep has finished playing, rewind to queue up another one.
     */
    private final OnCompletionListener beepListener = new OnCompletionListener() {
        public void onCompletion(MediaPlayer mediaPlayer) {
            mediaPlayer.seekTo(0);
        }

    };

    public class MyOrientationDetector extends OrientationEventListener {
        public MyOrientationDetector(Context context) {
            super(context);
        }

        // @Override
        public void onOrientationChanged(int orientation) {

            int rotation = MFCaptureActivity.this.getWindowManager()
                    .getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_0: // 竖屏
                    if (status == 1) {
                        return;
                    }
                    status = 1;
                    change();
                    return;
                case Surface.ROTATION_90: // 左横屏
                    if (status == 2) {
                        return;
                    }
                    status = 2;
                    change();

                    return;
                case Surface.ROTATION_180:

                    return;
                case Surface.ROTATION_270:
                    if (status == 3) {
                        return;
                    }
                    status = 3;
                    change();
                    // 右横屏

                    return;
            }

        }
    }

    public void change() {

        if (handler != null) {
            handler.quitSynchronously();
            handler = null;
        }
        viewfinderView.drawViewfinder();
        CameraManager.get().closeDriver();
        if (status == 2 || status == 3) {
            ViewfinderView.isHorizontal = true;
            ViewfinderView.isFirst = false;
        }
        if (status == 1) {
            ViewfinderView.isHorizontal = false;
            ViewfinderView.isFirst = false;
        }

        CameraManager.init(MFCaptureActivity.this.getApplication(),
                MFCaptureActivity.this);
        SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        if (hasSurface) {
            initCamera(surfaceHolder);
        } else {
            surfaceHolder.addCallback(MFCaptureActivity.this);
            surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }
        decodeFormats = null;
        characterSet = null;

        playBeep = true;
        AudioManager audioService = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            playBeep = false;
        }
        initBeepSound();
        vibrate = true;

    }

    public String setTitle() {
        return "条码扫描";
    }


    /**
     * 调用此方法，实现连续扫描
     */
    public void restartScan() {

        if (handler != null) {
            SystemClock.sleep(500);
            handler.restartPreviewAndDecode();
        }
    }

}
