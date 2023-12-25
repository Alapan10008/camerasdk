package com.example.smartqr_camera;
import static android.content.ContentValues.TAG;




import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ColorSpaceTransform;
import android.hardware.camera2.params.RggbChannelVector;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.heifwriter.HeifWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;


/**
 * Class that simplifies the use of Camera 2 api
 *
 * @author Omar Aflak
 * @since 23/02/2017
 */

public class EZCam {
    private Context context;
    private EZCamCallback cameraCallback;

    private SparseArray<String> camerasList;
    private String currentCamera;
    private Size previewSize;
    private File file;
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSession;
    private CameraCharacteristics cameraCharacteristics;
    private CaptureRequest.Builder captureRequestBuilder;
    private CaptureRequest.Builder captureRequestBuilderImageReader;
    private ImageReader imageReader;

    SensorManager sensorManager;
    Sensor lightSensor;
    private float currentLux = -1;
    TextView isovalues;
    float desiredFocalLength = 5.59f; // or any other value
    private Size imageDimension;
    private Sensor accelerometerSensor;
    private Sensor magneticFieldSensor;
    private float[] lastAccelerometer = new float[3];
    private float[] lastMagnetometer = new float[3];
    private boolean isAccelerometerSet = false;
    private boolean isMagnetometerSet = false;
    private float[] rotationMatrix = new float[9];
    private float[] orientation = new float[3];
    private TotalCaptureResult captureResult;
    private Image capturedImage;
    boolean rawSupported = false;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    public EZCam(Context context) {
        this.context = context;
        this.cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

//        accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
//        magneticFieldSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
//        this.sensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
//        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
//        sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_FASTEST);
//        if (accelerometerSensor != null && magneticFieldSensor != null) {
//            sensorManager.registerListener(sensorListener, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL);
//            sensorManager.registerListener(sensorListener, magneticFieldSensor, SensorManager.SENSOR_DELAY_NORMAL);
//        }
//        else {
//            Toast.makeText(context, "Required sensors are not available!", Toast.LENGTH_LONG).show();
//        }
    }



    /**
     * Set callback to receive camera states
     * @param cameraCallback callback
     */
    public void setCameraCallback(EZCamCallback cameraCallback) {
        this.cameraCallback = cameraCallback;
    }

    /**
     * Get available cameras
     * @return SparseArray of available cameras ids
     */
    public SparseArray<String> getCamerasList(){
        camerasList = new SparseArray<>();
        try {
            String[] camerasAvailable = cameraManager.getCameraIdList();
            CameraCharacteristics cam;
            Integer characteristic;
            for (String id : camerasAvailable){
                cam = cameraManager.getCameraCharacteristics(id);
                characteristic = cam.get(CameraCharacteristics.LENS_FACING);
                if (characteristic!=null){
                    switch (characteristic){
                        case CameraCharacteristics.LENS_FACING_FRONT:
                            camerasList.put(CameraCharacteristics.LENS_FACING_FRONT, id);
                            break;

                        case CameraCharacteristics.LENS_FACING_BACK:
                            camerasList.put(CameraCharacteristics.LENS_FACING_BACK, id);
                            break;

                        case CameraCharacteristics.LENS_FACING_EXTERNAL:
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                camerasList.put(CameraCharacteristics.LENS_FACING_EXTERNAL, id);
                            }
                            break;
                    }
                }
            }
            return camerasList;
        } catch (CameraAccessException e) {
            notifyError(e.getMessage());
            return null;
        }
    }

    /**
     * Select the camera you want to open : front, back, external(s)
     * @param id Id of the camera which can be retrieved with getCamerasList().get(CameraCharacteristics.LENS_FACING_BACK)
     */
    public void selectCamera(String id) {
        if(camerasList == null){
            getCamerasList();
        }

        currentCamera = camerasList.indexOfValue(id)<0?null:id;
        if(currentCamera == null) {
            notifyError("Camera id not found.");
            return;
        }

        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(currentCamera);
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            int[] capabilities = cameraCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

            for (int capability : capabilities) {
                if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) {
                    rawSupported = true;
                    break;
                }
            }
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];




            // Add permission for camera and let user grant the permission
