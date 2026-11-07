package com.samarthshukla.camcraft.ui.camera;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

public class CameraGridView extends View {

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public CameraGridView(Context context) {
        super(context);
        init();
    }

    public CameraGridView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CameraGridView(
            Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr
    ) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setWillNotDraw(false);

        gridPaint.setColor(0x99FFFFFF);
        gridPaint.setStrokeWidth(
                getResources().getDisplayMetrics().density
        );
        gridPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();

        // Vertical lines
        canvas.drawLine(width / 3f, 0, width / 3f, height, gridPaint);
        canvas.drawLine(2f * width / 3f, 0, 2f * width / 3f, height, gridPaint);

        // Horizontal lines
        canvas.drawLine(0, height / 3f, width, height / 3f, gridPaint);
        canvas.drawLine(0, 2f * height / 3f, width, 2f * height / 3f, gridPaint);
    }
}