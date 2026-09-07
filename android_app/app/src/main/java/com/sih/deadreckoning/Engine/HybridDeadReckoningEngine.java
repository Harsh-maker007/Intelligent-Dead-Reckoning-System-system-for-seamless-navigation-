package com.sih.deadreckoning.Engine;

import java.util.ArrayList;
import java.util.List;

public class HybridDeadReckoningEngine {

    public static class PositionPoint {
        public double x;
        public double y;
        public float headingRad;
        public long timestamp;
        public boolean isGnssActive;

        public PositionPoint(double x, double y, float headingRad, boolean isGnssActive) {
            this.x = x;
            this.y = y;
            this.headingRad = headingRad;
            this.timestamp = System.currentTimeMillis();
            this.isGnssActive = isGnssActive;
        }
    }

    private double currentX = 0.0;
    private double currentY = 0.0;
    private double totalDistance = 0.0;
    private int stepCount = 0;

    private boolean isGnssSimulatedActive = true;
    private final ZUPTFilter zuptFilter;
    private final List<PositionPoint> trajectoryPath = new ArrayList<>();

    // Weinberg step length estimator constants
    private static final float K_STEP_CONST = 0.42f;
    private float maxAccelMagnitude = 0.0f;
    private float minAccelMagnitude = 9.81f;

    public HybridDeadReckoningEngine() {
        this.zuptFilter = new ZUPTFilter();
        trajectoryPath.add(new PositionPoint(0, 0, 0, true));
    }

    public void setGnssActive(boolean active) {
        this.isGnssSimulatedActive = active;
    }

    public boolean isGnssActive() {
        return isGnssSimulatedActive;
    }

    public void processSensorFrame(float[] accel, float[] gyro, float[] mag, float[] orientation, boolean hardwareStepDetected) {
        float yawRad = orientation[2]; // Heading angle in radians

        // Accelerometer magnitude
        float accelMag = (float) Math.sqrt(accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]);
        maxAccelMagnitude = Math.max(maxAccelMagnitude, accelMag);
        minAccelMagnitude = Math.min(minAccelMagnitude, accelMag);

        boolean isStationary = zuptFilter.isStationary(accel, gyro);

        if (hardwareStepDetected && !isStationary) {
            stepCount++;
            
            // Calculate step length using Weinberg non-linear model: L = K * (A_max - A_min)^0.25
            float deltaAccel = Math.max(0.1f, maxAccelMagnitude - minAccelMagnitude);
            float stepLength = (float) (K_STEP_CONST * Math.pow(deltaAccel, 0.25));
            stepLength = Math.max(0.45f, Math.min(1.1f, stepLength)); // Clamp to realistic stride bounds

            // Reset min/max peak trackers
            maxAccelMagnitude = 0.0f;
            minAccelMagnitude = 20.0f;

            // Dead Reckoning Step Displacement update: dX = L * cos(yaw), dY = L * sin(yaw)
            double dx = stepLength * Math.cos(yawRad);
            double dy = stepLength * Math.sin(yawRad);

            currentX += dx;
            currentY += dy;
            totalDistance += stepLength;

            trajectoryPath.add(new PositionPoint(currentX, currentY, yawRad, isGnssSimulatedActive));
        }
    }

    public double getCurrentX() { return currentX; }
    public double getCurrentY() { return currentY; }
    public double getTotalDistance() { return totalDistance; }
    public int getStepCount() { return stepCount; }
    public List<PositionPoint> getTrajectoryPath() { return trajectoryPath; }

    public void reset() {
        currentX = 0.0;
        currentY = 0.0;
        totalDistance = 0.0;
        stepCount = 0;
        trajectoryPath.clear();
        trajectoryPath.add(new PositionPoint(0, 0, 0, isGnssSimulatedActive));
    }
}
