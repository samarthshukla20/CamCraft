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
import android.provider.DocumentsContract;

import android.view.LayoutInflater;
import android.widget.TextView;

import com.samarthshukla.camcraft.data.repository.PhotoDetailsReader;

import java.util.Map;

import android.graphics.drawable.ColorDrawable;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        binding.btnEditPhoto.setOnClickListener(v -> openEditor());

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

        binding.btnPhotoDetails.setOnClickListener(v ->
                showPhotoDetails()
        );

        binding.fullPhoto.setOnTouchListener((v, event) -> {
            handleSwipe(event);
            return true;
        });

        displayPhoto();
    }

    private void openEditor() {
        if (photoUris == null || photoUris.isEmpty()) return;

        Bundle bundle = new Bundle();
        bundle.putString("photo_uri", photoUris.get(currentIndex));

        PhotoEditorFragment editorFragment = new PhotoEditorFragment();
        editorFragment.setArguments(bundle);

        getParentFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, editorFragment)
                .addToBackStack(null)
                .commit();
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

    private void showPhotoDetails() {
        if (photoUris == null || photoUris.isEmpty()) return;

        Uri uri = Uri.parse(photoUris.get(currentIndex));

        Map<String, String> details =
                PhotoDetailsReader.read(requireContext(), uri);

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_photo_details, null);

        LinearLayout container =
                dialogView.findViewById(R.id.detailsContainer);

        TextView noDetails =
                dialogView.findViewById(R.id.txtNoDetails);

        String[][] sections = {
                {"FILE INFORMATION", "📁",
                        "File name", "File size", "Dimensions"},

                {"CAMERA INFORMATION", "📷",
                        "Camera make", "Camera model", "Focal length"},

                {"EXPOSURE SETTINGS", "☀",
                        "ISO", "Shutter speed", "Aperture",
                        "Flash", "White balance"},

                {"DATE & LOCATION", "📍",
                        "Date captured", "GPS location"}
        };

        int displayedSections = 0;

        for (String[] section : sections) {

            LinearLayout rows = new LinearLayout(requireContext());
            rows.setOrientation(LinearLayout.VERTICAL);

            int rowCount = 0;

            for (int i = 2; i < section.length; i++) {

                String key = section[i];
                String value = details.get(key);

                if (value == null || value.trim().isEmpty()) {
                    continue;
                }

                if (rowCount > 0) {
                    View divider = new View(requireContext());

                    divider.setBackgroundColor(
                            Color.parseColor("#383838")
                    );

                    LinearLayout.LayoutParams dividerParams =
                            new LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    1
                            );

                    dividerParams.setMargins(0, 10, 0, 10);
                    rows.addView(divider, dividerParams);
                }

                addDetailRow(rows, key, value,
                        "GPS location".equals(key));

                rowCount++;
            }

            if (rowCount == 0) continue;

            View card = LayoutInflater.from(requireContext())
                    .inflate(R.layout.item_photo_detail_card,
                            container, false);

            TextView icon = card.findViewById(R.id.txtSectionIcon);
            TextView title = card.findViewById(R.id.txtSectionTitle);
            LinearLayout detailRows = card.findViewById(R.id.detailRows);

            icon.setText(section[1]);
            title.setText(section[0]);

            detailRows.addView(rows);

            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.parseColor("#202020"));
            background.setCornerRadius(
                    16 * getResources().getDisplayMetrics().density
            );

            card.setBackground(background);

            container.addView(card);

            displayedSections++;
        }

        noDetails.setVisibility(
                displayedSections == 0 ? View.VISIBLE : View.GONE
        );

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("Photo Details")
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .create();

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new GradientDrawable() {{
                        setColor(Color.parseColor("#111111"));
                        setCornerRadius(20);
                    }}
            );
        }

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(Color.WHITE);
    }

    private void addDetailRow(
            LinearLayout container,
            String label,
            String value,
            boolean isGps
    ) {
        LinearLayout row = new LinearLayout(requireContext());

        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView labelView = new TextView(requireContext());
        labelView.setText(label);
        labelView.setTextColor(Color.parseColor("#AAAAAA"));
        labelView.setTextSize(13);

        LinearLayout.LayoutParams labelParams =
                new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                );

        row.addView(labelView, labelParams);

        TextView valueView = new TextView(requireContext());
        valueView.setText(value);
        valueView.setTextColor(Color.WHITE);
        valueView.setTextSize(13);
        valueView.setTypeface(null, Typeface.BOLD);
        valueView.setGravity(Gravity.END);

        row.addView(valueView);

        if (isGps) {
            TextView copyButton = new TextView(requireContext());

            copyButton.setText("COPY");
            copyButton.setTextColor(Color.parseColor("#80B9FF"));
            copyButton.setTextSize(11);
            copyButton.setTypeface(null, Typeface.BOLD);
            copyButton.setPadding(12, 8, 0, 8);

            copyButton.setOnClickListener(v -> {
                ClipboardManager clipboard =
                        (ClipboardManager) requireContext()
                                .getSystemService(Context.CLIPBOARD_SERVICE);

                if (clipboard != null) {
                    clipboard.setPrimaryClip(
                            ClipData.newPlainText("GPS Coordinates", value)
                    );

                    Toast.makeText(
                            requireContext(),
                            "GPS coordinates copied",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });

            row.addView(copyButton);
        }

        container.addView(row);
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
        if (photoUris == null || photoUris.isEmpty()) return;

        Uri uri = Uri.parse(photoUris.get(currentIndex));

        // SAF custom-folder photo
        if (DocumentsContract.isDocumentUri(requireContext(), uri)) {
            try {
                boolean deleted = DocumentsContract.deleteDocument(
                        requireContext().getContentResolver(),
                        uri
                );

                if (deleted) {
                    removeDeletedPhoto(uri.toString());
                } else {
                    showMessage("Unable to delete this photo.");
                }

            } catch (SecurityException e) {
                showMessage("Permission required to delete this photo.");
            } catch (Exception e) {
                e.printStackTrace();
                showMessage("Unable to delete this photo.");
            }

            return;
        }

        // MediaStore photo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                android.app.PendingIntent pendingIntent =
                        android.provider.MediaStore.createDeleteRequest(
                                requireContext().getContentResolver(),
                                java.util.Collections.singletonList(uri)
                        );

                pendingDeleteUri = uri.toString();

                deleteLauncher.launch(
                        new IntentSenderRequest.Builder(
                                pendingIntent.getIntentSender()
                        ).build()
                );

            } catch (Exception e) {
                e.printStackTrace();
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