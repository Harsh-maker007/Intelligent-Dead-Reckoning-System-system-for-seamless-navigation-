package com.sih.deadreckoning.Sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class IMUSensorManager implements SensorEventListener {

    public interface IMUSensorListener {
        void onSensorUpdate(float[] accel, float[] gyro, float[] mag, float[] orientation, boolean isStepDetected);
    }

    private final SensorManager sensorManager;
    private final Sensor accelSensor;
    private final Sensor gyroSensor;
    private final Sensor magSensor;
    private final Sensor stepSensor;
    private final Sensor rotationVectorSensor;

    private IMUSensorListener listener;

    private float[] lastAccel = new float[3];
    private float[] lastGyro = new float[3];
    private float[] lastMag = new float[3];
    private float[] rotationMatrix = new float[9];
    private float[] orientation = new float[3]; // Pitch, Roll, Yaw (heading)
    private boolean stepDetectedThisFrame = false;

    public IMUSensorManager(Context context) {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void setListener(IMUSensorListener listener) {
        this.listener = listener;
    }

    public void start() {
        if (sensorManager == null) return;
        
        // Sensor delay tuned for Samsung hardware: SENSOR_DELAY_GAME (~20ms / 50Hz)
        if (accelSensor != null) sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_GAME);
        if (gyroSensor != null) sensorManager.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_GAME);
        if (magSensor != null) sensorManager.registerListener(this, magSensor, SensorManager.SENSOR_DELAY_GAME);
        if (stepSensor != null) sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_FASTEST);
        if (rotationVectorSensor != null) sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        int type = event.sensor.getType();

        if (type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, lastAccel, 0, 3);
        } else if (type == Sensor.TYPE_GYROSCOPE) {
            System.arraycopy(event.values, 0, lastGyro, 0, 3);
        } else if (type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, lastMag, 0, 3);
        } else if (type == Sensor.TYPE_STEP_DETECTOR) {
            stepDetectedThisFrame = true;
        } else if (type == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
        }

        // Compute orientation fallback using Accel & Mag if Rotation Vector unavailable
        if (rotationVectorSensor == null) {
            boolean success = SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag);
            if (success) {
                SensorManager.getOrientation(rotationMatrix, orientation);
            }
        }

        if (listener != null) {
            listener.onSensorUpdate(lastAccel, lastGyro, lastMag, orientation, stepDetectedThisFrame);
            stepDetectedThisFrame = false; // Reset flag after dispatch
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}
