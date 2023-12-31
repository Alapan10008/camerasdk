package com.example.smartqr_camera;

import android.media.Image;

/**
 * Camera callback
 *
 * @author Omar
 * @since 23/02/2017
 */

public interface EZCamCallback {
    void onCameraReady();
    void onPicture();
    void onError(String message);
    void onCameraDisconnected();
}