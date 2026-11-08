
package com.samarthshukla.camcraft.ui.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.Manifest;
import android.content.pm.PackageManager;
import android.widget.Switch;

import androidx.core.content.ContextCompat;

import com.samarthshukla.camcraft.R;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "camcraft_settings";

    private static final String KEY_FORMAT = "image_format";
    private static final String KEY_RESOLUTION = "image_resolution";
    public static final String KEY_SAVE_LOCATION = "save_location";

    private SharedPreferences preferences;

    private TextView locationText;
    public static final String KEY_GEOTAGGING = "geotagging_enabled";

    private final ActivityResultLauncher<Uri> folderPickerLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.OpenDocumentTree(),
                    uri -> {
                        if (uri == null || preferences == null) return;

                        try {
                            final int flags =
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

                            requireContext()
                                    .getContentResolver()
                                    .takePersistableUriPermission(uri, flags);

                            preferences.edit()
                                    .putString(KEY_SAVE_LOCATION, uri.toString())
                                    .apply();

                            updateLocationText(uri);

                        } catch (SecurityException e) {
                            e.printStackTrace();

                            if (locationText != null) {
                                locationText.setText(
                                        "Unable to access selected folder"
                                );
                            }
                        }
                    }
            );

    public SettingsFragment() {
        super(R.layout.fragment_settings);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        preferences = requireContext().getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        setupImageFormat(view);
        setupResolution(view);
        setupSaveLocation(view);
        setupGeotagging(view);
    }

    private void setupImageFormat(View view) {

        RadioGroup radioGroup = view.findViewById(R.id.radioImageFormat);

        String savedFormat = preferences.getString(KEY_FORMAT, "JPEG");

        radioGroup.check(
                savedFormat.equals("PNG")
                        ? R.id.radioPng
                        : R.id.radioJpeg
        );

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {

            String format = checkedId == R.id.radioPng ? "PNG" : "JPEG";

            preferences.edit()
                    .putString(KEY_FORMAT, format)
                    .apply();
        });
    }

    private void setupResolution(View view) {

        RadioGroup radioGroup = view.findViewById(R.id.radioResolution);

        String savedResolution = preferences.getString(
                KEY_RESOLUTION,
                "HIGH"
        );

        int selectedId;

        switch (savedResolution) {
            case "MEDIUM":
                selectedId = R.id.radioMedium;
                break;

            case "LOW":
                selectedId = R.id.radioLow;
                break;

            default:
                selectedId = R.id.radioHigh;
                break;
        }

        radioGroup.check(selectedId);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {

            String resolution;

            if (checkedId == R.id.radioMedium) {
                resolution = "MEDIUM";
            } else if (checkedId == R.id.radioLow) {
                resolution = "LOW";
            } else {
                resolution = "HIGH";
            }

            preferences.edit()
                    .putString(KEY_RESOLUTION, resolution)
                    .apply();
        });
    }

    private void setupGeotagging(View view) {

        Switch geotaggingSwitch = view.findViewById(R.id.switchGeotagging);
        TextView statusText = view.findViewById(R.id.txtGeotaggingStatus);

        boolean enabled = preferences.getBoolean(KEY_GEOTAGGING, false);

        geotaggingSwitch.setChecked(enabled);
        updateGeotaggingStatus(statusText, enabled);

        geotaggingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {

                boolean hasFineLocation =
                        ContextCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED;

                boolean hasCoarseLocation =
                        ContextCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.ACCESS_COARSE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED;

                if (!hasFineLocation && !hasCoarseLocation) {
                    geotaggingSwitch.setChecked(false);

                    requestPermissions(
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            1001
                    );

                    return;
                }
            }

            preferences.edit()
                    .putBoolean(KEY_GEOTAGGING, isChecked)
                    .apply();

            updateGeotaggingStatus(statusText, isChecked);
        });
    }

    private void updateGeotaggingStatus(TextView statusText, boolean enabled) {

        if (enabled) {
            statusText.setText("Geotagging enabled");
        } else {
            statusText.setText("Disabled");
        }
    }

    private void setupSaveLocation(View view) {

        locationText = view.findViewById(R.id.txtSaveLocation);

        TextView chooseButton = view.findViewById(R.id.btnChooseLocation);

        String savedUri = preferences.getString(KEY_SAVE_LOCATION, null);

        if (savedUri == null) {
            locationText.setText("Pictures/CamCraft (Default)");
        } else {
            updateLocationText(Uri.parse(savedUri));
        }

        chooseButton.setOnClickListener(v ->
                folderPickerLauncher.launch(null)
        );
    }

    private void updateLocationText(Uri uri) {

        if (locationText == null) return;

        try {
            String folderPath = DocumentsContract.getTreeDocumentId(uri);

            folderPath = folderPath.replace(":", "/");

            locationText.setText(folderPath);

        } catch (Exception e) {
            locationText.setText("Custom folder selected");
        }
    }
}