//            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                ActivityCompat.requestPermissions(context, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
//                return;
//            }
            if(map != null) {
                previewSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
                imageReader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(onImageAvailable, backgroundHandler);
            }
            else{
                notifyError("Could not get configuration map.");
            }
        } catch (CameraAccessException e) {
            notifyError(e.getMessage());
        }
    }
    private final SensorEventListener sensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {

            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, lastAccelerometer, 0, event.values.length);
                isAccelerometerSet = true;
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, lastMagnetometer, 0, event.values.length);
                isMagnetometerSet = true;
            }
            if (isAccelerometerSet && isMagnetometerSet) {
                SensorManager.getRotationMatrix(rotationMatrix, null, lastAccelerometer, lastMagnetometer);
                SensorManager.getOrientation(rotationMatrix, orientation);

                float rollInRadians = orientation[2];
                float rollInDegrees = (float) Math.toDegrees(rollInRadians);

                float pitchInRadians = orientation[1];
                float pitchInDegrees = (float) Math.toDegrees(pitchInRadians);

                boolean shouldHideTakePicture = false; // Initialize the variable

// Check the roll condition for left tilt
                if (rollInDegrees > 15) {
//                    borderViewleft.setBackgroundResource(R.color.red);
//                    shouldHideTakePicture = true; // Set the variable to true
                } else {
//                    borderViewleft.setBackgroundResource(R.color.green);
                }

// Check the roll condition for right tilt
                if (rollInDegrees < -15) {
//                    borderViewright.setBackgroundResource(R.color.red);
                    shouldHideTakePicture = true; // Set the variable to true
                } else {
//                    borderViewright.setBackgroundResource(R.color.green);
                }

// Check the pitch condition for top tilt
                if (pitchInDegrees >= -40) {
//                    borderViewtop.setBackgroundResource(R.color.green);
                } else {
//                    borderViewtop.setBackgroundResource(R.color.red);
//                    shouldHideTakePicture = true; // Set the variable to true
                }

// Check for bottom tilt
                if (pitchInDegrees <= -20) {
//                    borderViewbottom.setBackgroundResource(R.color.green);
                } else {
//                    borderViewbottom.setBackgroundResource(R.color.red);
                    shouldHideTakePicture = true; // Set the variable to true
                }

                // Set the visibility of bTakePicture based on the shouldHideTakePicture variable
                if (shouldHideTakePicture) {
//                    bTakePicture.setVisibility(View.GONE);
                } else {
//                    bTakePicture.setVisibility(View.VISIBLE);
                }

            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // Handle accuracy changes if necessary
        }
    };

    private final SensorEventListener lightSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
                currentLux = event.values[0];
