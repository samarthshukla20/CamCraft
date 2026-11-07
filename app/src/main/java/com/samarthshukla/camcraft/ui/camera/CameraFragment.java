package com.samarthshukla.camcraft.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.MeteringPoint;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.samarthshukla.camcraft.R;
import com.samarthshukla.camcraft.ui.camera.CameraController;
import com.samarthshukla.camcraft.data.model.CameraUiState;
import com.samarthshukla.camcraft.databinding.FragmentCameraBinding;
import com.samarthshukla.camcraft.ui.gallery.GalleryFragment;

import java.util.Locale;

import android.database.Cursor;
import android.provider.MediaStore;
import com.bumptech.glide.Glide;

import android.os.Handler;
import android.os.Looper;

import android.provider.DocumentsContract;

import com.samarthshukla.camcraft.ui.settings.SettingsFragment;

public class CameraFragment extends Fragment {

    private FragmentCameraBinding binding;
    private CameraViewModel viewModel;
    private CameraController cameraController;

    private Boolean lastBoundFrontCamera = null;
    private boolean cameraReady = false;

    private float minZoom = 1f;
    private float maxZoom = 1f;
    private float currentZoom = 1f;

    private int minExposure = 0;
    private int maxExposure = 0;
    private float exposureStep = 0f;
    private boolean updatingZoomSlider = false;
    private boolean updatingExposureSlider = false;
    private boolean isScaling = false;
    private boolean isGridEnabled = false;
    private int selectedTimerSeconds = 0;
    private final android.os.Handler timerHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());

    private Runnable countdownRunnable;
    private int remainingSeconds = 0;
    private ScaleGestureDetector scaleGestureDetector;
    private boolean isZoomPanelOpen = false;
    private boolean isExposurePanelOpen = false;

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            requestLegacyStoragePermissionIfNeeded();
                        } else {
                            showMessage("Camera permission is required.");
                        }
                    }
            );

    private final ActivityResultLauncher<String> storagePermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) {
                            startCameraIfPermitted();
                        } else {
                            showMessage(
                                    "Storage permission is required on this Android version."
                            );
                        }
                    }
            );

    public CameraFragment() {
        super(R.layout.fragment_camera);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentCameraBinding.bind(view);
        viewModel = new ViewModelProvider(this).get(CameraViewModel.class);
        cameraController = new CameraController();

        setupButtons();
        loadLastPhotoThumbnail();
        setupZoomControls();
        setupExposureControls();
        setupPreviewGestures();
        observeState();

        requestCameraPermissionIfNeeded();
    }

    private void showTimerMenu() {

        if (binding == null) return;

        android.widget.PopupMenu popup =
                new android.widget.PopupMenu(
                        requireContext(),
                        binding.btnTimer
                );

        popup.getMenu().add("Off");
        popup.getMenu().add("3 seconds");
        popup.getMenu().add("5 seconds");
        popup.getMenu().add("10 seconds");

        popup.setOnMenuItemClickListener(item -> {

            String option = item.getTitle().toString();

            switch (option) {
                case "Off":
                    selectedTimerSeconds = 0;
                    break;

                case "3 seconds":
                    selectedTimerSeconds = 3;
                    break;

                case "5 seconds":
                    selectedTimerSeconds = 5;
                    break;

                case "10 seconds":
                    selectedTimerSeconds = 10;
                    break;
            }

            binding.btnTimer.setContentDescription(
                    "Camera timer " +
                            (selectedTimerSeconds == 0
                                    ? "off"
                                    : selectedTimerSeconds + " seconds")
            );

            return true;
        });

        popup.show();
    }

    private void startCountdown() {

        if (countdownRunnable != null) return;
        if (binding == null || !cameraReady) return;

        remainingSeconds = selectedTimerSeconds;

        binding.btnCapture.setEnabled(false);
        binding.txtCountdown.setVisibility(View.VISIBLE);
        binding.btnCancelCountdown.setVisibility(View.VISIBLE);

        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (binding == null) return;

                if (remainingSeconds > 0) {
                    binding.txtCountdown.setText(
                            String.valueOf(remainingSeconds)
                    );

                    binding.txtCountdown.setScaleX(1.3f);
                    binding.txtCountdown.setScaleY(1.3f);

                    binding.txtCountdown.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(300)
                            .start();

                    remainingSeconds--;
                    timerHandler.postDelayed(this, 1000);

                } else {
                    binding.txtCountdown.setVisibility(View.GONE);
                    binding.btnCancelCountdown.setVisibility(View.GONE);
                    countdownRunnable = null;
                    capturePhoto();
                }
            }
        };

        countdownRunnable.run();
    }

    private void cancelCountdown() {
        if (countdownRunnable != null) {
            timerHandler.removeCallbacks(countdownRunnable);
            countdownRunnable = null;
        }

        remainingSeconds = 0;

        if (binding != null) {
            binding.txtCountdown.animate().cancel();
            binding.txtCountdown.setVisibility(View.GONE);
            binding.txtCountdown.setScaleX(1f);
            binding.txtCountdown.setScaleY(1f);

            binding.btnCancelCountdown.setVisibility(View.GONE);
            binding.btnCapture.setEnabled(cameraReady);
        }
    }


    private void loadLastPhotoThumbnail() {
        if (binding == null) return;

        try {
            String customLocation = requireContext()
                    .getSharedPreferences(
                            "camcraft_settings",
                            android.content.Context.MODE_PRIVATE
                    )
                    .getString("save_location", null);

            // Custom SAF folder selected
            if (customLocation != null) {

                Uri treeUri = Uri.parse(customLocation);

                String documentId =
                        DocumentsContract.getTreeDocumentId(treeUri);

                Uri childrenUri =
                        DocumentsContract.buildChildDocumentsUriUsingTree(
                                treeUri,
                                documentId
                        );

                String[] projection = {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_MIME_TYPE,
                        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME
                };

                Uri latestPhotoUri = null;
                long latestDate = Long.MIN_VALUE;

                try (Cursor cursor = requireContext()
                        .getContentResolver()
                        .query(childrenUri, projection, null, null, null)) {

                    if (cursor != null) {

                        int idColumn = cursor.getColumnIndexOrThrow(
                                DocumentsContract.Document.COLUMN_DOCUMENT_ID
                        );

                        int mimeColumn = cursor.getColumnIndexOrThrow(
                                DocumentsContract.Document.COLUMN_MIME_TYPE
                        );

                        int dateColumn = cursor.getColumnIndexOrThrow(
                                DocumentsContract.Document.COLUMN_LAST_MODIFIED
                        );

                        int nameColumn = cursor.getColumnIndexOrThrow(
                                DocumentsContract.Document.COLUMN_DISPLAY_NAME
                        );

                        while (cursor.moveToNext()) {

                            String mimeType = cursor.getString(mimeColumn);

                            if (mimeType == null ||
                                    !mimeType.startsWith("image/")) {
                                continue;
                            }

                            String childId = cursor.getString(idColumn);
                            String fileName = cursor.getString(nameColumn);

                            long modifiedDate = cursor.getLong(dateColumn);

                            // Fallback: extract timestamp from CamCraft filename
                            if (modifiedDate <= 0 && fileName != null) {
                                try {
                                    int start = fileName.indexOf("CamCraft_");
                                    int end = fileName.lastIndexOf('.');

                                    if (start >= 0 && end > start) {
                                        modifiedDate = Long.parseLong(
                                                fileName.substring(start + 9, end)
                                        );
                                    }
                                } catch (NumberFormatException ignored) {
                                }
                            }

                            // Normalize timestamps if provider returns seconds
                            if (modifiedDate > 0 &&
                                    modifiedDate < 100000000000L) {
                                modifiedDate *= 1000L;
                            }

                            if (modifiedDate > latestDate) {
                                latestDate = modifiedDate;

                                latestPhotoUri =
                                        DocumentsContract.buildDocumentUriUsingTree(
                                                treeUri,
                                                childId
                                        );
                            }
                        }
                    }
                }

                if (latestPhotoUri != null) {
                    Glide.with(this)
                            .load(latestPhotoUri)
                            .centerCrop()
                            .into(binding.imgLastPhoto);
                } else {
                    binding.imgLastPhoto.setImageResource(
                            android.R.drawable.ic_menu_gallery
                    );
                }

                return;
            }

            // Default MediaStore folder
            String[] projection = {
                    MediaStore.Images.Media._ID
            };

            String selection =
                    MediaStore.Images.Media.RELATIVE_PATH + "=?";

            String[] selectionArgs = {
                    "Pictures/CamCraft/"
            };

            String sortOrder =
                    MediaStore.Images.Media.DATE_ADDED + " DESC";

            try (Cursor cursor = requireContext()
                    .getContentResolver()
                    .query(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            projection,
                            selection,
                            selectionArgs,
                            sortOrder
                    )) {

                if (cursor != null && cursor.moveToFirst()) {

                    long id = cursor.getLong(
                            cursor.getColumnIndexOrThrow(
                                    MediaStore.Images.Media._ID
                            )
                    );

                    Uri photoUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            String.valueOf(id)
                    );

                    Glide.with(this)
                            .load(photoUri)
                            .centerCrop()
                            .into(binding.imgLastPhoto);

                } else {
                    binding.imgLastPhoto.setImageResource(
                            android.R.drawable.ic_menu_gallery
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();

            if (binding != null) {
                binding.imgLastPhoto.setImageResource(
                        android.R.drawable.ic_menu_gallery
                );
            }
        }
    }


    // ---------------- BUTTONS ----------------


    private void setupButtons() {

        binding.btnFlash.setOnClickListener(v ->
                viewModel.cycleFlash()
        );

        binding.btnSwitchCamera.setOnClickListener(v -> {
            cancelCountdown();
            viewModel.toggleCamera();
        });

        binding.btnGallery.setOnClickListener(v -> openGallery());

        binding.btnResetExposure.setOnClickListener(v -> {
            if (!cameraReady) return;

            cameraController.setExposureIndex(0);
            updateExposureProgress(0);
        });

        binding.btnGrid.setOnClickListener(v -> {
            isGridEnabled = !isGridEnabled;

            binding.cameraGridOverlay.setVisibility(
                    isGridEnabled ? View.VISIBLE : View.GONE
            );

            binding.btnGrid.setContentDescription(
                    isGridEnabled ? "Disable camera grid" : "Enable camera grid"
            );
        });

        binding.btnCapture.setOnClickListener(v -> {
            if (!cameraReady) return;

            if (selectedTimerSeconds > 0) {
                startCountdown();
            } else {
                capturePhoto();
            }
        });

        binding.btnTimer.setOnClickListener(v -> showTimerMenu());

        binding.btnCancelCountdown.setOnClickListener(v ->
                cancelCountdown()
        );

        binding.btnSettings.setOnClickListener(v -> {
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new SettingsFragment())
                    .addToBackStack("settings")
                    .commit();
        });

        binding.btnZoom.setOnClickListener(v -> toggleZoomPanel());

        binding.btnExposure.setOnClickListener(v -> toggleExposurePanel());
    }

    private void openGallery() {

        if (!isAdded()) return;

        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new GalleryFragment())
                .addToBackStack("gallery")
                .commit();
    }

    private void toggleZoomPanel() {
        if (binding == null) return;

        isZoomPanelOpen = !isZoomPanelOpen;

        // Close exposure panel
        isExposurePanelOpen = false;
        binding.exposureControls.setVisibility(View.GONE);

        // Toggle zoom panel
        binding.zoomControls.setVisibility(
                isZoomPanelOpen ? View.VISIBLE : View.GONE
        );

        binding.btnZoom.setAlpha(isZoomPanelOpen ? 1f : 0.65f);
        binding.btnExposure.setAlpha(0.65f);
    }

    private void toggleExposurePanel() {
        if (binding == null) return;

        isExposurePanelOpen = !isExposurePanelOpen;

        // Close zoom panel
        isZoomPanelOpen = false;
        binding.zoomControls.setVisibility(View.GONE);

        // Toggle exposure panel
        binding.exposureControls.setVisibility(
                isExposurePanelOpen ? View.VISIBLE : View.GONE
        );

        binding.btnExposure.setAlpha(isExposurePanelOpen ? 1f : 0.65f);
        binding.btnZoom.setAlpha(0.65f);
    }


    // ---------------- VIEW MODEL ----------------

    private void observeState() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {

            if (state == null || binding == null) return;

            updateFlashLabel(state.getFlashMode());

            if (!hasCameraPermission()) return;

            if (lastBoundFrontCamera == null
                    || lastBoundFrontCamera != state.isFrontCamera()) {

                lastBoundFrontCamera = state.isFrontCamera();
                startCamera(state);

            } else if (cameraReady) {
                cameraController.setFlashMode(state.getFlashMode());
            }
        });
    }

    // ---------------- PERMISSIONS ----------------

    private void requestCameraPermissionIfNeeded() {
        if (hasCameraPermission()) {
            requestLegacyStoragePermissionIfNeeded();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLegacyStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) != PackageManager.PERMISSION_GRANTED) {

            storagePermissionLauncher.launch(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            );

        } else {
            startCameraIfPermitted();
        }
    }

    private void startCameraIfPermitted() {
        if (!hasCameraPermission() || binding == null) return;

        CameraUiState state = viewModel.getUiState().getValue();

        if (state != null) {
            lastBoundFrontCamera = state.isFrontCamera();
            startCamera(state);
        }
    }

    // ---------------- CAMERA ----------------

    private void startCamera(CameraUiState state) {
        if (binding == null) return;

        cameraReady = false;
        binding.btnCapture.setEnabled(false);
        binding.btnFlash.setEnabled(false);

        cameraController.startCamera(
                requireContext(),
                getViewLifecycleOwner(),
                binding.previewView,
                state.isFrontCamera(),
                state.getFlashMode(),
                ContextCompat.getMainExecutor(requireContext()),
                new CameraController.CameraCallback() {

                    @Override
                    public void onCameraReady(Camera camera) {
                        if (binding == null) return;

                        cameraReady = true;
                        binding.btnCapture.setEnabled(true);

                        binding.btnFlash.setEnabled(
                                camera.getCameraInfo().hasFlashUnit()
                        );

                        if (!camera.getCameraInfo().hasFlashUnit()) {
                            showMessage(
                                    "Flash is unavailable on this camera."
                            );
                        }

                        configureZoom();
                        configureExposure();
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;

                        cameraReady = false;
                        binding.btnCapture.setEnabled(false);
                        showMessage(message);
                    }
                }
        );
    }

    // ---------------- ZOOM ----------------

    private void setupZoomControls() {
        binding.seekZoom.setMax(100);

        binding.seekZoom.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        if (!fromUser || !cameraReady) return;

                        float ratio = minZoom
                                + (maxZoom - minZoom) * progress / 100f;

                        applyZoom(ratio);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );
    }

    private void configureZoom() {
        if (binding == null || !cameraReady) return;

        minZoom = cameraController.getMinZoomRatio();
        maxZoom = cameraController.getMaxZoomRatio();

        if (maxZoom < minZoom) maxZoom = minZoom;

        currentZoom = Math.max(minZoom, Math.min(1f, maxZoom));

        binding.seekZoom.setEnabled(maxZoom > minZoom);
        applyZoom(currentZoom);
    }

    private void applyZoom(float ratio) {
        if (!cameraReady || binding == null) return;

        currentZoom = Math.max(
                minZoom,
                Math.min(ratio, maxZoom)
        );

        cameraController.setZoomRatio(currentZoom);

        binding.txtZoom.setText(
                String.format(Locale.getDefault(), "%.1fx", currentZoom)
        );

        if (maxZoom > minZoom) {
            int progress = Math.round(
                    ((currentZoom - minZoom) / (maxZoom - minZoom)) * 100f
            );

            updatingZoomSlider = true;
            binding.seekZoom.setProgress(progress);
            updatingZoomSlider = false;
        }
    }

    // ---------------- EXPOSURE ----------------

    private void setupExposureControls() {
        binding.seekExposure.setMax(24);

        binding.seekExposure.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar,
                            int progress,
                            boolean fromUser
                    ) {
                        if (!fromUser || !cameraReady) return;

                        int index = minExposure + progress;
                        cameraController.setExposureIndex(index);
                        updateExposureLabel(index);
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {}
                }
        );
    }

    private void configureExposure() {
        if (binding == null || !cameraReady) return;

        minExposure = cameraController.getMinExposureIndex();
        maxExposure = cameraController.getMaxExposureIndex();
        exposureStep = cameraController.getExposureStep();

        int range = Math.max(0, maxExposure - minExposure);

        binding.seekExposure.setMax(range);
        binding.seekExposure.setEnabled(range > 0 && exposureStep > 0);

        int currentIndex = cameraController.getCurrentExposureIndex();

        updateExposureProgress(currentIndex);
        updateExposureLabel(currentIndex);
    }

    private void updateExposureProgress(int index) {
        if (binding == null) return;

        int progress = Math.max(
                0,
                Math.min(index - minExposure, maxExposure - minExposure)
        );

        updatingExposureSlider = true;
        binding.seekExposure.setProgress(progress);
        updatingExposureSlider = false;

        updateExposureLabel(index);
    }

    private void updateExposureLabel(int index) {
        if (binding == null) return;

        float ev = index * exposureStep;

        binding.txtExposure.setText(
                String.format(Locale.getDefault(), "EV %+.1f", ev)
        );
    }

    // ---------------- PINCH ZOOM & TAP FOCUS ----------------

    private void setupPreviewGestures() {

        scaleGestureDetector = new ScaleGestureDetector(
                requireContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {

                    @Override
                    public boolean onScaleBegin(
                            ScaleGestureDetector detector
                    ) {
                        isScaling = true;
                        return true;
                    }

                    @Override
                    public boolean onScale(
                            ScaleGestureDetector detector
                    ) {
                        if (cameraReady) {
                            applyZoom(
                                    currentZoom * detector.getScaleFactor()
                            );
                        }
                        return true;
                    }
                }
        );

        binding.previewView.setOnTouchListener((v, event) -> {

            scaleGestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {

                case MotionEvent.ACTION_DOWN:
                    isScaling = false;
                    return true;

                case MotionEvent.ACTION_POINTER_DOWN:
                    isScaling = true;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    return true;

                case MotionEvent.ACTION_UP:
                    if (!isScaling && cameraReady) {
                        focusAt(event.getX(), event.getY());
                    }

                    isScaling = false;
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    isScaling = false;
                    return true;
            }

            return true;
        });
    }

    private void focusAt(float x, float y) {
        if (binding == null || !cameraReady) return;

        MeteringPoint point = binding.previewView
                .getMeteringPointFactory()
                .createPoint(x, y);

        cameraController.focusAt(point);

        View indicator = binding.focusIndicator;

        indicator.setX(
                x - indicator.getWidth() / 2f
        );

        indicator.setY(
                y - indicator.getHeight() / 2f
        );

        indicator.setVisibility(View.VISIBLE);
        indicator.setAlpha(1f);

        indicator.animate()
                .alpha(0f)
                .setStartDelay(700)
                .setDuration(400)
                .withEndAction(() -> {
                    if (binding != null) {
                        indicator.setVisibility(View.GONE);
                        indicator.setAlpha(1f);
                    }
                })
                .start();
    }

    // ---------------- PHOTO CAPTURE ----------------

    private void capturePhoto() {
        if (!cameraReady || binding == null) return;

        binding.btnCapture.setEnabled(false);

        // Give immediate shutter feedback.
        playCaptureFlash();

        cameraController.capturePhoto(
                requireContext(),
                ContextCompat.getMainExecutor(requireContext()),
                new CameraController.CaptureCallback() {

                    @Override
                    public void onSaved(Uri uri) {
                        if (binding == null) return;

                        binding.btnCapture.setEnabled(true);
                        loadLastPhotoThumbnail();
                        showMessage("Photo captured successfully!");
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;

                        binding.btnCapture.setEnabled(true);
                        showMessage(message);
                    }
                }
        );
    }

    private void playCaptureFlash() {
        if (binding == null) return;

        View flash = binding.captureFlash;

        flash.animate().cancel();

        flash.setVisibility(View.VISIBLE);
        flash.setAlpha(0f);

        flash.animate()
                .alpha(0.85f)
                .setDuration(10)
                .withEndAction(() ->
                        flash.animate()
                                .alpha(0f)
                                .setDuration(150)
                                .withEndAction(() -> {
                                    if (binding != null) {
                                        flash.setVisibility(View.GONE);
                                    }
                                })
                                .start()
                )
                .start();
    }

    // ---------------- UI HELPERS ----------------
    private void updateFlashLabel(int mode) {
        if (binding == null) return;

        String label;
        int icon;

        switch (mode) {
            case CameraUiState.FLASH_ON:
                label = "ON";
                icon = R.drawable.ic_zap;
                break;

            case CameraUiState.FLASH_OFF:
                label = "OFF";
                icon = R.drawable.ic_zap_off;
                break;

            default:
                label = "AUTO";
                icon = R.drawable.ic_zap_auto;
                break;
        }

        binding.btnFlash.setImageResource(icon);
        binding.btnFlash.setContentDescription("Flash: " + label);
        binding.btnFlash.setAlpha(
                mode == CameraUiState.FLASH_OFF ? 0.55f : 1f
        );
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

    // ---------------- LIFECYCLE ----------------

    @Override
    public void onDestroyView() {
        if (cameraController != null) {
            cameraController.stopCamera();
        }

        cameraReady = false;
        lastBoundFrontCamera = null;
        cancelCountdown();
        timerHandler.removeCallbacksAndMessages(null);
        isZoomPanelOpen = false;
        isExposurePanelOpen = false;
        binding = null;

        super.onDestroyView();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadLastPhotoThumbnail();
    }
}
