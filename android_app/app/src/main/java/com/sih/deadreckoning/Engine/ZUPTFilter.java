package com.sih.deadreckoning.Engine;

public class ZUPTFilter {

    private final float accelVarianceThreshold;
    private final float gyroMagnitudeThreshold;
    private final float[] accelWindow;
    private int windowIdx = 0;
    private final int windowSize;

    public ZUPTFilter(int windowSize, float accelVarThresh, float gyroMagThresh) {
        this.windowSize = windowSize;
        this.accelVarianceThreshold = accelVarThresh;
        this.gyroMagnitudeThreshold = gyroMagThresh;
        this.accelWindow = new float[windowSize];
    }

    public ZUPTFilter() {
        this(15, 0.25f, 0.08f); // Defaults tuned for mobile handheld motion
    }

    /**
     * Determines whether the device is stationary (Zero-Velocity state).
     */
    public boolean isStationary(float[] accel, float[] gyro) {
        // Accelerometer magnitude
        float accelMag = (float) Math.sqrt(accel[0] * accel[0] + accel[1] * accel[1] + accel[2] * accel[2]);
        accelWindow[windowIdx] = accelMag;
        windowIdx = (windowIdx + 1) % windowSize;

        // Gyroscope magnitude
        float gyroMag = (float) Math.sqrt(gyro[0] * gyro[0] + gyro[1] * gyro[1] + gyro[2] * gyro[2]);

        // Compute variance of acceleration window
        float sum = 0.0f;
        for (float v : accelWindow) sum += v;
        float mean = sum / windowSize;

        float varSum = 0.0f;
        for (float v : accelWindow) {
            float diff = v - mean;
            varSum += diff * diff;
        }
        float accelVar = varSum / windowSize;

        // Stationary if acceleration variance and angular velocity are below noise floor
        return (accelVar < accelVarianceThreshold) && (gyroMag < gyroMagnitudeThreshold);
    }
}
