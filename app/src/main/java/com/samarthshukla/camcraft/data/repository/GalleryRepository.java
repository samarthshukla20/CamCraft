package com.samarthshukla.camcraft.data.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import com.samarthshukla.camcraft.data.model.Photo;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GalleryRepository {

    private static final String FOLDER = "Pictures/CamCraft";
    private static final String PREFS_NAME = "camcraft_settings";
    private static final String KEY_SAVE_LOCATION = "save_location";

    public List<Photo> getPhotos(Context context) {

        SharedPreferences preferences = context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );

        String customLocation = preferences.getString(
                KEY_SAVE_LOCATION,
                null
        );

        List<Photo> photos;

        if (customLocation != null) {
            photos = getCustomFolderPhotos(
                    context,
                    Uri.parse(customLocation)
            );
        } else {
            photos = getDefaultFolderPhotos(context);
        }

        return groupBurstPhotos(photos);
    }

    // ---------------- DEFAULT MEDIASTORE FOLDER ----------------

    private List<Photo> getDefaultFolderPhotos(Context context) {

        List<Photo> photos = new ArrayList<>();

        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME
        };

        String selection;
        String[] selectionArgs;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            selection = MediaStore.Images.Media.RELATIVE_PATH + "=?";
            selectionArgs = new String[]{FOLDER + "/"};

        } else {

            selection = MediaStore.Images.Media.DATA + " LIKE ?";
            selectionArgs = new String[]{"%/" + FOLDER + "/%"};
        }

        String sortOrder =
                MediaStore.Images.Media.DATE_ADDED + " DESC";

        ContentResolver resolver = context.getContentResolver();

        try (Cursor cursor = resolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
        )) {

            if (cursor != null) {

                int idColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media._ID
                );

                int dateColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DATE_ADDED
                );

                int nameColumn = cursor.getColumnIndexOrThrow(
                        MediaStore.Images.Media.DISPLAY_NAME
                );

                while (cursor.moveToNext()) {

                    long id = cursor.getLong(idColumn);
                    long date = cursor.getLong(dateColumn);
                    String fileName = cursor.getString(nameColumn);

                    Uri photoUri = ContentUris.withAppendedId(
                            collection,
                            id
                    );

                    photos.add(new Photo(
                            photoUri,
                            date,
                            fileName
                    ));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return photos;
    }

    // ---------------- CUSTOM SAF FOLDER ----------------


    private List<Photo> getCustomFolderPhotos(
            Context context,
            Uri treeUri
    ) {

        List<Photo> photos = new ArrayList<>();

        try {

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

            try (Cursor cursor = context.getContentResolver().query(
                    childrenUri,
                    projection,
                    null,
                    null,
                    null
            )) {

                if (cursor == null) return photos;

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

                // Keep the timestamp associated with each Photo.
                List<Long> modifiedDates = new ArrayList<>();

                while (cursor.moveToNext()) {

                    String childDocumentId = cursor.getString(idColumn);
                    String mimeType = cursor.getString(mimeColumn);
                    String fileName = cursor.getString(nameColumn);
                    long modifiedDate = cursor.getLong(dateColumn);

                    if (mimeType == null || !mimeType.startsWith("image/")) {
                        continue;
                    }

                    Uri photoUri =
                            DocumentsContract.buildDocumentUriUsingTree(
                                    treeUri,
                                    childDocumentId
                            );

                    long date = modifiedDate / 1000L;

                    photos.add(new Photo(
                            photoUri,
                            date,
                            fileName
                    ));
                    modifiedDates.add(modifiedDate);
                }

                // Sort newest first, keeping dates paired with their photos.
                List<Integer> order = new ArrayList<>();

                for (int i = 0; i < photos.size(); i++) {
                    order.add(i);
                }

                order.sort((first, second) ->
                        Long.compare(
                                modifiedDates.get(second),
                                modifiedDates.get(first)
                        )
                );

                List<Photo> sortedPhotos = new ArrayList<>();

                for (int index : order) {
                    sortedPhotos.add(photos.get(index));
                }

                return sortedPhotos;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return photos;
    }

    private List<Photo> groupBurstPhotos(List<Photo> inputPhotos) {

        Map<String, List<Photo>> groups = new LinkedHashMap<>();
        List<Photo> result = new ArrayList<>();

        Pattern pattern = Pattern.compile(
                "^CamCraft_BURST_(\\d+)_\\d+\\.(jpg|jpeg|png)$",
                Pattern.CASE_INSENSITIVE
        );

        for (Photo photo : inputPhotos) {

            String fileName = photo.getFileName();

            if (fileName == null) {
                result.add(photo);
                continue;
            }

            Matcher matcher = pattern.matcher(fileName);

            if (matcher.matches()) {

                String burstId = matcher.group(1);

                groups.computeIfAbsent(
                        burstId,
                        key -> new ArrayList<>()
                ).add(photo);

            } else {
                result.add(photo);
            }
        }

        // Convert each burst into one gallery item.
        for (Map.Entry<String, List<Photo>> entry : groups.entrySet()) {

            List<Photo> burst = entry.getValue();

            burst.sort((first, second) ->
                    Long.compare(
                            second.getDateAdded(),
                            first.getDateAdded()
                    )
            );

            List<Uri> burstUris = new ArrayList<>();

            for (Photo photo : burst) {
                burstUris.add(photo.getUri());
            }

            Photo coverPhoto = burst.get(0);

            result.add(new Photo(
                    coverPhoto.getUri(),
                    coverPhoto.getDateAdded(),
                    entry.getKey(),
                    burstUris,
                    coverPhoto.getFileName()
            ));
        }

        // Keep all gallery items newest first.
        result.sort((first, second) ->
                Long.compare(
                        second.getDateAdded(),
                        first.getDateAdded()
                )
        );

        return result;
    }

}
