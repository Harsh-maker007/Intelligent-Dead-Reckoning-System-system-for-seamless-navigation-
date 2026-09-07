package com.sih.deadreckoning.engine;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.sih.deadreckoning.filter.EKFPositionFilter;
import com.sih.deadreckoning.filter.MadgwickFilter;

import java.util.ArrayList;
import java.util.List;

public class NavigationEngine implements SensorEventListener {

    public enum NavMode { GPS, DEAD_RECKONING }

    public interface NavigationListener {
        void onLocationUpdate(double lat, double lon, float accuracy,
                              float speedMs, float headingDeg, NavMode mode,
                              int stepCount, double totalDistanceM);
        void onModeChanged(NavMode newMode);
    }

    // Sensors
    private SensorManager sensorManager;
    private Sensor accelSensor, gyroSensor, magSensor, rotVecSensor, stepSensor, pressureSensor;

    // GPS
    private LocationManager locationManager;

    // Filters
    private MadgwickFilter madgwick = new MadgwickFilter(0.033f);
    private EKFPositionFilter ekf = new EKFPositionFilter();

    // Raw sensor data
    private float[] accel = new float[3];
    private float[] gyro  = new float[3];
    private float[] mag   = new float[3];
    private float[] rotVec = new float[5];

    // State
    private NavMode currentMode = NavMode.GPS;
    private double currentLat = 0, currentLon = 0;
    private float  currentHeadingDeg = 0;
    private float  currentSpeedMs = 0;
    private int    stepCount = 0;
    private double totalDistanceM = 0;

    // Local EKF coordinates in meters from first GPS fix
    private double originLat = 0, originLon = 0;
    private boolean hasOrigin = false;

    // GPS timeout — switch to DR if no GPS fix in 3 seconds
    private long lastGpsTimestampMs = 0;
    private static final long GPS_TIMEOUT_MS = 3000;

    // Timing
    private long lastSensorTimestampNs = 0;

    // Step detection
    private boolean stepJustDetected = false;
    private float maxAccelMag = 0f, minAccelMag = 20f;
    private float[] accelWindow = new float[20];
    private int accelWindowIdx = 0;

    // Listener
    private NavigationListener listener;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Context context;

    public NavigationEngine(Context context, NavigationListener listener) {
        this.context = context;
        this.listener = listener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

        accelSensor   = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magSensor     = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        rotVecSensor  = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        stepSensor    = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        pressureSensor= sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
    }