//                setCameraProperties();

            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            // You can handle changes in accuracy here if necessary
        }
    };


    /**
     * Open camera to prepare preview
     * @param templateType capture mode e.g. CameraDevice.TEMPLATE_PREVIEW
     * @param textureView Surface where preview should be displayed
     */
    public void open(final int templateType, final TextureView textureView) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            notifyError("You don't have the required permissions.");
            return;
        }

        startBackgroundThread();

        try {

            try {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                    int supportLevel = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);

                    switch (supportLevel) {
                        case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY:
                            System.out.println("Camera ID " + cameraId + ": LEGACY support for Camera2 API");
                            break;
                        case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED:
                            System.out.println("Camera ID " + cameraId + ": LIMITED support for Camera2 API");
                            break;
                        case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL:
                            System.out.println("Camera ID " + cameraId + ": FULL support for Camera2 API");
                            break;
                        case CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3:
                            System.out.println("Camera ID " + cameraId + ": LEVEL 3 support for Camera2 API");
                            break;
                        default:
                            System.out.println("Camera ID " + cameraId + ": Unknown support level for Camera2 API");
                    }
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            cameraManager.openCamera(currentCamera, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    cameraDevice = camera;
                    setupPreview(templateType, textureView);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {
                    if(cameraCallback != null){
                        cameraCallback.onError("Camera device is no longer available for use.");
                        cameraCallback.onCameraDisconnected();
                    }
                }

                @Override
                public void onError( CameraDevice camera, int error) {
                    switch (error){
                        case CameraDevice.StateCallback.ERROR_CAMERA_DEVICE:
                            notifyError("Camera device has encountered a fatal error.");
                            break;
                        case CameraDevice.StateCallback.ERROR_CAMERA_DISABLED:
                            notifyError("Camera device could not be opened due to a device policy.");
                            break;
                        case CameraDevice.StateCallback.ERROR_CAMERA_IN_USE:
                            notifyError("Camera device is in use already.");
                            break;
                        case CameraDevice.StateCallback.ERROR_CAMERA_SERVICE:
                            notifyError("Camera service has encountered a fatal error.");
                            break;
                        case CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE:
                            notifyError("Camera device could not be opened because there are too many other open camera devices.");
                            break;
                    }
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            notifyError(e.getMessage());
        }
    }

    private void setupPreview_(int templateType, TextureView textureView){
        Surface surface = new Surface(textureView.getSurfaceTexture());

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(templateType);
            captureRequestBuilder.addTarget(surface);

            captureRequestBuilderImageReader = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilderImageReader.addTarget(imageReader.getSurface());
            setCameraProperties();
            cameraDevice.createCaptureSession(Arrays.asList(surface, imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    cameraCaptureSession = session;
                    updatePreview();
                    if(cameraCallback != null){
                        cameraCallback.onCameraReady();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    notifyError("Could not configure capture session.");
                }
            }, backgroundHandler);
        } catch (CameraAccessException e) {
            notifyError(e.getMessage());
        }
    }

    private void setupPreview(final int templateType, final TextureView outputSurface){
        if(outputSurface.isAvailable()){
            setupPreview_(templateType, outputSurface);
        }
        else{
            outputSurface.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                    setAspectRatioTextureView(outputSurface, width, height);
                    setupPreview_(templateType, outputSurface);
                }

                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {}
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {return false;}
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {}
            });
        }
    }

    /**
     * Set CaptureRequest parameters for preview e.g. flash, auto-focus, macro mode, etc.
     * @param key e.g. CaptureRequest.CONTROL_EFFECT_MODE
     * @param value e.g. CameraMetadata.CONTROL_EFFECT_MODE_NEGATIVE
     */
    public<T> void setCaptureSetting(CaptureRequest.Key<T> key, T value){
        if(captureRequestBuilder!=null && captureRequestBuilderImageReader!=null) {
            captureRequestBuilder.set(key, value);
            captureRequestBuilderImageReader.set(key, value);
        }
    }

    /**
     * Get characteristic of selected camera e.g. available effects, scene modes, etc.
     * @param key e.g. CameraCharacteristics.CONTROL_AVAILABLE_EFFECTS
     */
    public<T> T getCharacteristic(CameraCharacteristics.Key<T> key){
        if(cameraCharacteristics!=null) {
            return cameraCharacteristics.get(key);
        }
        return null;
    }

    private void setAspectRatioTextureView(TextureView textureView, int surfaceWidth, int surfaceHeight)
    {
        int rotation = ((Activity)context).getWindowManager().getDefaultDisplay().getRotation();
        int newWidth = surfaceWidth, newHeight = surfaceHeight;

        switch (rotation) {
            case Surface.ROTATION_0:
                newWidth = surfaceWidth;
                newHeight = (surfaceWidth * previewSize.getWidth() / previewSize.getHeight());
                break;

            case Surface.ROTATION_180:
                newWidth = surfaceWidth;
                newHeight = (surfaceWidth * previewSize.getWidth() / previewSize.getHeight());
                break;

            case Surface.ROTATION_90:
                newWidth = surfaceHeight;
                newHeight = (surfaceHeight * previewSize.getWidth() / previewSize.getHeight());
                break;

            case Surface.ROTATION_270:
                newWidth = surfaceHeight;
                newHeight = (surfaceHeight * previewSize.getWidth() / previewSize.getHeight());
                break;
        }

        textureView.setLayoutParams(new FrameLayout.LayoutParams(newWidth, newHeight, Gravity.CENTER));
        rotatePreview(textureView, rotation, newWidth, newHeight);
    }

    private void rotatePreview(TextureView mTextureView, int rotation, int viewWidth, int viewHeight) {
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    /**
     * start the preview, capture request is built at each call here
     */
    public void startPreview(){
        try {
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            notifyError(e.getMessage());
        }
    }

    /**
     * stop the preview
     */
    public void stopPreview(){
        try {
            cameraCaptureSession.stopRepeating();
        } catch (CameraAccessException e) {
            notifyError(e.getMessage());
        }
    }

    /**
     * shortcut to call stopPreview() then startPreview()
     */
    public void restartPreview(){
        stopPreview();
        startPreview();
    }

    /**
     * close the camera definitively
     */
    public void close(){
        cameraDevice.close();
        stopBackgroundThread();
    }

    /**
     * take a picture
     */
    public void takePicture(){
        captureRequestBuilderImageReader.set(CaptureRequest.JPEG_ORIENTATION, cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION));

        int width = 1920;
        int height = 1080;


        ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);

        if (cameraCharacteristics != null) {

            if (rawSupported) {
                Size[] rawSizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.RAW_SENSOR);
                if (rawSizes != null && rawSizes.length > 0) {
                    width = rawSizes[0].getWidth();
                    height = rawSizes[0].getHeight();
                }

                reader = ImageReader.newInstance(width, height, ImageFormat.RAW_SENSOR, 1);
//                        File dir = getExternalFilesDir(null);

                // Setting up file for saving the DNG image
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "IMG_" + timeStamp + ".dng"; // Use ".dng" extension
                File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                file = new File(storageDir, imageFileName);
                Log.e(TAG, "Raw Supported : " + rawSupported);
            } else {
                Size[] rawSizes = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
                reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 2);
