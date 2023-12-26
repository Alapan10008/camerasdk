package com.example.smartqr_camera;

import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import java.io.File;

public class smartqrcam {
    private TextureView textureView;
    private static final String TAG = "DataCapture";
    Button bTakePicture, bRecording;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private String cameraId;
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;

    private ImageReader imageReader;
    private File file;
    private File folder;
    private String folderName = "CaptureHemo";
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    CameraCharacteristics characteristics;
    View box2;
    SensorManager sensorManager;
    Sensor lightSensor;
    private float currentLux = -1;
    TextView isovalues;
    float desiredFocalLength = 5.59f; // or any other value

    private Sensor accelerometerSensor;
    private Sensor magneticFieldSensor;
    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private boolean isAccelerometerSet = false;
    private boolean isMagnetometerSet = false;
    private float[] rotationMatrix = new float[9];
    private float[] orientation = new float[3];
    private View borderViewleft,borderViewright,borderViewtop,borderViewbottom;
    private TotalCaptureResult captureResult;
    private Image capturedImage;
    boolean rawSupported = false;


}
