
package com.samarthshukla.camcraft.ui.gallery;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.samarthshukla.camcraft.data.model.Photo;
import com.samarthshukla.camcraft.data.repository.GalleryRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GalleryViewModel extends AndroidViewModel {

    private final MutableLiveData<List<Photo>> photos =
            new MutableLiveData<>();

    private final GalleryRepository repository =
            new GalleryRepository();

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    public GalleryViewModel(@NonNull Application application) {
        super(application);
    }

    public LiveData<List<Photo>> getPhotos() {
        return photos;
    }

    public void loadPhotos() {
        executor.execute(() -> {
            List<Photo> result = repository.getPhotos(
                    getApplication()
            );

            photos.postValue(result);
        });
    }

    @Override
    protected void onCleared() {
        executor.shutdown();
        super.onCleared();
    }
}