    public void start() {
        // Register sensors at GAME rate (50Hz)
        if (accelSensor  != null) sensorManager.registerListener(this, accelSensor,  SensorManager.SENSOR_DELAY_GAME);
        if (gyroSensor   != null) sensorManager.registerListener(this, gyroSensor,   SensorManager.SENSOR_DELAY_GAME);
        if (magSensor    != null) sensorManager.registerListener(this, magSensor,     SensorManager.SENSOR_DELAY_GAME);
        if (rotVecSensor != null) sensorManager.registerListener(this, rotVecSensor,  SensorManager.SENSOR_DELAY_GAME);
        if (stepSensor   != null) sensorManager.registerListener(this, stepSensor,    SensorManager.SENSOR_DELAY_FASTEST);

        // GPS: update every 1 second or 1 meter
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 1f, gpsListener);
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 2000, 5f, gpsListener);
        } catch (SecurityException e) {
            // Permission not granted yet
        }

        // Poll for mode timeout
        mainHandler.postDelayed(modeCheckRunnable, 1000);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
        try { locationManager.removeUpdates(gpsListener); } catch (Exception ignored) {}
        mainHandler.removeCallbacks(modeCheckRunnable);
    }

    // ─── GPS Listener ─────────────────────────────────────────────────────────
    private LocationListener gpsListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location loc) {
            lastGpsTimestampMs = System.currentTimeMillis();

            currentLat = loc.getLatitude();
            currentLon = loc.getLongitude();
            if (loc.hasSpeed()) currentSpeedMs = loc.getSpeed();
            if (loc.hasBearing()) currentHeadingDeg = loc.getBearing();

            if (!hasOrigin) {
                originLat = currentLat;
                originLon = currentLon;
                hasOrigin = true;
                ekf.reset(0, 0);
            }

            // Convert GPS to local metric and update EKF
            double[] local = gpsToLocal(currentLat, currentLon);
            ekf.updateWithGPS(local[0], local[1]);

            if (currentMode != NavMode.GPS) {
                currentMode = NavMode.GPS;
                notifyModeChange(NavMode.GPS);
            }

            notifyUpdate();
        }

        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onProviderDisabled(String provider) {}
    };

    // ─── Sensor Events ────────────────────────────────────────────────────────
    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();

        if (type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accel, 0, 3);
        } else if (type == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, gyro, 0, 3);
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, mag, 0, 3);
        } else if (type == Sensor.TYPE_ROTATION_VECTOR) {
            System.arraycopy(event.values, 0, rotVec, 0, Math.min(event.values.length, 5));
        } else if (type == Sensor.TYPE_STEP_DETECTOR) {
            stepJustDetected = true;
            stepCount++;
        }

        // Only process once we have all 3 basic sensors
        if (type != Sensor.TYPE_ACCELEROMETER && type != Sensor.TYPE_GYROSCOPE) return;

        long nowNs = event.timestamp;
        if (lastSensorTimestampNs == 0) { lastSensorTimestampNs = nowNs; return; }
        float dt = (nowNs - lastSensorTimestampNs) * 1e-9f;
        if (dt <= 0 || dt > 1.0f) { lastSensorTimestampNs = nowNs; return; }
        lastSensorTimestampNs = nowNs;

        // Run Madgwick filter for accurate orientation
        madgwick.update(
            (float) Math.toRadians(gyro[0]), (float) Math.toRadians(gyro[1]), (float) Math.toRadians(gyro[2]),
            accel[0], accel[1], accel[2],
            mag[0], mag[1], mag[2], dt
        );

        currentHeadingDeg = (float) Math.toDegrees(madgwick.getYaw());
        if (currentHeadingDeg < 0) currentHeadingDeg += 360;

        // Only use sensors for DR if GPS is lost
        if (currentMode == NavMode.DEAD_RECKONING && hasOrigin) {
            processDeadReckoning(dt);
        }
    }

    private void processDeadReckoning(float dt) {
        float accelMag = (float) Math.sqrt(accel[0]*accel[0] + accel[1]*accel[1] + accel[2]*accel[2]);
        accelWindow[accelWindowIdx] = accelMag;
        accelWindowIdx = (accelWindowIdx + 1) % accelWindow.length;

        maxAccelMag = Math.max(maxAccelMag, accelMag);
        minAccelMag = Math.min(minAccelMag, accelMag);

        // ZUPT: detect stationary
        boolean stationary = isStationary();
        if (stationary) {
            ekf.applyZUPT();
        }

        // Remove gravity from accelerometer (world frame)
        float headingRad = (float) Math.toRadians(currentHeadingDeg);

        if (stepJustDetected && !stationary) {
            stepJustDetected = false;

            // Weinberg adaptive step length model
            float deltaAccel = Math.max(0.1f, maxAccelMag - minAccelMag);
            float stepLength = (float) (0.42 * Math.pow(deltaAccel, 0.25));
            stepLength = Math.max(0.4f, Math.min(1.2f, stepLength));

            maxAccelMag = 0f;
            minAccelMag = 20f;

            // Displacement in local coordinates
            double dx = stepLength * Math.sin(headingRad);
            double dy = stepLength * Math.cos(headingRad);

            // Update EKF with step displacement
            ekf.predict(dx / dt, dy / dt, dt);

            totalDistanceM += stepLength;
            currentSpeedMs = stepLength / dt;

            // Convert EKF local position to lat/lon
            double[] ll = localToGps(ekf.getX(), ekf.getY());
            currentLat = ll[0];
            currentLon = ll[1];

            notifyUpdate();
        } else {
            // Between steps: predict with near-zero acceleration
            ekf.predict(0, 0, dt);
        }
    }

    // ─── Mode Checker ─────────────────────────────────────────────────────────
    private Runnable modeCheckRunnable = new Runnable() {
        @Override public void run() {
            long elapsed = System.currentTimeMillis() - lastGpsTimestampMs;
            if (elapsed > GPS_TIMEOUT_MS && currentMode == NavMode.GPS && hasOrigin) {
                currentMode = NavMode.DEAD_RECKONING;
                notifyModeChange(NavMode.DEAD_RECKONING);
            }
            mainHandler.postDelayed(this, 1000);
        }
    };

    // ─── Coordinate Conversion ─────────────────────────────────────────────────
    private double[] gpsToLocal(double lat, double lon) {
        double dx = (lon - originLon) * Math.cos(Math.toRadians(originLat)) * 111320.0;
        double dy = (lat - originLat) * 111320.0;
        return new double[]{dx, dy};
    }

    private double[] localToGps(double x, double y) {
        double lat = originLat + y / 111320.0;
        double lon = originLon + x / (111320.0 * Math.cos(Math.toRadians(originLat)));
        return new double[]{lat, lon};
    }

    private boolean isStationary() {
        float sum = 0;
        for (float v : accelWindow) sum += v;
        float mean = sum / accelWindow.length;
        float var = 0;
        for (float v : accelWindow) { float d = v - mean; var += d * d; }
        var /= accelWindow.length;
        float gyroMag = (float) Math.sqrt(gyro[0]*gyro[0]+gyro[1]*gyro[1]+gyro[2]*gyro[2]);
        return var < 0.3f && gyroMag < 0.1f;
    }

    private void notifyUpdate() {
        if (listener == null) return;
        final double lat = currentLat, lon = currentLon;
        final float spd = currentSpeedMs, hdg = currentHeadingDeg;
        final NavMode mode = currentMode;
        final int steps = stepCount;
        final double dist = totalDistanceM;
        mainHandler.post(() -> listener.onLocationUpdate(lat, lon, 5f, spd, hdg, mode, steps, dist));
    }

    private void notifyModeChange(NavMode mode) {
        if (listener == null) return;
        mainHandler.post(() -> listener.onModeChanged(mode));
    }

    public NavMode getCurrentMode() { return currentMode; }
    public double getCurrentLat() { return currentLat; }
    public double getCurrentLon() { return currentLon; }
    public float getHeadingDeg() { return currentHeadingDeg; }
    public int getStepCount() { return stepCount; }
    public double getTotalDistance() { return totalDistanceM; }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
