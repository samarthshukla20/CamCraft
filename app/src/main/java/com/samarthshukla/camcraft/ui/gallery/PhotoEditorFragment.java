package com.samarthshukla.camcraft.ui.gallery;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.samarthshukla.camcraft.R;
import com.samarthshukla.camcraft.databinding.FragmentPhotoEditorBinding;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import androidx.exifinterface.media.ExifInterface;
import android.graphics.Matrix;

public class PhotoEditorFragment extends Fragment {

    private FragmentPhotoEditorBinding binding;

    private Uri photoUri;

    private float brightness = 0f;
    private float contrast = 1f;
    private float saturation = 1f;
    private String selectedFilter = "original";

    private boolean isSaving = false;

    private final ActivityResultLauncher<Intent> cropLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {

                        if (result.getResultCode() == android.app.Activity.RESULT_OK) {

                            Uri croppedUri = UCrop.getOutput(result.getData());

                            if (croppedUri != null) {
                                photoUri = croppedUri;
                                resetAdjustments();
                                displayPhoto();
                                showMessage("Changes applied.");
                            }

                        } else if (result.getResultCode() == UCrop.RESULT_ERROR) {

                            Throwable error = UCrop.getError(result.getData());

                            showMessage(error != null
                                    ? error.getMessage()
                                    : "Editing failed.");
                        }
                    }
            );

    public PhotoEditorFragment() {
        super(R.layout.fragment_photo_editor);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentPhotoEditorBinding.bind(view);

        Bundle args = getArguments();

        if (args == null || args.getString("photo_uri") == null) {
            getParentFragmentManager().popBackStack();
            return;
        }

        String savedUri = savedInstanceState != null
                ? savedInstanceState.getString("photo_uri")
                : null;

        photoUri = savedUri != null
                ? Uri.parse(savedUri)
                : Uri.parse(args.getString("photo_uri"));

        if (savedInstanceState != null) {
            brightness = savedInstanceState.getFloat("brightness", 0f);
            contrast = savedInstanceState.getFloat("contrast", 1f);
            saturation = savedInstanceState.getFloat("saturation", 1f);

            selectedFilter = savedInstanceState.getString(
                    "selected_filter", "original"
            );
        }

        displayPhoto();

        setupControls();
        updateAdjustmentUI();

        binding.btnEditorBack.setOnClickListener(v ->
                getParentFragmentManager().popBackStack()
        );

        binding.btnCrop.setOnClickListener(v ->
                openCropper()
        );

        binding.btnResetAdjustments.setOnClickListener(v ->
                resetAdjustments()
        );

        binding.btnSavePhoto.setOnClickListener(v ->
                saveEditedPhoto()
        );
    }

    private void setupControls() {

        binding.brightnessSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        if (fromUser) {
                            brightness = progress - 100f;
                            applyAdjustments();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );

        binding.contrastSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        if (fromUser) {
                            contrast = progress / 100f;
                            applyAdjustments();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );

        binding.saturationSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        if (fromUser) {
                            saturation = progress / 100f;
                            applyAdjustments();
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );

        binding.filterOriginal.setOnClickListener(v -> selectFilter("original"));
        binding.filterWarm.setOnClickListener(v -> selectFilter("warm"));
        binding.filterBW.setOnClickListener(v -> selectFilter("bw"));
        binding.filterVintage.setOnClickListener(v -> selectFilter("vintage"));
        binding.filterCool.setOnClickListener(v -> selectFilter("cool"));
        binding.filterCinematic.setOnClickListener(v -> selectFilter("cinematic"));
        binding.filterFade.setOnClickListener(v -> selectFilter("fade"));
        binding.filterSepia.setOnClickListener(v -> selectFilter("sepia"));
        binding.filterDramatic.setOnClickListener(v -> selectFilter("dramatic"));
    }

    private void selectFilter(String filter) {
        selectedFilter = filter;
        updateFilterSelection();
        applyAdjustments();
    }

    private Bitmap rotateBitmapFromExif(
            Bitmap bitmap,
            Uri uri,
            Context context
    ) throws IOException {

        ExifInterface exif;

        try (InputStream input = context.getContentResolver()
                .openInputStream(uri)) {

            if (input == null) {
                return bitmap;
            }

            exif = new ExifInterface(input);
        }

        int orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
        );

        Matrix matrix = new Matrix();

        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;

            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;

            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;

            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.preScale(-1, 1);
                break;

            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.preScale(1, -1);
                break;

            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;

            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;

            default:
                return bitmap;
        }

        Bitmap rotated = Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.getWidth(),
                bitmap.getHeight(),
                matrix,
                true
        );

        if (rotated != bitmap) {
            bitmap.recycle();
        }

        return rotated;
    }

    private void updateFilterSelection() {

        if (binding == null) return;

        TextView[] chips = {
                binding.filterOriginal,
                binding.filterWarm,
                binding.filterBW,
                binding.filterVintage,
                binding.filterCool,
                binding.filterCinematic,
                binding.filterFade,
                binding.filterSepia,
                binding.filterDramatic
        };

        String[] names = {
                "original", "warm", "bw", "vintage",
                "cool", "cinematic", "fade", "sepia", "dramatic"
        };

        for (int i = 0; i < chips.length; i++) {

            boolean selected = selectedFilter.equals(names[i]);

            chips[i].setBackgroundColor(
                    selected ? Color.WHITE : Color.rgb(48, 48, 48)
            );

            chips[i].setTextColor(
                    selected ? Color.BLACK : Color.WHITE
            );
        }
    }

    private ColorMatrix buildCombinedColorMatrix() {

        ColorMatrix filterMatrix = new ColorMatrix();

        switch (selectedFilter) {

            case "warm":
                filterMatrix.set(new float[]{
                        1.05f, 0, 0, 0, 12,
                        0, 1.02f, 0, 0, 4,
                        0, 0, 0.92f, 0, -8,
                        0, 0, 0, 1, 0
                });
                break;

            case "bw":
                filterMatrix.setSaturation(0);
                break;

            case "vintage":
                filterMatrix.set(new float[]{
                        0.90f, 0.35f, 0.10f, 0, 15,
                        0.20f, 0.85f, 0.10f, 0, 10,
                        0.10f, 0.25f, 0.70f, 0, 5,
                        0, 0, 0, 1, 0
                });
                break;

            case "cool":
                filterMatrix.set(new float[]{
                        0.95f, 0, 0, 0, -5,
                        0, 1.0f, 0, 0, 0,
                        0, 0, 1.08f, 0, 12,
                        0, 0, 0, 1, 0
                });
                break;

            case "cinematic":
                filterMatrix.set(new float[]{
                        1.12f, 0, 0, 0, -10,
                        0, 1.08f, 0, 0, -5,
                        0, 0, 1.12f, 0, 8,
                        0, 0, 0, 1, 0
                });
                break;

            case "fade":
                filterMatrix.set(new float[]{
                        0.85f, 0, 0, 0, 25,
                        0, 0.85f, 0, 0, 25,
                        0, 0, 0.85f, 0, 25,
                        0, 0, 0, 1, 0
                });
                break;

            case "sepia":
                filterMatrix.set(new float[]{
                        0.393f, 0.769f, 0.189f, 0, 0,
                        0.349f, 0.686f, 0.168f, 0, 0,
                        0.272f, 0.534f, 0.131f, 0, 0,
                        0, 0, 0, 1, 0
                });
                break;

            case "dramatic":
                filterMatrix.set(new float[]{
                        1.25f, 0, 0, 0, -20,
                        0, 1.25f, 0, 0, -20,
                        0, 0, 1.25f, 0, -20,
                        0, 0, 0, 1, 0
                });
                break;

            default:
                // Original image
                break;
        }

        ColorMatrix saturationMatrix = new ColorMatrix();
        saturationMatrix.setSaturation(saturation);

        ColorMatrix adjustmentMatrix = new ColorMatrix(new float[]{
                contrast, 0, 0, 0, brightness,
                0, contrast, 0, 0, brightness,
                0, 0, contrast, 0, brightness,
                0, 0, 0, 1, 0
        });

        filterMatrix.postConcat(saturationMatrix);
        filterMatrix.postConcat(adjustmentMatrix);

        return filterMatrix;
    }

    private void displayPhoto() {

        if (binding == null || photoUri == null) return;

        Glide.with(this)
                .load(photoUri)
                .fitCenter()
                .into(binding.editorPreview);

        applyAdjustments();
    }

    private void applyAdjustments() {

        if (binding == null) return;

        ColorMatrix combinedMatrix = buildCombinedColorMatrix();

        binding.editorPreview.setColorFilter(
                new ColorMatrixColorFilter(combinedMatrix)
        );

        updateAdjustmentUI();
        updateFilterSelection();
    }

    private void updateAdjustmentUI() {

        if (binding == null) return;

        binding.brightnessSeekBar.setProgress(
                Math.round(brightness + 100)
        );

        binding.contrastSeekBar.setProgress(
                Math.round(contrast * 100)
        );

        binding.saturationSeekBar.setProgress(
                Math.round(saturation * 100)
        );

        binding.txtBrightnessValue.setText(
                String.valueOf(Math.round(brightness))
        );

        binding.txtContrastValue.setText(
                String.format(java.util.Locale.US, "%.2f", contrast)
        );

        binding.txtSaturationValue.setText(
                String.format(java.util.Locale.US, "%.2f", saturation)
        );
    }

    private void resetAdjustments() {

        brightness = 0f;
        contrast = 1f;
        saturation = 1f;
        selectedFilter = "original";

        applyAdjustments();
    }

    private void openCropper() {

        if (photoUri == null) return;

        Uri destinationUri = Uri.fromFile(
                new File(
                        requireContext().getCacheDir(),
                        "camcraft_edit_" + System.currentTimeMillis() + ".jpg"
                )
        );

        UCrop.Options options = new UCrop.Options();

        options.setToolbarColor(Color.BLACK);
        options.setToolbarWidgetColor(Color.WHITE);

        options.setCompressionFormat(Bitmap.CompressFormat.JPEG);
        options.setCompressionQuality(95);

        UCrop.of(photoUri, destinationUri)
                .withOptions(options)
                .start(requireContext(), cropLauncher);
    }


    private void saveEditedPhoto() {

        if (photoUri == null || isSaving || binding == null) return;

        isSaving = true;
        binding.btnSavePhoto.setEnabled(false);

        // Capture required state before starting background work.
        final Context context = requireContext().getApplicationContext();
        final Uri sourceUri = photoUri;
        final ColorMatrix combinedMatrix = buildCombinedColorMatrix();

        final Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {

            Uri savedUri = null;
            Bitmap original = null;
            Bitmap edited = null;
            String errorMessage = null;

            try {
                BitmapFactory.Options decodeOptions =
                        new BitmapFactory.Options();

                decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;

                // Decode original image.
                try (InputStream input = context.getContentResolver()
                        .openInputStream(sourceUri)) {

                    if (input == null) {
                        throw new IOException("Unable to open source image.");
                    }

                    original = BitmapFactory.decodeStream(
                            input, null, decodeOptions
                    );
                }

                if (original == null) {
                    throw new IOException("Unable to decode image.");
                }

                // Correct image orientation using EXIF metadata.
                original = rotateBitmapFromExif(original, sourceUri, context);

                // Create output bitmap.
                edited = Bitmap.createBitmap(
                        original.getWidth(),
                        original.getHeight(),
                        Bitmap.Config.ARGB_8888
                );

                Canvas canvas = new Canvas(edited);

                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                paint.setFilterBitmap(true);

                // Apply the same matrix used by the editor preview.
                paint.setColorFilter(
                        new ColorMatrixColorFilter(combinedMatrix)
                );

                canvas.drawBitmap(original, 0, 0, paint);

                // Prepare MediaStore entry.
                ContentValues values = new ContentValues();

                values.put(
                        MediaStore.Images.Media.DISPLAY_NAME,
                        "CamCraft_Edit_" + System.currentTimeMillis() + ".jpg"
                );

                values.put(
                        MediaStore.Images.Media.MIME_TYPE,
                        "image/jpeg"
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            "Pictures/CamCraft/"
                    );

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
                    throw new IOException("Unable to create output image.");
                }

                // Write edited bitmap.
                try (OutputStream output = context.getContentResolver()
                        .openOutputStream(savedUri)) {

                    if (output == null) {
                        throw new IOException("Unable to open output stream.");
                    }

                    if (!edited.compress(
                            Bitmap.CompressFormat.JPEG,
                            95,
                            output
                    )) {
                        throw new IOException("Image compression failed.");
                    }
                }

                // Make image visible in the gallery.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

                    ContentValues completed = new ContentValues();

                    completed.put(
                            MediaStore.Images.Media.IS_PENDING,
                            0
                    );

                    context.getContentResolver().update(
                            savedUri, completed, null, null
                    );
                }

            } catch (Exception e) {

                e.printStackTrace();
                errorMessage = e.getMessage();

                if (savedUri != null) {
                    context.getContentResolver().delete(
                            savedUri, null, null
                    );
                }

            } finally {

                if (original != null && !original.isRecycled()) {
                    original.recycle();
                }

                if (edited != null && !edited.isRecycled()) {
                    edited.recycle();
                }
            }

            final String resultMessage = errorMessage == null
                    ? "Edited photo saved successfully!"
                    : "Failed to save photo: " + errorMessage;

            // Return to the main thread safely.
            mainHandler.post(() -> {

                isSaving = false;

                if (binding != null) {
                    binding.btnSavePhoto.setEnabled(true);
                    showMessage(resultMessage);
                }
            });

        }).start();
    }


    private void showMessage(String message) {

        if (getContext() != null) {
            Toast.makeText(
                    requireContext(),
                    message,
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (photoUri != null) {
            outState.putString("photo_uri", photoUri.toString());
        }

        outState.putFloat("brightness", brightness);
        outState.putFloat("contrast", contrast);
        outState.putFloat("saturation", saturation);
        outState.putString("selected_filter", selectedFilter);
    }

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }
}