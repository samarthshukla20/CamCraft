
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

    private ScaleGestureDetector scaleGestureDetector;

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
        setupZoomControls();
        setupExposureControls();
        setupPreviewGestures();
        observeState();

        requestCameraPermissionIfNeeded();
    }

    // ---------------- BUTTONS ----------------

    private void setupButtons() {

        binding.btnFlash.setOnClickListener(v ->
                viewModel.cycleFlash()
        );

        binding.btnSwitchCamera.setOnClickListener(v ->
                viewModel.toggleCamera()
        );

        binding.btnCapture.setOnClickListener(v ->
                capturePhoto()
        );

        binding.btnGallery.setOnClickListener(v ->
                getParentFragmentManager()
                        .beginTransaction()
                        .replace(
                                R.id.fragmentContainer,
                                new GalleryFragment()
                        )
                        .addToBackStack(null)
                        .commit()
        );

        binding.btnResetExposure.setOnClickListener(v -> {
            if (!cameraReady) return;

            cameraController.setExposureIndex(0);
            updateExposureProgress(0);
        });
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

        cameraController.capturePhoto(
                requireContext(),
                ContextCompat.getMainExecutor(requireContext()),
                new CameraController.CaptureCallback() {

                    @Override
                    public void onSaved(Uri uri) {
                        if (binding == null) return;

                        binding.btnCapture.setEnabled(true);
                        showMessage("Photo saved to Pictures/CamCraft");
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

    // ---------------- UI HELPERS ----------------

    private void updateFlashLabel(int mode) {
        if (binding == null) return;

        String label;

        switch (mode) {
            case CameraUiState.FLASH_ON:
                label = "Flash: ON";
                break;

            case CameraUiState.FLASH_OFF:
                label = "Flash: OFF";
                break;

            default:
                label = "Flash: AUTO";
                break;
        }

        binding.btnFlash.setContentDescription(label);
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
        binding = null;

        super.onDestroyView();
    }
}
