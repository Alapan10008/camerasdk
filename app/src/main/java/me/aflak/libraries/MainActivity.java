package me.aflak.libraries;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;

import com.example.smartqr_camera.EZCam;
import com.example.smartqr_camera.EZCamCallback;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;



public class MainActivity extends Activity implements EZCamCallback, View.OnLongClickListener{
    private TextureView textureView;

    private EZCam cam;
    private SimpleDateFormat dateFormat;

    private final String TAG = "CAM";



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = (TextureView) findViewById(R.id.textureView);
        dateFormat = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault());

        cam = new EZCam(this);
        cam.setCameraCallback(this);

        String id = cam.getCamerasList().get(CameraCharacteristics.LENS_FACING_BACK);
        cam.selectCamera(id);


        Dexter.withActivity(MainActivity.this).withPermission(Manifest.permission.CAMERA).withListener(new PermissionListener() {
            @Override
            public void onPermissionGranted(PermissionGrantedResponse response) {
                cam.open(CameraDevice.TEMPLATE_PREVIEW, textureView);
            }

            @Override
            public void onPermissionDenied(PermissionDeniedResponse response) {
                Log.e(TAG, "permission denied");
            }

            @Override
            public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                token.continuePermissionRequest();
            }
        }).check();
    }

    @Override
    public boolean onLongClick(View v) {
        cam.takePicture();
        return false;
    }

    @Override
    public void onCameraReady() {
        cam.setCaptureSetting(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
        cam.startPreview();
        textureView.setOnLongClickListener(this);
    }

    @Override
    public void onPicture() {
        cam.stopPreview();


//        String filename = "image_"+dateFormat.format(new Date())+".jpg";
//        File file = new File(getFilesDir(), filename);
//        Intent intent = new Intent(this, DisplayActivity.class);
//        intent.putExtra("filepath", file.getAbsolutePath());
//        startActivity(intent);
//        finish();
    }

    @Override
    public void onCameraDisconnected() {
        Log.e(TAG, "Camera disconnected");
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, message);
    }

    @Override
    protected void onDestroy() {
        cam.close();
        super.onDestroy();
    }
}
