
package com.samarthshukla.camcraft.ui.camera;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.samarthshukla.camcraft.data.model.CameraUiState;

public class CameraViewModel extends ViewModel {

    private final MutableLiveData<CameraUiState> uiState =
            new MutableLiveData<>(
                    new CameraUiState(CameraUiState.FLASH_AUTO, false)
            );

    public LiveData<CameraUiState> getUiState() {
        return uiState;
    }

    public void cycleFlash() {
        CameraUiState current = uiState.getValue();
        if (current == null) return;

        int next = (current.getFlashMode() + 1) % 3;

        uiState.setValue(
                new CameraUiState(next, current.isFrontCamera())
        );
    }

    public void toggleCamera() {
        CameraUiState current = uiState.getValue();
        if (current == null) return;

        uiState.setValue(
                new CameraUiState(
                        current.getFlashMode(),
                        !current.isFrontCamera()
                )
        );
    }
}
