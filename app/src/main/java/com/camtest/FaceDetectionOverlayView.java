package com.camtest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class FaceDetectionOverlayView extends View {
    private Rect faceRect;

    public FaceDetectionOverlayView(Context context) {
        super(context);
    }

    public FaceDetectionOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void drawFace(Rect faceRect) {
        this.faceRect = faceRect;
        invalidate(); // Redraw the overlay
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Paint paint = new Paint();
        paint.setColor(Color.RED);
        canvas.drawText("Date Time " , 1, 20, paint);
        if (faceRect != null) {

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1);
            canvas.drawRect(faceRect, paint);
        }
    }

    public void clear() {
        invalidate();
    }
}