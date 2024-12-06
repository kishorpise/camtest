package com.camtest;

import static android.content.Context.CAMERA_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;

import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.Collections;


public class xCamView extends FrameLayout {
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // Time in milliseconds
    private static final long MIN_ELAPSED_TIME = 7000;
    private static final long LONG_PRESS_THRESHOLD = 1000; // 1 second
    private static final long DOUBLE_TAP_THRESHOLD = 300; // 300 milliseconds
    private static final int REQUEST_CODE_PERMISSIONS = 1;
    static int numberOfCameras = 0;
    static int currentCamera = 0;

    static String tag = "xcamview";
    private final Context context;
    private Size previewSize;
    private TextureView textureView;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                startPreview();
                // startRecording();
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera.close();
            }
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                camera.close();
            }
            cameraDevice = null;

        }
    };
    private MediaRecorder mediaRecorder;
    private final String TAG = "Camera View";
    private final long lastClickTime = 0;


    private boolean isRecording = false;

    private CameraManager cameraManager;
    private TimeStampOverlayView overlayView;
    private final long lastExecutionTime = 0; // Variable to store the last execution time
    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {


            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

           // configureTransform(width, height);

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
            overlayView.drawStamp();
        }
    };
    private final String[] requiredPermissions = {android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private File outputFile;
    private final String VIDEO_FOLDER = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/Recordings/";
    private long lastTapTime = 0;
    private long startTime;
    private boolean isLongPress = false;
    private boolean isMultiTouch = false;
    private Activity callingAct;

    public xCamView(Context context, Activity callingAct) {
        super(context);
        this.context = context;
        this.callingAct = callingAct;
        init();
    }

    public xCamView(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        init();
    }

    public xCamView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        this.context = context;
        init();
    }




    private static Rect mapFaceToPreview(PointF midpoint, float eyesDistance, int bitmapWidth, int bitmapHeight, int viewHeight, int viewWidth) {

        // Calculate scaling factors
        float scaleX = (float) viewWidth / bitmapWidth;
        float scaleY = (float) viewHeight / bitmapHeight;

        // Scale the face coordinates
        int left = (int) ((midpoint.x - eyesDistance) * scaleX);
        int top = (int) ((midpoint.y - eyesDistance) * scaleY);
        int right = (int) ((midpoint.x + eyesDistance) * scaleX);
        int bottom = (int) ((midpoint.y + eyesDistance) * scaleY);

        // Return the mapped face rectangle
        return new Rect(left, top, right, bottom);
    }

    private boolean hasPermissions() {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {

        if (ActivityCompat.shouldShowRequestPermissionRationale(callingAct, android.Manifest.permission.READ_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(callingAct, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {

            // If the user has previously denied permission, show an explanation
            Toast.makeText(callingAct, "Storage permissions are required to save and load videos.", Toast.LENGTH_LONG).show();
        }

        // Request permissions at runtime
        ActivityCompat.requestPermissions(callingAct, requiredPermissions, REQUEST_CODE_PERMISSIONS);


    }

    @SuppressLint("ClickableViewAccessibility")
    private void init() {

        if (context instanceof Activity) {
            callingAct = (Activity) context;
        }

        LayoutInflater.from(context).inflate(R.layout.xcamview, this);

        if (!hasPermissions()) {

            requestPermissions();
        }

        File videoDir = new File(VIDEO_FOLDER);
        if (!videoDir.exists()) {
            videoDir.mkdirs();
        }
        textureView = findViewById(R.id.textureView);
        overlayView = findViewById(R.id.overlayView);
        initializeViews();


        this.setOnTouchListener((v, event) -> {

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    // Handle Long Press Detection
                    startTime = System.currentTimeMillis();
                    isLongPress = false;

                    // Handle Double Tap Detection
                    long currentTime = System.currentTimeMillis();
                    if (currentTime - lastTapTime < DOUBLE_TAP_THRESHOLD) {
                        // It's a double tap
                        onDoubleTap();
                    }
                    lastTapTime = currentTime;

                    // Handle Multi-Touch Detection
                    isMultiTouch = event.getPointerCount() > 1;

                    return true;
                case MotionEvent.ACTION_UP:
                    // Check if it's a Long Press
                    long duration = System.currentTimeMillis() - startTime;
                    if (duration >= LONG_PRESS_THRESHOLD && !isLongPress) {
                        isLongPress = true;
                        ButtonPress();
                    }

                    // Handle end of touch, also checking if multi-touch ends
                    if (isMultiTouch) {
                        onMultiTouchEnd();
                    }

                    return true;

                case MotionEvent.ACTION_MOVE:
                    // Optionally, handle movements if needed
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    // Handle additional fingers for multi-touch
                    if (event.getPointerCount() > 1) {
                        isMultiTouch = true;
                    }
                    return true;

                case MotionEvent.ACTION_POINTER_UP:
                    // Handle when additional fingers are lifted
                    if (event.getPointerCount() <= 1) {
                        isMultiTouch = false;
                    }
                    return true;

                default:
                    return false;
            }


        });


    }



    public void ButtonPress() {
        if (isRecording) stopRecording();
        else startRecording();
    }


    private void onDoubleTap() {
        swapCamera();
    }

    private void onMultiTouchEnd() {
        Toast.makeText(context, "Multi-Touch Ended", Toast.LENGTH_SHORT).show();
    }

    private void initializeViews() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
            //getCameraInfo();
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }

    }

    private void swapCamera() {

        numberOfCameras = Camera.getNumberOfCameras();

        if (isRecording) {
            stopRecording();
        }
        if ((numberOfCameras - 1) > currentCamera) currentCamera++;
        else currentCamera = 0;

        if (cameraDevice != null) {
            closeCamera(); // Close the current camera before opening a new one
        }
        openCamera();
    }



    private void closeCamera() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (cameraCaptureSession != null) {

                cameraCaptureSession.close();

                cameraCaptureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    }

    private void openCamera() {
        CameraManager cameraManager = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cameraManager = (CameraManager) context.getSystemService(CAMERA_SERVICE);
            try {

                String cameraId = cameraManager.getCameraIdList()[currentCamera];

                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size[] outputSizes = map.getOutputSizes(SurfaceTexture.class);
                if (outputSizes.length > 0) {
                    previewSize = new Size(outputSizes[0].getWidth(), outputSizes[0].getHeight());

                    int newheight = (textureView.getWidth() * outputSizes[0].getHeight() / outputSizes[0].getWidth());
                    setTextureViewHeight(newheight);
                }

                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

                    return;
                }
                cameraManager.openCamera(cameraId, stateCallback, null);

            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void setTextureViewHeight(int heightInPixels) {
        ViewGroup.LayoutParams layoutParams = textureView.getLayoutParams();
        layoutParams.height = heightInPixels;
        textureView.setLayoutParams(layoutParams);
    }

    private void configureTransform(int width, int height) {
        Matrix matrix = new Matrix();
        float scale = (float) width / previewSize.getWidth();
        matrix.setScale(scale, scale);
        textureView.setTransform(matrix);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startPreview() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture == null) return;

            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());

            Surface surface = new Surface(surfaceTexture);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                captureRequestBuilder.addTarget(surface);

                cameraDevice.createCaptureSession(Collections.singletonList(surface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        cameraCaptureSession = session;
                        try {
                            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Toast.makeText(context, "COnfiguration Failed", Toast.LENGTH_SHORT).show();
                    }
                }, null);

            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (isRecording) {
            stopRecording();
        }
    }

    private void startRecordingW() {
        Toast.makeText(context, "Recording Started !", Toast.LENGTH_SHORT).show();
        try {
            mediaRecorder = new MediaRecorder();

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            mediaRecorder.setVideoFrameRate(30);

            outputFile = new File(VIDEO_FOLDER, "video_chunk_" + System.currentTimeMillis() + ".mp4");
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            SurfaceTexture texture = textureView.getSurfaceTexture();

            if (texture == null) {
                throw new IllegalStateException("SurfaceTexture is not ready");
            }
            Surface recorderSurface = new Surface(texture);
            mediaRecorder.setPreviewDisplay(recorderSurface);

            mediaRecorder.setVideoSize(previewSize.getWidth(), previewSize.getHeight());

            // mediaRecorder.setCaptureRate(30);
            mediaRecorder.prepare();
            try {
                mediaRecorder.start();
            } catch (IllegalStateException ex) {
                Log.d(TAG, "startRecording: " + ex.getMessage());
            }
            isRecording = true;


            new Thread(() -> {
                try {
                    //Thread.sleep(300000); // 5 minutes
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                stopRecording();
                startRecording(); // Start a new recording chunk
            }).start();
        } catch (IllegalStateException | IllegalArgumentException | UnknownError | IOException ex) {
            ex.printStackTrace();
            stopRecording();
        }
    }




    private void startRecording (){
        try {
            mediaRecorder = new MediaRecorder();

            //mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H263);

            //mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            mediaRecorder.setVideoFrameRate(30);

            outputFile = new File(VIDEO_FOLDER, "video_chunk_" + System.currentTimeMillis() + ".mp4");
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            SurfaceTexture texture = textureView.getSurfaceTexture();

            if (texture == null) {
                throw new IllegalStateException("SurfaceTexture is not ready");
            }
            Surface recorderSurface = new Surface(texture);
            mediaRecorder.setPreviewDisplay(recorderSurface);
            mediaRecorder.setVideoSize(previewSize.getWidth(), previewSize.getHeight());

            // mediaRecorder.setCaptureRate(30);
            mediaRecorder.prepare();
            try {
                mediaRecorder.start();
            } catch (IllegalStateException ex) {
                Log.d(TAG, "startRecording: " + ex.getMessage());
            }
            isRecording = true;


            new Thread(() -> {
                try {
                    //Thread.sleep(300000); // 5 minutes
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                stopRecording();
                startRecording();
            }).start();
        } catch (IllegalStateException | IOException | UnknownError | IllegalArgumentException ex) {
            ex.printStackTrace();
            stopRecording();
        }
    }


    private void stopRecording() {

        try {


            if (mediaRecorder != null) {
                mediaRecorder.stop();

                mediaRecorder.release();
                mediaRecorder = null;
            }
        } catch (Exception e) {
            Log.d(TAG, "stopRecording: " + e.getMessage());
        } finally {
            isRecording = false;
        }

    }
}
