package com.sih.deadreckoning.UI;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;

import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.sih.deadreckoning.Engine.HybridDeadReckoningEngine.PositionPoint;

import java.util.List;

public class TrajectoryCanvasView extends View {

    private Paint gridPaint;
    private Paint pathGnssPaint;
    private Paint pathDeadReckoningPaint;
    private Paint userDotPaint;
    private Paint headingArrowPaint;
    private Paint textPaint;

    private List<PositionPoint> points;
    private float currentHeadingRad = 0.0f;

    // View Transformation (Pan & Zoom)
    private float scaleFactor = 35.0f; // pixels per meter
    private float offsetX = 0.0f;
    private float offsetY = 0.0f;
    private float lastTouchX, lastTouchY;

    public TrajectoryCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#1E293B"));
        gridPaint.setStrokeWidth(2f);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        pathGnssPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pathGnssPaint.setColor(Color.parseColor("#22C55E"));
        pathGnssPaint.setStrokeWidth(6f);
        pathGnssPaint.setStyle(Paint.Style.STROKE);

        pathDeadReckoningPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pathDeadReckoningPaint.setColor(Color.parseColor("#38BDF8"));
        pathDeadReckoningPaint.setStrokeWidth(7f);
        pathDeadReckoningPaint.setStyle(Paint.Style.STROKE);

        userDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        userDotPaint.setColor(Color.parseColor("#EF4444"));
        userDotPaint.setStyle(Paint.Style.FILL);

        headingArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        headingArrowPaint.setColor(Color.parseColor("#F59E0B"));
        headingArrowPaint.setStrokeWidth(5f);
        headingArrowPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#94A3B8"));
        textPaint.setTextSize(26f);
    }

    public void updateTrajectory(List<PositionPoint> points, float headingRad) {
        this.points = points;
        this.currentHeadingRad = headingRad;
        invalidate(); // Trigger canvas redraw
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();

        float centerX = width / 2.0f + offsetX;
        float centerY = height / 2.0f + offsetY;

        // Draw background grid lines
        float stepPx = scaleFactor * 5.0f; // Grid square every 5 meters
        for (float x = centerX % stepPx; x < width; x += stepPx) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }
        for (float y = centerY % stepPx; y < height; y += stepPx) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        // Draw coordinate origin marker (0,0)
        canvas.drawText("(0,0) Origin", centerX + 10, centerY - 10, textPaint);

        if (points == null || points.isEmpty()) return;

        // Draw trajectory path
        Path pathGnss = new Path();
        Path pathDr = new Path();

        PositionPoint first = points.get(0);
        float startPxX = centerX + (float) (first.x * scaleFactor);
        float startPxY = centerY - (float) (first.y * scaleFactor);

        pathGnss.moveTo(startPxX, startPxY);
        pathDr.moveTo(startPxX, startPxY);

        for (int i = 1; i < points.size(); i++) {
            PositionPoint pt = points.get(i);
            float pxX = centerX + (float) (pt.x * scaleFactor);
            float pxY = centerY - (float) (pt.y * scaleFactor);

            if (pt.isGnssActive) {
                pathGnss.lineTo(pxX, pxY);
                pathDr.moveTo(pxX, pxY);
            } else {
                pathDr.lineTo(pxX, pxY);
                pathGnss.moveTo(pxX, pxY);
            }
        }

        canvas.drawPath(pathGnss, pathGnssPaint);
        canvas.drawPath(pathDr, pathDeadReckoningPaint);

        // Draw current user position
        PositionPoint current = points.get(points.size() - 1);
        float currentPxX = centerX + (float) (current.x * scaleFactor);
        float currentPxY = centerY - (float) (current.y * scaleFactor);

        canvas.drawCircle(currentPxX, currentPxY, 14f, userDotPaint);

        // Draw heading arrow vector
        float arrowLen = 45f;
        float arrowEndX = currentPxX + (float) (arrowLen * Math.cos(currentHeadingRad));
        float arrowEndY = currentPxY - (float) (arrowLen * Math.sin(currentHeadingRad));
        canvas.drawLine(currentPxX, currentPxY, arrowEndX, arrowEndY, headingArrowPaint);

        // Display current scale legend
        canvas.drawText(String.format("Scale: 1 grid = 5m | Mode: %s", current.isGnssActive ? "GNSS GPS" : "AI Dead Reckoning"), 20, height - 30, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = x;
                lastTouchY = y;
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = x - lastTouchX;
                float dy = y - lastTouchY;
                offsetX += dx;
                offsetY += dy;
                lastTouchX = x;
                lastTouchY = y;
                invalidate();
                break;
        }
        return true;
    }
}
