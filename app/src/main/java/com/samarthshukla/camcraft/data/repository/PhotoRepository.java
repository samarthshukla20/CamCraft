
package com.samarthshukla.camcraft.data.repository;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

public class PhotoRepository {

    public int deletePhoto(Context context, Uri uri) {
        ContentResolver resolver = context.getContentResolver();

        return resolver.delete(uri, null, null);
    }
}