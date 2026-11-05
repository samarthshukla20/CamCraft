package com.samarthshukla.camcraft.ui.gallery;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.samarthshukla.camcraft.R;
import com.samarthshukla.camcraft.data.repository.PhotoRepository;
import com.samarthshukla.camcraft.databinding.FragmentPhotoViewerBinding;

import java.util.ArrayList;
import android.content.IntentSender;

public class PhotoViewerFragment extends Fragment {

    private FragmentPhotoViewerBinding binding;

    private ArrayList<String> photoUris;
    private int currentIndex = 0;

    private float startX;
    private float startY;

    private static final int SWIPE_THRESHOLD = 80;
    private static final int SWIPE_DISTANCE_THRESHOLD = 120;

    private final PhotoRepository photoRepository = new PhotoRepository();

    private String pendingDeleteUri;

    private final ActivityResultLauncher<IntentSenderRequest> deleteLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartIntentSenderForResult(),
                    result -> {
                        if (pendingDeleteUri == null) return;

                        if (result.getResultCode() == android.app.Activity.RESULT_OK) {
                            removeDeletedPhoto(pendingDeleteUri);
                        } else {
                            showMessage("Photo was not deleted.");
                        }

                        pendingDeleteUri = null;
                    });

    public PhotoViewerFragment() {
        super(R.layout.fragment_photo_viewer);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        binding = FragmentPhotoViewerBinding.bind(view);

        Bundle args = getArguments();

        if (args == null) {
            getParentFragmentManager().popBackStack();
            return;
        }

        photoUris = args.getStringArrayList("photo_uris");
        currentIndex = args.getInt("selected_index", 0);

        if (photoUris == null || photoUris.isEmpty()) {
            getParentFragmentManager().popBackStack();
            return;
        }

        currentIndex = Math.max(
                0,
                Math.min(currentIndex, photoUris.size() - 1)
        );

        binding.btnViewerBack.setOnClickListener(v ->
                getParentFragmentManager().popBackStack()
        );

        binding.btnDeletePhoto.setOnClickListener(v ->
                confirmDelete()
        );

        binding.btnSharePhoto.setOnClickListener(v ->
                sharePhoto()
        );

        binding.fullPhoto.setOnTouchListener((v, event) -> {
            handleSwipe(event);
            return true;
        });

        displayPhoto();
    }

    // ---------------- SWIPE NAVIGATION ----------------

    private void handleSwipe(MotionEvent event) {
        switch (event.getAction()) {

            case MotionEvent.ACTION_DOWN:
                startX = event.getX();
                startY = event.getY();
                return;

            case MotionEvent.ACTION_UP:

                float deltaX = event.getX() - startX;
                float deltaY = event.getY() - startY;

                if (Math.abs(deltaX) > SWIPE_THRESHOLD
                        && Math.abs(deltaX) > Math.abs(deltaY)
                        && Math.abs(deltaX) > SWIPE_DISTANCE_THRESHOLD) {

                    if (deltaX < 0) {
                        showNextPhoto();
                    } else {
                        showPreviousPhoto();
                    }
                }
                break;
        }
    }

    private void showNextPhoto() {
        if (currentIndex < photoUris.size() - 1) {
            currentIndex++;
            displayPhoto();
        }
    }

    private void showPreviousPhoto() {
        if (currentIndex > 0) {
            currentIndex--;
            displayPhoto();
        }
    }

    // ---------------- DISPLAY ----------------

    private void displayPhoto() {
        if (binding == null || photoUris.isEmpty()) return;

        String uriString = photoUris.get(currentIndex);

        Glide.with(this)
                .load(Uri.parse(uriString))
                .fitCenter()
                .into(binding.fullPhoto);

        binding.txtPhotoPosition.setText(
                (currentIndex + 1) + " / " + photoUris.size()
        );
    }

    // ---------------- DELETE ----------------

    private void confirmDelete() {
        if (photoUris.isEmpty()) return;

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete photo?")
                .setMessage(
                        "This photo will be removed from your device."
                )
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) ->
                        deleteCurrentPhoto()
                )
                .show();
    }

    private void deleteCurrentPhoto() {
        if (photoUris.isEmpty()) return;

        Uri uri = Uri.parse(photoUris.get(currentIndex));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.app.PendingIntent pendingIntent =
                        android.provider.MediaStore.createDeleteRequest(
                                requireContext().getContentResolver(),
                                java.util.Collections.singletonList(uri)
                        );

                pendingDeleteUri = uri.toString();

                deleteLauncher.launch(
                        new androidx.activity.result.IntentSenderRequest.Builder(
                                pendingIntent.getIntentSender()
                        ).build()
                );

            } catch (Exception e) {
                showMessage("Unable to request photo deletion.");
            }

        } else {
            try {
                int deleted = photoRepository.deletePhoto(
                        requireContext(),
                        uri
                );

                if (deleted > 0) {
                    removeDeletedPhoto(uri.toString());
                } else {
                    showMessage("Unable to delete this photo.");
                }

            } catch (SecurityException e) {
                showMessage("Permission required to delete this photo.");
            }
        }
    }


    private void removeDeletedPhoto(String uri) {
        int deletedIndex = photoUris.indexOf(uri);

        if (deletedIndex < 0) return;

        photoUris.remove(deletedIndex);

        if (photoUris.isEmpty()) {
            getParentFragmentManager().popBackStack();
            showMessage("Photo deleted.");
            return;
        }

        if (deletedIndex < currentIndex) {
            currentIndex--;
        } else if (currentIndex >= photoUris.size()) {
            currentIndex = photoUris.size() - 1;
        }

        displayPhoto();
        showMessage("Photo deleted.");
    }

    // ---------------- SHARE ----------------

    private void sharePhoto() {
        if (photoUris.isEmpty()) return;

        Uri uri = Uri.parse(photoUris.get(currentIndex));

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/jpeg");
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(
                Intent.createChooser(shareIntent, "Share photo")
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

    @Override
    public void onDestroyView() {
        binding = null;
        super.onDestroyView();
    }

    private static class IntentSenderWrapper {
        private final android.content.IntentSender sender;

        IntentSenderWrapper(android.content.IntentSender sender) {
            this.sender = sender;
        }

        Intent createIntent() {
            return new Intent().putExtra(
                    "intentSender",
                    sender
            );
        }
    }
}