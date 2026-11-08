package com.samarthshukla.camcraft.data.model;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

public class Photo {

    private final Uri uri;
    private final long dateAdded;
    private final String burstId;
    private final List<Uri> burstPhotos;
    private final String fileName;

    // Single photo
    public Photo(Uri uri, long dateAdded) {
        this(uri, dateAdded, null, null, null);
    }

    // Single photo with filename
    public Photo(Uri uri, long dateAdded, String fileName) {
        this(uri, dateAdded, null, null, fileName);
    }

    // Burst group
    public Photo(
            Uri uri,
            long dateAdded,
            String burstId,
            List<Uri> burstPhotos
    ) {
        this(uri, dateAdded, burstId, burstPhotos, null);
    }

    // Complete constructor
    public Photo(
            Uri uri,
            long dateAdded,
            String burstId,
            List<Uri> burstPhotos,
            String fileName
    ) {
        this.uri = uri;
        this.dateAdded = dateAdded;
        this.burstId = burstId;
        this.fileName = fileName;

        this.burstPhotos = burstPhotos == null
                ? new ArrayList<>()
                : new ArrayList<>(burstPhotos);
    }

    public Uri getUri() {
        return uri;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public String getBurstId() {
        return burstId;
    }

    public boolean isBurst() {
        return burstId != null && !burstPhotos.isEmpty();
    }

    public List<Uri> getBurstPhotos() {
        return new ArrayList<>(burstPhotos);
    }

    public int getBurstCount() {
        return burstPhotos.size();
    }

    public String getFileName() {
        return fileName;
    }
}