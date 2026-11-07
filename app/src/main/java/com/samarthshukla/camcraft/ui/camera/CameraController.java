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

import android.content.SharedPreferences;
import android.provider.DocumentsContract;
import java.io.OutputStream;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    private static final ExecutorService imageProcessor =
            Executors.newSingleThreadExecutor();

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

        File sourceFile = new File(
                context.getCacheDir(),
                "capture_" + System.currentTimeMillis() + ".jpg"
        );

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(sourceFile)
                        .build();

        imageCapture.takePicture(
                outputOptions,
                executor,
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults results
                    ) {
                        imageProcessor.execute(() -> {
                            try {
                                Uri savedUri = processAndSavePhoto(
                                        context,
                                        sourceFile
                                );

                                executor.execute(() ->
                                        callback.onSaved(savedUri)
                                );

                            } catch (Exception e) {
                                e.printStackTrace();

                                executor.execute(() ->
                                        callback.onError(
                                                e.getMessage() != null
                                                        ? e.getMessage()
                                                        : "Unable to process photo."
                                        )
                                );

                            } finally {
                                if (sourceFile.exists()) {
                                    sourceFile.delete();
                                }
                            }
                        });
                    }

                    @Override
                    public void onError(
                            @NonNull ImageCaptureException exception
                    ) {
                        if (sourceFile.exists()) {
                            sourceFile.delete();
                        }

                        callback.onError(
                                exception.getMessage() != null
                                        ? exception.getMessage()
                                        : "Photo capture failed."
                        );
                    }
                }
        );
    }



    private Uri processAndSavePhoto(
            Context context,
            File sourceFile
    ) throws Exception {

        SharedPreferences preferences = context.getSharedPreferences(
                "camcraft_settings",
                Context.MODE_PRIVATE
        );

        String format = preferences.getString("image_format", "JPEG");
        String resolution = preferences.getString("image_resolution", "HIGH");

        boolean isPng = "PNG".equalsIgnoreCase(format);

        String extension = isPng ? ".png" : ".jpg";
        String mimeType = isPng ? "image/png" : "image/jpeg";

        String fileName =
                "CamCraft_" + System.currentTimeMillis() + extension;

        File finalFile = sourceFile;

        int maxDimension;

        switch (resolution.toUpperCase(java.util.Locale.ROOT)) {
            case "MEDIUM":
                maxDimension = 1920;
                break;

            case "LOW":
                maxDimension = 1280;
                break;

            case "HIGH":
            default:
                maxDimension = 0;
                break;
        }

        // Process PNG images and images requiring resolution scaling.
        if (isPng || maxDimension > 0) {

            finalFile = new File(
                    context.getCacheDir(),
                    "processed_" + System.currentTimeMillis()
                            + extension
            );

            // Read dimensions without decoding the full image.
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;

            BitmapFactory.decodeFile(
                    sourceFile.getAbsolutePath(),
                    bounds
            );

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw new IOException("Unable to read image dimensions.");
            }

            int originalWidth = bounds.outWidth;
            int originalHeight = bounds.outHeight;

            // Use sampled decoding to reduce memory usage.
            int sampleSize = 1;

            if (maxDimension > 0) {
                while (Math.max(
                        originalWidth / (sampleSize * 2),
                        originalHeight / (sampleSize * 2)
                ) > maxDimension * 2) {
                    sampleSize *= 2;
                }
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;

            Bitmap bitmap = BitmapFactory.decodeFile(
                    sourceFile.getAbsolutePath(),
                    options
            );

            if (bitmap == null) {
                throw new IOException("Unable to decode captured image.");
            }

            Bitmap outputBitmap = bitmap;

            try {
                // Scale without changing the aspect ratio.
                if (maxDimension > 0) {

                    int width = bitmap.getWidth();
                    int height = bitmap.getHeight();

                    float scale = Math.min(
                            1f,
                            maxDimension / (float) Math.max(width, height)
                    );

                    if (scale < 1f) {

                        int targetWidth = Math.max(
                                1,
                                Math.round(width * scale)
                        );

                        int targetHeight = Math.max(
                                1,
                                Math.round(height * scale)
                        );

                        outputBitmap = Bitmap.createScaledBitmap(
                                bitmap,
                                targetWidth,
                                targetHeight,
                                true
                        );
                    }
                }

                Bitmap.CompressFormat compressFormat = isPng
                        ? Bitmap.CompressFormat.PNG
                        : Bitmap.CompressFormat.JPEG;

                int quality = isPng ? 100 : 95;

                try (FileOutputStream outputStream =
                             new FileOutputStream(finalFile)) {

                    if (!outputBitmap.compress(
                            compressFormat,
                            quality,
                            outputStream
                    )) {
                        throw new IOException("Image processing failed.");
                    }

                    outputStream.flush();
                }

                // Preserve available EXIF metadata.
                copyExifAttributes(sourceFile, finalFile);

            } finally {

                if (outputBitmap != bitmap) {
                    outputBitmap.recycle();
                }

                bitmap.recycle();
            }
        }

        Uri savedUri = null;

        try {

            String customLocation = preferences.getString(
                    "save_location",
                    null
            );

            if (customLocation != null) {

                Uri treeUri = Uri.parse(customLocation);

                String documentId =
                        DocumentsContract.getTreeDocumentId(treeUri);

                Uri parentDocumentUri =
                        DocumentsContract.buildDocumentUriUsingTree(
                                treeUri,
                                documentId
                        );

                savedUri = DocumentsContract.createDocument(
                        context.getContentResolver(),
                        parentDocumentUri,
                        mimeType,
                        fileName
                );

                if (savedUri == null) {
                    throw new IOException(
                            "Unable to create image in selected folder."
                    );
                }

            } else {

                ContentValues values = new ContentValues();

                values.put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        fileName
                );

                values.put(
                        MediaStore.Images.Media.MIME_TYPE,
                        mimeType
                );

                values.put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/CamCraft"
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(
                            MediaStore.Images.Media.IS_PENDING,
                            1
                    );
                }

                savedUri = context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                );

                if (savedUri == null) {
                    throw new IOException(
                            "Unable to create image in MediaStore."
                    );
                }
            }

            try (
                    InputStream inputStream =
                            new FileInputStream(finalFile);

                    OutputStream outputStream =
                            context.getContentResolver()
                                    .openOutputStream(savedUri, "w")
            ) {

                if (outputStream == null) {
                    throw new IOException("Unable to open output image.");
                }

                byte[] buffer = new byte[8192];
                int length;

                while ((length = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, length);
                }

                outputStream.flush();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    && customLocation == null) {

                ContentValues complete = new ContentValues();

                complete.put(
                        MediaStore.Images.Media.IS_PENDING,
                        0
                );

                context.getContentResolver().update(
                        savedUri,
                        complete,
                        null,
                        null
                );
            }

            return savedUri;

        } catch (Exception e) {

            if (savedUri != null) {
                try {
                    if (preferences.getString("save_location", null) != null) {

                        DocumentsContract.deleteDocument(
                                context.getContentResolver(),
                                savedUri
                        );

                    } else {

                        context.getContentResolver().delete(
                                savedUri,
                                null,
                                null
                        );
                    }

                } catch (Exception ignored) {
                }
            }

            throw e;

        } finally {

            if (finalFile != sourceFile && finalFile.exists()) {
                finalFile.delete();
            }
        }
    }


    private void copyExifAttributes(
            File sourceFile,
            File destinationFile
    ) {
        try {
            ExifInterface source =
                    new ExifInterface(sourceFile.getAbsolutePath());

            ExifInterface destination =
                    new ExifInterface(destinationFile.getAbsolutePath());

            String[] tags = {
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_ISO_SPEED_RATINGS,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_WHITE_BALANCE,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_GPS_TIMESTAMP,
                    ExifInterface.TAG_GPS_DATESTAMP
            };

            for (String tag : tags) {
                String value = source.getAttribute(tag);

                if (value != null) {
                    destination.setAttribute(tag, value);
                }
            }

            destination.saveAttributes();

        } catch (Exception e) {
            // Image conversion should still succeed if metadata copying fails.
            e.printStackTrace();
        }
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
