
package com.samarthshukla.camcraft.data.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.samarthshukla.camcraft.data.model.Photo;

import java.util.ArrayList;
import java.util.List;

public class GalleryRepository {

    private static final String FOLDER = "Pictures/CamCraft";

    public List<Photo> getPhotos(Context context) {
        List<Photo> photos = new ArrayList<>();

        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
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

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idColumn);
                    long date = cursor.getLong(dateColumn);

                    Uri photoUri = ContentUris.withAppendedId(
                            collection, id
                    );

                    photos.add(new Photo(photoUri, date));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return photos;
    }
}
