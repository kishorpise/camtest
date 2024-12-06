package com.camtest;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class TimeStampOverlayView extends View {

    private Paint paint = new Paint();
    private   Date currentDate = new Date();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd MM yy - hh:mm:ss" , Locale.ENGLISH);
    public TimeStampOverlayView(Context context) {
        super(context);
    }

    public TimeStampOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void drawStamp() {

        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        paint.setColor(Color.RED);

        String formattedDateTime = dateFormat.format(currentDate);
        canvas.drawText(formattedDateTime , 5, 20, paint);
    }


}