package com.sih.deadreckoning.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class MapCanvasView extends View {

    public static class TrackPoint {
        public double lat, lon;
        public boolean isGps;
        public TrackPoint(double lat, double lon, boolean isGps) {
            this.lat = lat; this.lon = lon; this.isGps = isGps;
        }
    }

    private final List<TrackPoint> track = new ArrayList<>();
    private double centerLat = 0, centerLon = 0;
    private float headingDeg = 0;
    private float scalePxPerMeter = 40f;
    private float offsetX = 0, offsetY = 0;
    private float lastTouchX, lastTouchY;
    private boolean followUser = true;

    // Paints
    private Paint bgPaint, gridPaint, gridTextPaint;
    private Paint gpsTrackPaint, drTrackPaint;
    private Paint userDotPaint, userDotBorderPaint, userPulsePaint;
    private Paint compassPaint, compassNeedlePaint, compassNPaint;
    private Paint statusPaint, statusBgPaint, scalePaint;
    private Paint accuracyPaint;

    private float pulseRadius = 0f;
    private float pulseAlpha = 200f;
    private ScaleGestureDetector scaleDetector;

    // Status bar values
    private String modeLabelText = "GPS Active";
    private String speedText = "0.0 km/h";
    private String distText = "0.0 m";
    private String stepsText = "0";
    private String coordText = "0.000000°N, 0.000000°E";
    private int modeColor = Color.parseColor("#4CAF50");

    public MapCanvasView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        bgPaint = makePaint(Color.parseColor("#1A1A2E"), Paint.Style.FILL, 1);

        gridPaint = makePaint(Color.parseColor("#2D2D44"), Paint.Style.STROKE, 1.5f);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{8, 16}, 0));

        gridTextPaint = makePaint(Color.parseColor("#404060"), Paint.Style.FILL, 1);
        gridTextPaint.setTextSize(22f);
        gridTextPaint.setTypeface(Typeface.MONOSPACE);

        gpsTrackPaint = makePaint(Color.parseColor("#4CAF50"), Paint.Style.STROKE, 6f);
        gpsTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        gpsTrackPaint.setStrokeJoin(Paint.Join.ROUND);

        drTrackPaint = makePaint(Color.parseColor("#2196F3"), Paint.Style.STROKE, 6f);
        drTrackPaint.setStrokeCap(Paint.Cap.ROUND);
        drTrackPaint.setPathEffect(new DashPathEffect(new float[]{20, 8}, 0));

        userDotPaint = makePaint(Color.parseColor("#2196F3"), Paint.Style.FILL, 1);
        userDotBorderPaint = makePaint(Color.WHITE, Paint.Style.FILL, 1);
        userPulsePaint = makePaint(Color.parseColor("#2196F3"), Paint.Style.FILL, 1);
        accuracyPaint = makePaint(Color.parseColor("#332196F3"), Paint.Style.FILL, 1);

        compassPaint = makePaint(Color.parseColor("#252540"), Paint.Style.FILL, 1);
        compassNeedlePaint = makePaint(Color.parseColor("#F44336"), Paint.Style.FILL_AND_STROKE, 2f);
        compassNPaint = makePaint(Color.WHITE, Paint.Style.FILL, 1);
        compassNPaint.setTextSize(24f);
        compassNPaint.setTextAlign(Paint.Align.CENTER);
        compassNPaint.setTypeface(Typeface.DEFAULT_BOLD);

        statusPaint = makePaint(Color.WHITE, Paint.Style.FILL, 1);
        statusPaint.setTextSize(28f);
        statusPaint.setTypeface(Typeface.DEFAULT_BOLD);

        statusBgPaint = makePaint(Color.parseColor("#CC0D1B2E"), Paint.Style.FILL, 1);

        scalePaint = makePaint(Color.parseColor("#AAAAAA"), Paint.Style.STROKE, 3f);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scalePxPerMeter *= detector.getScaleFactor();
                scalePxPerMeter = Math.max(5f, Math.min(500f, scalePxPerMeter));
                invalidate();
                return true;
            }
        });

        // Pulse animation
        post(pulseAnimator);
    }

    private Paint makePaint(int color, Paint.Style style, float strokeWidth) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(style);
        p.setStrokeWidth(strokeWidth);
        return p;
    }

    private Runnable pulseAnimator = new Runnable() {
        @Override public void run() {
            pulseRadius += 1.5f;
            pulseAlpha -= 3f;
            if (pulseRadius > 60f) { pulseRadius = 0f; pulseAlpha = 180f; }
            userPulsePaint.setAlpha((int) Math.max(0, pulseAlpha));
            invalidate();
            postDelayed(this, 16);
        }
    };

    public void updateLocation(double lat, double lon, float heading, String mode,
                                float speedMs, double distM, int steps, int modeClr) {
        this.centerLat = lat;
        this.centerLon = lon;
        this.headingDeg = heading;
        this.modeColor = modeClr;
        this.modeLabelText = mode;
        this.speedText = String.format("%.1f km/h", speedMs * 3.6f);
        this.distText = distM >= 1000
                ? String.format("%.2f km", distM / 1000.0)
                : String.format("%.0f m", distM);
        this.stepsText = String.valueOf(steps);
        this.coordText = String.format("%.6f°%s, %.6f°%s",
                Math.abs(lat), lat >= 0 ? "N" : "S",
                Math.abs(lon), lon >= 0 ? "E" : "W");
        if (followUser) { offsetX = 0; offsetY = 0; }
        invalidate();
    }

    public void addTrackPoint(double lat, double lon, boolean isGps) {
        track.add(new TrackPoint(lat, lon, isGps));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        float cx = w / 2f + offsetX;
        float cy = h / 2f + offsetY;

        // Background
        canvas.drawRect(0, 0, w, h, bgPaint);

        // Grid lines
        drawGrid(canvas, cx, cy, w, h);

        // Track history
        drawTrack(canvas, cx, cy);

        // Accuracy circle
        canvas.drawCircle(cx, cy, 25f, accuracyPaint);

        // Pulse ring
        canvas.drawCircle(cx, cy, pulseRadius, userPulsePaint);

        // User dot (Google Maps style: white border + blue dot + heading cone)
        drawUserDot(canvas, cx, cy);

        // Compass
        drawCompass(canvas, w, h);

        // Scale bar
        drawScaleBar(canvas, w, h);

        // Status overlay at bottom
        drawStatusOverlay(canvas, w, h);
    }

    private void drawGrid(Canvas canvas, float cx, float cy, int w, int h) {
        // Adaptive grid: 10m, 50m, or 100m based on scale
        float gridM;
        if (scalePxPerMeter > 100) gridM = 5;
        else if (scalePxPerMeter > 20) gridM = 20;
        else if (scalePxPerMeter > 5) gridM = 100;
        else gridM = 500;
        float gridPx = gridM * scalePxPerMeter;

        float startX = cx % gridPx;
        float startY = cy % gridPx;

        for (float x = startX; x < w; x += gridPx) canvas.drawLine(x, 0, x, h, gridPaint);
        for (float y = startY; y < h; y += gridPx) canvas.drawLine(0, y, w, y, gridPaint);

        // Label grid distances
        for (float x = startX; x < w; x += gridPx) {
            float mDist = (x - cx) / scalePxPerMeter;
            canvas.drawText(String.format("%.0fm", Math.abs(mDist)), x + 4, 30, gridTextPaint);
        }
    }

    private void drawTrack(Canvas canvas, float cx, float cy) {
        if (track.size() < 2) return;
        Path gpsPath = new Path();
        Path drPath = new Path();
        boolean firstGps = true, firstDr = true;

        for (int i = 0; i < track.size(); i++) {
            TrackPoint tp = track.get(i);
            float px = cx + (float) lonToMeters(tp.lon, centerLon) * scalePxPerMeter;
            float py = cy - (float) latToMeters(tp.lat, centerLat) * scalePxPerMeter;

            if (tp.isGps) {
                if (firstGps) { gpsPath.moveTo(px, py); firstGps = false; }
                else gpsPath.lineTo(px, py);
            } else {
                if (firstDr) { drPath.moveTo(px, py); firstDr = false; }
                else drPath.lineTo(px, py);
            }
        }
        canvas.drawPath(gpsPath, gpsTrackPaint);
        canvas.drawPath(drPath, drTrackPaint);
    }

    private void drawUserDot(Canvas canvas, float cx, float cy) {
        float hdg = headingDeg;

        // Heading cone (blue transparent)
        Paint conePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        conePaint.setColor(Color.parseColor("#442196F3"));
        conePaint.setStyle(Paint.Style.FILL);
        canvas.save();
        canvas.rotate(hdg, cx, cy);
        RectF coneRect = new RectF(cx - 50, cy - 50, cx + 50, cy + 50);
        canvas.drawArc(coneRect, -90 - 30, 60, true, conePaint);
        canvas.restore();

        // White border
        canvas.drawCircle(cx, cy, 20f, userDotBorderPaint);
        // Blue center
        canvas.drawCircle(cx, cy, 15f, userDotPaint);
        // Center white core
        Paint core = new Paint(Paint.ANTI_ALIAS_FLAG);
        core.setColor(Color.WHITE);
        core.setStyle(Paint.Style.FILL);
        canvas.drawCircle(cx, cy, 6f, core);
    }

    private void drawCompass(Canvas canvas, int w, int h) {
        float cx = w - 70, cy = 100;
        canvas.drawCircle(cx, cy, 55, compassPaint);

        canvas.save();
        canvas.rotate(-headingDeg, cx, cy);

        // North needle (red)
        Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
        red.setColor(Color.parseColor("#F44336"));
        red.setStyle(Paint.Style.FILL);
        float[] northX = {cx, cx - 8, cx + 8};
        float[] northY = {cy - 40, cy + 5, cy + 5};
        Path northPath = new Path();
        northPath.moveTo(northX[0], northY[0]);
        northPath.lineTo(northX[1], northY[1]);
        northPath.lineTo(northX[2], northY[2]);
        northPath.close();
        canvas.drawPath(northPath, red);

        // South needle (gray)
        Paint gray = new Paint(Paint.ANTI_ALIAS_FLAG);
        gray.setColor(Color.parseColor("#888888"));
        gray.setStyle(Paint.Style.FILL);
        float[] southX = {cx, cx - 8, cx + 8};
        float[] southY = {cy + 40, cy - 5, cy - 5};
        Path southPath = new Path();
        southPath.moveTo(southX[0], southY[0]);
        southPath.lineTo(southX[1], southY[1]);
        southPath.lineTo(southX[2], southY[2]);
        southPath.close();
        canvas.drawPath(southPath, gray);

        canvas.restore();

        // N label
        canvas.drawText("N", cx, cy - 50, compassNPaint);
    }

    private void drawScaleBar(Canvas canvas, int w, int h) {
        float barLenM = 50f; // 50 meters
        float barPx = barLenM * scalePxPerMeter;
        if (barPx > w * 0.4f) { barLenM = 10; barPx = barLenM * scalePxPerMeter; }
        if (barPx < 40) { barLenM = 200; barPx = barLenM * scalePxPerMeter; }

        float left = 40, top = h - 130;
        canvas.drawLine(left, top, left + barPx, top, scalePaint);
        canvas.drawLine(left, top - 8, left, top + 8, scalePaint);
        canvas.drawLine(left + barPx, top - 8, left + barPx, top + 8, scalePaint);

        Paint lbl = new Paint(Paint.ANTI_ALIAS_FLAG);
        lbl.setColor(Color.WHITE);
        lbl.setTextSize(24f);
        lbl.setTextAlign(Paint.Align.CENTER);
        canvas.drawText((int) barLenM + " m", left + barPx / 2, top - 16, lbl);
    }

    private void drawStatusOverlay(Canvas canvas, int w, int h) {
        RectF bgRect = new RectF(0, h - 160, w, h);
        canvas.drawRect(bgRect, statusBgPaint);

        // Mode badge
        Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
        badge.setColor(modeColor);
        badge.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(16, h - 150, 250, h - 110), 20, 20, badge);

        Paint badgeTxt = new Paint(Paint.ANTI_ALIAS_FLAG);
        badgeTxt.setColor(Color.WHITE);
        badgeTxt.setTextSize(24f);
        badgeTxt.setTypeface(Typeface.DEFAULT_BOLD);
        badgeTxt.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(modeLabelText, 133, h - 123, badgeTxt);

        // Coordinate
        Paint coord = new Paint(Paint.ANTI_ALIAS_FLAG);
        coord.setColor(Color.parseColor("#CCFFFFFF"));
        coord.setTextSize(22f);
        canvas.drawText(coordText, 16, h - 95, coord);

        // Speed | Distance | Steps
        Paint stat = new Paint(Paint.ANTI_ALIAS_FLAG);
        stat.setColor(Color.WHITE);
        stat.setTextSize(28f);
        stat.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText("⚡ " + speedText, 16, h - 55, stat);
        canvas.drawText("📍 " + distText, w / 3f, h - 55, stat);
        canvas.drawText("👣 " + stepsText + " steps", 2 * w / 3f, h - 55, stat);
    }

    private double latToMeters(double lat, double refLat) {
        return (lat - refLat) * 111320.0;
    }

    private double lonToMeters(double lon, double refLon) {
        return (lon - refLon) * 111320.0 * Math.cos(Math.toRadians(centerLat));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getX();
                lastTouchY = event.getY();
                followUser = false;
                break;
            case MotionEvent.ACTION_MOVE:
                if (!scaleDetector.isInProgress()) {
                    offsetX += event.getX() - lastTouchX;
                    offsetY += event.getY() - lastTouchY;
                    lastTouchX = event.getX();
                    lastTouchY = event.getY();
                    invalidate();
                }
                break;
        }
        return true;
    }

    public void setFollowUser(boolean follow) { this.followUser = follow; }
}
