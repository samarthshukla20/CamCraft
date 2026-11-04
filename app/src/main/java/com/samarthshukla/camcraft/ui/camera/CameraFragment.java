
package com.samarthshukla.camcraft.ui.camera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.samarthshukla.camcraft.R;
import com.samarthshukla.camcraft.camera.CameraController;
import com.samarthshukla.camcraft.data.model.CameraUiState;
import com.samarthshukla.camcraft.databinding.FragmentCameraBinding;
import com.samarthshukla.camcraft.ui.gallery.GalleryFragment;

public class CameraFragment extends Fragment {

    private FragmentCameraBinding binding;
    private CameraViewModel viewModel;
    private CameraController cameraController;

    private Boolean lastBoundFrontCamera = null;
    private boolean cameraReady = false;

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
                            showMessage("Storage permission is required on this Android version.");
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
        observeState();
        requestCameraPermissionIfNeeded();
    }

    private void setupButtons() {
        binding.btnFlash.setOnClickListener(v -> {
            viewModel.cycleFlash();
        });

        binding.btnSwitchCamera.setOnClickListener(v -> {
            viewModel.toggleCamera();
        });

        binding.btnCapture.setOnClickListener(v -> {
            capturePhoto();
        });

        binding.btnGallery.setOnClickListener(v -> {
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, new GalleryFragment())
                    .addToBackStack(null)
                    .commit();
        });
    }

    private void observeState() {
        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null || binding == null) return;

            updateFlashLabel(state.getFlashMode());

            if (!hasCameraPermission()) return;

            if (lastBoundFrontCamera == null
                    || lastBoundFrontCamera != state.isFrontCamera()) {

                startCamera(state);
                lastBoundFrontCamera = state.isFrontCamera();

            } else {
                cameraController.setFlashMode(state.getFlashMode());
            }
        });
    }

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

    private void startCamera(CameraUiState state) {
        if (binding == null) return;

        cameraReady = false;
        binding.btnCapture.setEnabled(false);

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

                        boolean hasFlash = camera.getCameraInfo().hasFlashUnit();
                        binding.btnFlash.setEnabled(hasFlash);

                        if (!hasFlash) {
                            showMessage("Flash is unavailable on this camera.");
                        }
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
