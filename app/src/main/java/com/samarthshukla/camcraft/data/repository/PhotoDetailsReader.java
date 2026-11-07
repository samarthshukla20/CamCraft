
package com.samarthshukla.camcraft.data.repository;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.exifinterface.media.ExifInterface;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class PhotoDetailsReader {

    public static Map<String, String> read(
            Context context,
            Uri uri
    ) {
        Map<String, String> details = new LinkedHashMap<>();

        try {
            // File information
            String fileName = null;
            long fileSize = -1;

            try (Cursor cursor = context.getContentResolver().query(
                    uri,
                    null,
                    null,
                    null,
                    null
            )) {
                if (cursor != null && cursor.moveToFirst()) {

                    int nameIndex = cursor.getColumnIndex(
                            OpenableColumns.DISPLAY_NAME
                    );

                    int sizeIndex = cursor.getColumnIndex(
                            OpenableColumns.SIZE
                    );

                    if (nameIndex >= 0) {
                        fileName = cursor.getString(nameIndex);
                    }

                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex);
                    }
                }
            }

            if (fileName != null) {
                details.put("File name", fileName);
            }

            if (fileSize >= 0) {
                details.put("File size", formatFileSize(fileSize));
            }

            // EXIF information
            try (InputStream inputStream =
                         context.getContentResolver().openInputStream(uri)) {

                if (inputStream == null) {
                    return details;
                }

                ExifInterface exif = new ExifInterface(inputStream);

                add(details, "Camera make",
                        exif.getAttribute(ExifInterface.TAG_MAKE));

                add(details, "Camera model",
                        exif.getAttribute(ExifInterface.TAG_MODEL));

                String width = exif.getAttribute(
                        ExifInterface.TAG_IMAGE_WIDTH
                );

                String height = exif.getAttribute(
                        ExifInterface.TAG_IMAGE_LENGTH
                );

                if (width != null && height != null) {
                    details.put("Dimensions", width + " × " + height);
                }

                add(details, "Date captured",
                        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL));

                add(details, "ISO",
                        exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS));

                add(details, "Shutter speed",
                        exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME));

                add(details, "Aperture",
                        formatAperture(exif.getAttribute(
                                ExifInterface.TAG_F_NUMBER
                        )));

                add(details, "Focal length",
                        exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH));

                add(details, "Flash",
                        formatFlash(exif.getAttribute(
                                ExifInterface.TAG_FLASH
                        )));

                add(details, "White balance",
                        formatWhiteBalance(exif.getAttribute(
                                ExifInterface.TAG_WHITE_BALANCE
                        )));

                // GPS information
                float[] coordinates = new float[2];

                if (exif.getLatLong(coordinates)) {
                    details.put(
                            "GPS location",
                            String.format(
                                    Locale.getDefault(),
                                    "%.6f, %.6f",
                                    coordinates[0],
                                    coordinates[1]
                            )
                    );
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return details;
    }

    private static void add(
            Map<String, String> details,
            String key,
            String value
    ) {
        if (value != null && !value.trim().isEmpty()) {
            details.put(key, value);
        }
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        if (bytes < 1024 * 1024) {
            return String.format(
                    Locale.getDefault(),
                    "%.2f KB",
                    bytes / 1024.0
            );
        }

        return String.format(
                Locale.getDefault(),
                "%.2f MB",
                bytes / (1024.0 * 1024.0)
        );
    }

    private static String formatAperture(String value) {
        if (value == null) return null;

        try {
            return "f/" + value;
        } catch (Exception e) {
            return value;
        }
    }

    private static String formatFlash(String value) {
        if (value == null) return null;

        try {
            int flash = Integer.parseInt(value);

            return (flash & 1) != 0 ? "Fired" : "Did not fire";

        } catch (NumberFormatException e) {
            return value;
        }
    }

    private static String formatWhiteBalance(String value) {
        if (value == null) return null;

        if ("0".equals(value)) return "Auto";
        if ("1".equals(value)) return "Manual";

        return value;
    }
}
