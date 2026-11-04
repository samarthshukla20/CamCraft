
package com.samarthshukla.camcraft.data.model;

import android.net.Uri;

public class Photo {

    private final Uri uri;
    private final long dateAdded;

    public Photo(Uri uri, long dateAdded) {
        this.uri = uri;
        this.dateAdded = dateAdded;
    }

    public Uri getUri() {
        return uri;
    }

    public long getDateAdded() {
        return dateAdded;
    }
}
