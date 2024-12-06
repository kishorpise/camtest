package com.camtest;

import static android.content.Context.CAMERA_SERVICE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.media.FaceDetector;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;


import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;


import android.util.Size;
public class xCamView extends FrameLayout  {
    private static final long DOUBLE_CLICK_TIME_DELTA = 300; // Time in milliseconds
    private static final long MIN_ELAPSED_TIME = 7000;
    private static final long LONG_PRESS_THRESHOLD = 1000; // 1 second
    private static final long DOUBLE_TAP_THRESHOLD = 300; // 300 milliseconds
    static int numberOfCameras = 0;
    static int currentCamera = 0;
    static boolean isHeadUnit;

    static String tag = "xcamview";
    private final Context context;

    private Size previewSize ;

    private TextureView textureView;
    private CameraDevice cameraDevice;

    private CameraCaptureSession cameraCaptureSession;
    private CaptureRequest.Builder captureRequestBuilder;

    private MediaRecorder mediaRecorder;
    private String TAG = "Camera View";
    private long lastClickTime = 0;
    private Surface previewSurface;
    private Surface recordingSurface;
    private boolean isRecording = false;
    private Handler backgroundHandler;
    private CameraManager cameraManager;
    private FaceDetectionOverlayView overlayView;
    private long lastExecutionTime = 0; // Variable to store the last execution time
    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {



                    openCamera();
                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {



                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                    if (!isHeadUnit)
                        detectFaces();
                }
            };
    private File outputFile;
    private String VIDEO_FOLDER = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS) + "/Recordings/";
    private long lastTapTime = 0;
    private long startTime;
    private boolean isLongPress = false;
    private boolean isMultiTouch = false;

    public xCamView(Context context, Activity callingAct) {
        super(context);
        this.context = context;
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

    public static void getCameraInfo() {

        try {

            numberOfCameras = Camera.getNumberOfCameras();
            // Loop through each camera
            for (int i = 0; i < numberOfCameras; i++) {
                Camera usbcamera = null;
                Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                android.hardware.Camera.getCameraInfo(i, cameraInfo);
                String facing = (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) ? "Front" : "Back";
                System.out.println("Camera ID: " + i);
                System.out.println("Facing: " + facing);
                System.out.println("Orientation: " + cameraInfo.orientation);
                Camera camera = Camera.open(i);
                Camera.Parameters params = camera.getParameters();
                List<Camera.Size> previewSizes = params.getSupportedPreviewSizes();
                System.out.println("Supported Preview Sizes:");
                for (Camera.Size size : previewSizes) {
                    System.out.println(size.width + "x" + size.height);
                }

                List<Camera.Size> pictureSizes = params.getSupportedPictureSizes();
                System.out.println("Supported Picture Sizes:");
                for (Camera.Size size : pictureSizes) {
                    System.out.println(size.width + "x" + size.height);
                }

                // Release the camera after use
                camera.release();
            }
        } catch (Exception e) {
            isHeadUnit = true;
            Log.d(tag, "getCameraInfo: " + e.getMessage());
        }

    }

    @SuppressLint("ClickableViewAccessibility")
    private void init() {
        LayoutInflater.from(context).inflate(R.layout.xcamview, this);

        File videoDir = new File(VIDEO_FOLDER);
        if (!videoDir.exists()) {
            videoDir.mkdirs();
        }
        textureView = findViewById(R.id.textureView);
        overlayView = findViewById(R.id.overlayView);
        initializeViews();


        this.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {

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
                        if (event.getPointerCount() > 1) {
                            isMultiTouch = true;
                        } else {
                            isMultiTouch = false;
                        }

                        return true;
                    case MotionEvent.ACTION_UP:
                        // Check if it's a Long Press
                        long duration = System.currentTimeMillis() - startTime;
                        if (duration >= LONG_PRESS_THRESHOLD && !isLongPress) {
                            isLongPress = true;
                            onLongPress();
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


            }
        });


    }

    private void onLongPress() {
        // startRecording();

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
        if ((numberOfCameras - 1) > currentCamera)
            currentCamera++;
        else
            currentCamera = 0;

        if (cameraDevice != null) {
            closeCamera(); // Close the current camera before opening a new one
        }
        openCamera();
    }

    private void detectFaces() {

        long currentTime = SystemClock.elapsedRealtime();
        long delta = currentTime - lastExecutionTime;
        if (delta <= MIN_ELAPSED_TIME) {

            return;
        }
        lastExecutionTime = currentTime;

        // Convert TextureView frame into Bitmap
        Bitmap bitmap = textureView.getBitmap(640, 480);
        Rect faceRect = getFacerecfromBitmap(bitmap, 5, textureView.getHeight(), textureView.getWidth());
        overlayView.drawFace(faceRect);

    }



    public static Rect getFacerecfromBitmap(Bitmap bitmap, int maxfaces , int th, int tw) {

        if (bitmap == null) return null;
        Bitmap convertedBitmap = bitmap.copy(Bitmap.Config.RGB_565, true);

        // Initialize FaceDetector
        FaceDetector faceDetector = new FaceDetector(convertedBitmap.getWidth(), convertedBitmap.getHeight(), maxfaces);  // Max faces to detect
        FaceDetector.Face[] faces = new FaceDetector.Face[maxfaces];  // Array to store detected faces
        int numberOfFaces = faceDetector.findFaces(convertedBitmap, faces);

        if (numberOfFaces > 0) {
            // Draw lines or overlays for detected faces
            for (int i = 0; i < numberOfFaces; i++) {
                FaceDetector.Face face = faces[i];
                if (face != null) {
                    PointF midpoint = new PointF();
                    face.getMidPoint(midpoint);
                    float eyesDistance = face.eyesDistance();
                    return  mapFaceToPreview(midpoint, eyesDistance, convertedBitmap.getWidth(), convertedBitmap.getHeight() , th, tw);

                }
            }
        }else {
            return  mapFaceToPreview( new PointF(0,0), 0, convertedBitmap.getWidth(), convertedBitmap.getHeight(), th,tw);

        }
        return  null ;
    }

    private static Rect mapFaceToPreview(PointF midpoint, float eyesDistance, int bitmapWidth, int bitmapHeight ,  int viewHeight , int viewWidth  ) {

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

                if (outputSizes.length >0)
                {
                    previewSize = new Size(outputSizes[0].getWidth(), outputSizes[0].getHeight());
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

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private void startPreview() {
        try {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture == null) return;
            //  surfaceTexture.setDefaultBufferSize(1920, 1080); // Set preview size
            surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            //   surfaceTexture.setDefaultBufferSize(2048, 1535);
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

    private void startRecording() {
        try {
            mediaRecorder = new MediaRecorder();

            mediaRecorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    Log.d(TAG, "onInfo: " );
                }
            });

            //   mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            //    mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

            outputFile = new File(VIDEO_FOLDER, "video_chunk_" + System.currentTimeMillis() + ".mp4");
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            SurfaceTexture texture = textureView.getSurfaceTexture();

            if (texture == null) {
                throw new IllegalStateException("SurfaceTexture is not ready");
            }
            Surface recorderSurface = new Surface(texture);
            mediaRecorder.setPreviewDisplay(recorderSurface);

            mediaRecorder.setVideoSize(previewSize.getWidth(), previewSize.getHeight());
            //   mediaRecorder.setVideoSize(2048, 1536);
            mediaRecorder.setMaxDuration(30000);
            // mediaRecorder.setCaptureRate(30);
            mediaRecorder.prepare();
            try {
                mediaRecorder.start();
            } catch (IllegalStateException ex) {
                Log.d(TAG, "startRecording: " + ex.getMessage());
            }
            isRecording = true;

            // Stop after 5 minutes (300 seconds)
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
        } catch (IllegalStateException ex) {
            ex.printStackTrace();
            stopRecording();
        } catch (IllegalArgumentException ex) {
            ex.printStackTrace();
            stopRecording();
        } catch (UnknownError ex) {
            ex.printStackTrace();
            stopRecording();
        } catch (IOException e) {
            e.printStackTrace();
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
        }finally {
            isRecording = false;
        }

    }
}