//                    File dir = getExternalFilesDir(null);
                // Setting up file for saving the DNG image
                String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String imageFileName = "IMG_" + timeStamp + ".heic"; // Use ".dng" extension
                File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                file = new File(storageDir, imageFileName);
                Log.e(TAG, "Raw Supported : " + rawSupported);
            }
        }


        setCameraProperties();

        ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                capturedImage = reader.acquireLatestImage();
                if (rawSupported) {
                    processImage();
                } else {
                    processHEICImage();
                }
            }
        };
        reader.setOnImageAvailableListener(readerListener, backgroundHandler);


//            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
//                @Override
//                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
//                    super.onCaptureCompleted(session, request, result);
//                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
//                    Integer aeCompensation = result.get(CaptureResult.CONTROL_AE_EXPOSURE_COMPENSATION);
//
//                    RggbChannelVector gains = result.get(CaptureResult.COLOR_CORRECTION_GAINS);
//                    ColorSpaceTransform transform = result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM);
//
//                    Integer actualISO = result.get(CaptureResult.SENSOR_SENSITIVITY);
//                    if (actualISO != null) {
//                        Log.d("Camera", "Actual ISO: " + gains + transform);
//                    }
//
//                    captureResult = result;
//                    // Now check if captureResult is null
//                    if (captureResult != null) {
//                        if (rawSupported) {
//                            processImage();
//                        } else {
//                            processHEICImage();
//                        }
//                        Toast.makeText(context, "Saved:" + file, Toast.LENGTH_SHORT).show();
//                    } else {
//                        Log.e(TAG, "captureResult is null");
//                    }
////                    createCameraPreview();
//                }
//            };


        try {
            cameraCaptureSession.capture(captureRequestBuilderImageReader.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            notifyError(e.getMessage());
        }

    }

    private ImageReader.OnImageAvailableListener onImageAvailable = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            if(cameraCallback != null){
                cameraCallback.onPicture(imageReader.acquireLatestImage());
            }
        }
    };

    private void notifyError(String message) {
        if (cameraCallback != null) {
            cameraCallback.onError(message);
        }
    }

    /**
     * Save image to storage
     * @param image Image object got from onPicture() callback of EZCamCallback
     * @param file File where image is going to be written
     * @return File object pointing to the file uri, null if file already exist
     */
    public static File saveImage(Image image, File file) throws IOException {
        if(file.exists()) {
            image.close();
            return null;
        }
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        FileOutputStream output = new FileOutputStream(file);
        output.write(bytes);
        image.close();
        output.close();
        return file;
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("EZCam");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundThread.quitSafely();
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            notifyError(e.getMessage());
        }
    }

    private void processHEICImage(){
        if (capturedImage != null && captureResult != null) {
            Image.Plane[] planes = capturedImage.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);

            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
//            String destination = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + "/photo.heic";

            try {
                HeifWriter.Builder builder = new HeifWriter.Builder(
                        file.getAbsolutePath(),
                        bitmap.getWidth(),
                        bitmap.getHeight(),
                        HeifWriter.INPUT_MODE_BITMAP
                );
                builder.setQuality(90);
                HeifWriter heifWriter = builder.build();

                heifWriter.start();
                heifWriter.addBitmap(bitmap);
                heifWriter.stop(0);
                heifWriter.close();
                Toast.makeText(context, "Captured", Toast.LENGTH_SHORT).show();
//                            // Notify MediaStore
                MediaScannerConnection.scanFile(context,
                        new String[]{file.getAbsolutePath()}, null,
                        (path, uri) -> {
                            Log.i("ExternalStorage", "Scanned " + path + ":");
                            Log.i("ExternalStorage", "-> uri=" + uri);
                        });
            } catch (IOException e) {
                e.printStackTrace();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
    private void processImage() {
        if (capturedImage != null && captureResult != null) {
            // Now you have both the image and the capture result
            DngCreator dngCreator = new DngCreator(cameraCharacteristics, captureResult);
            try (FileOutputStream output = new FileOutputStream(file)) {
                dngCreator.writeImage(output, capturedImage);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                capturedImage.close();
                capturedImage = null;
                captureResult = null;
            }

            // Notify MediaStore
            MediaScannerConnection.scanFile(context,
                    new String[]{file.toString()}, null,
                    (path, uri) -> {
                        Log.i("ExternalStorage", "Scanned " + path + ":");
                        Log.i("ExternalStorage", "-> uri=" + uri);
                    });
        }
    }
    private void setCameraProperties() {


        if (captureRequestBuilder == null) return;

        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
        Range<Integer> targetFpsRange = new Range<>(30, 30); // This means it will aim for a constant 30fps
        captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, targetFpsRange);

        captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF);

        if(rawSupported){
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);
        }
        else {
            captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        }
//        captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_OFF);

    }

    protected void updatePreview() {
        if (null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
            return;
        }
        try {
            setCameraProperties();
            cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

}