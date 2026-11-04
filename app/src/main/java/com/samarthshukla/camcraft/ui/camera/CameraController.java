package com.samarthshukla.camcraft.ui.camera;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.Executor;
import androidx.camera.core.MeteringPoint;

public class CameraController {

    public interface CameraCallback {
        void onCameraReady(Camera camera);
        void onError(String message);
    }

    public interface CaptureCallback {
        void onSaved(Uri uri);
        void onError(String message);
    }

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera camera;
    private int startRequestId = 0;

    public void startCamera(
            @NonNull Context context,
            @NonNull LifecycleOwner lifecycleOwner,
            @NonNull PreviewView previewView,
            boolean frontCamera,
            int flashMode,
            @NonNull Executor mainExecutor,
            @NonNull CameraCallback callback
    ) {
        final int requestId = ++startRequestId;

        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(context);

        future.addListener(() -> {
            if (requestId != startRequestId) return;

            try {
                ProcessCameraProvider provider = future.get();

                CameraSelector selector = frontCamera
                        ? CameraSelector.DEFAULT_FRONT_CAMERA
                        : CameraSelector.DEFAULT_BACK_CAMERA;

                if (!provider.hasCamera(selector)) {
                    callback.onError("Selected camera is unavailable.");
                    return;
                }

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageCapture capture = new ImageCapture.Builder()
                        .setCaptureMode(
                                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        )
                        .setFlashMode(toCameraXFlashMode(flashMode))
                        .build();

                provider.unbindAll();

                Camera boundCamera = provider.bindToLifecycle(
                        lifecycleOwner,
                        selector,
                        preview,
                        capture
                );

                if (requestId != startRequestId) {
                    provider.unbindAll();
                    return;
                }

                cameraProvider = provider;
                imageCapture = capture;
                camera = boundCamera;

                callback.onCameraReady(boundCamera);

            } catch (Exception e) {
                callback.onError(
                        e.getMessage() != null
                                ? e.getMessage()
                                : "Unable to start camera."
                );
            }
        }, mainExecutor);
    }

    private int toCameraXFlashMode(int mode) {
        switch (mode) {
            case 1:
                return ImageCapture.FLASH_MODE_ON;
            case 2:
                return ImageCapture.FLASH_MODE_OFF;
            case 0:
            default:
                return ImageCapture.FLASH_MODE_AUTO;
        }
    }

    public void setFlashMode(int mode) {
        if (imageCapture != null) {
            imageCapture.setFlashMode(toCameraXFlashMode(mode));
        }
    }

    // ---------------- ZOOM ----------------

    public float getMinZoomRatio() {
        if (camera == null) return 1f;
        return camera.getCameraInfo()
                .getZoomState()
                .getValue()
                .getMinZoomRatio();
    }

    public float getMaxZoomRatio() {
        if (camera == null) return 1f;
        return camera.getCameraInfo()
                .getZoomState()
                .getValue()
                .getMaxZoomRatio();
    }

    public void setZoomRatio(float ratio) {
        if (camera == null) return;

        CameraInfo info = camera.getCameraInfo();
        float min = getMinZoomRatio();
        float max = getMaxZoomRatio();

        float safeRatio = Math.max(min, Math.min(ratio, max));

        camera.getCameraControl().setZoomRatio(safeRatio);
    }

    // ---------------- TAP TO FOCUS ----------------


    public void focusAt(@NonNull MeteringPoint point) {
        if (camera == null) return;

        FocusMeteringAction action = new FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
        )
                .setAutoCancelDuration(
                        3,
                        java.util.concurrent.TimeUnit.SECONDS
                )
                .build();

        camera.getCameraControl().startFocusAndMetering(action);
    }


    // ---------------- EXPOSURE ----------------

    public int getMinExposureIndex() {
        if (camera == null) return 0;
        return camera.getCameraInfo()
                .getExposureState()
                .getExposureCompensationRange()
                .getLower();
    }

    public int getMaxExposureIndex() {
        if (camera == null) return 0;
        return camera.getCameraInfo()
                .getExposureState()
                .getExposureCompensationRange()
                .getUpper();
    }

    public int getCurrentExposureIndex() {
        if (camera == null) return 0;
        return camera.getCameraInfo()
                .getExposureState()
                .getExposureCompensationIndex();
    }

    public float getExposureStep() {
        if (camera == null) return 0f;

        return camera.getCameraInfo()
                .getExposureState()
                .getExposureCompensationStep()
                .floatValue();
    }

    public void setExposureIndex(int index) {
        if (camera == null) return;

        int safeIndex = Math.max(
                getMinExposureIndex(),
                Math.min(index, getMaxExposureIndex())
        );

        camera.getCameraControl()
                .setExposureCompensationIndex(safeIndex);
    }

    // ---------------- PHOTO CAPTURE ----------------

    public void capturePhoto(
            @NonNull Context context,
            @NonNull Executor executor,
            @NonNull CaptureCallback callback
    ) {
        if (imageCapture == null) {
            callback.onError("Camera is not ready.");
            return;
        }

        String fileName =
                "CamCraft_" + System.currentTimeMillis() + ".jpg";

        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "Pictures/CamCraft"
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
        }

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(
                        context.getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                ).build();

        imageCapture.takePicture(
                outputOptions,
                executor,
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults results
                    ) {
                        Uri savedUri = results.getSavedUri();

                        if (savedUri == null) {
                            callback.onError(
                                    "Photo was saved without a URI."
                            );
                            return;
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            ContentValues complete = new ContentValues();
                            complete.put(
                                    MediaStore.Images.Media.IS_PENDING, 0
                            );

                            context.getContentResolver().update(
                                    savedUri,
                                    complete,
                                    null,
                                    null
                            );
                        }

                        callback.onSaved(savedUri);
                    }

                    @Override
                    public void onError(
                            @NonNull ImageCaptureException exception
                    ) {
                        callback.onError(
                                exception.getMessage() != null
                                        ? exception.getMessage()
                                        : "Photo capture failed."
                        );
                    }
                }
        );
    }

    public void stopCamera() {
        startRequestId++;

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }

        cameraProvider = null;
        imageCapture = null;
        camera = null;
    }
}
