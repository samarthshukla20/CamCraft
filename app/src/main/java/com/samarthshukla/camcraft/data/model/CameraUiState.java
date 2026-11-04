
package com.samarthshukla.camcraft.data.model;

public class CameraUiState {

    public static final int FLASH_AUTO = 0;
    public static final int FLASH_ON = 1;
    public static final int FLASH_OFF = 2;

    private final int flashMode;
    private final boolean frontCamera;

    public CameraUiState(int flashMode, boolean frontCamera) {
        this.flashMode = flashMode;
        this.frontCamera = frontCamera;
    }

    public int getFlashMode() {
        return flashMode;
    }

    public boolean isFrontCamera() {
        return frontCamera;
    }
}
