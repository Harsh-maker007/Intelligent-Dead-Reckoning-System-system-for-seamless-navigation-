package com.sih.deadreckoning;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.sih.deadreckoning.Engine.HybridDeadReckoningEngine;
import com.sih.deadreckoning.Engine.HybridDeadReckoningEngine.PositionPoint;
import com.sih.deadreckoning.Sensors.IMUSensorManager;
import com.sih.deadreckoning.UI.TrajectoryCanvasView;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity implements IMUSensorManager.IMUSensorListener {

    private IMUSensorManager imuSensorManager;
    private HybridDeadReckoningEngine engine;

    private TrajectoryCanvasView canvasView;
    private TextView tvSteps, tvDistance, tvHeading, tvZuptStatus, tvGnssBadge;
    private Button btnToggleGnss, btnReset, btnExport;

    private float currentHeadingDeg = 0.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Core Engines
        engine = new HybridDeadReckoningEngine();
        imuSensorManager = new IMUSensorManager(this);
        imuSensorManager.setListener(this);

        // Bind UI Views
        canvasView = findViewById(R.id.trajectoryCanvas);
        tvSteps = findViewById(R.id.tvSteps);
        tvDistance = findViewById(R.id.tvDistance);
        tvHeading = findViewById(R.id.tvHeading);
        tvZuptStatus = findViewById(R.id.tvZuptStatus);
        tvGnssBadge = findViewById(R.id.tvGnssBadge);

        btnToggleGnss = findViewById(R.id.btnToggleGnss);
        btnReset = findViewById(R.id.btnReset);
        btnExport = findViewById(R.id.btnExport);

        // Setup Button Action Listeners
        btnToggleGnss.setOnClickListener(v -> toggleGnssMode());
        btnReset.setOnClickListener(v -> resetPath());
        btnExport.setOnClickListener(v -> exportGpxTrajectory());
    }

    @Override
    protected void onResume() {
        super.onResume();
        imuSensorManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        imuSensorManager.stop();
    }

    @Override
    public void onSensorUpdate(float[] accel, float[] gyro, float[] mag, float[] orientation, boolean isStepDetected) {
        engine.processSensorFrame(accel, gyro, mag, orientation, isStepDetected);

        float yawRad = orientation[2];
        currentHeadingDeg = (float) Math.toDegrees(yawRad);
        if (currentHeadingDeg < 0) currentHeadingDeg += 360.0f;

        // Update Dashboard Text Views
        tvSteps.setText(String.valueOf(engine.getStepCount()));
        tvDistance.setText(String.format("%.1f m", engine.getTotalDistance()));
        tvHeading.setText(String.format("%.0f° %s", currentHeadingDeg, getCompassCardinal(currentHeadingDeg)));

        // Update Trajectory Canvas View
        canvasView.updateTrajectory(engine.getTrajectoryPath(), yawRad);
    }

    private void toggleGnssMode() {
        boolean currentlyActive = engine.isGnssActive();
        boolean newMode = !currentlyActive;
        engine.setGnssActive(newMode);

        if (newMode) {
            tvGnssBadge.setText("GNSS ON");
            tvGnssBadge.setBackgroundColor(Color.parseColor("#22C55E"));
            btnToggleGnss.setText("Simulate GNSS Outage");
            Toast.makeText(this, "GNSS Signal Restored. Using Satellite + Sensor Fusion.", Toast.LENGTH_SHORT).show();
        } else {
            tvGnssBadge.setText("GNSS OUTAGE (AI ACTIVE)");
            tvGnssBadge.setBackgroundColor(Color.parseColor("#EF4444"));
            btnToggleGnss.setText("Restore GNSS Signal");
            Toast.makeText(this, "GNSS Signal Lost! AI Dead Reckoning Engine Engaged.", Toast.LENGTH_SHORT).show();
        }
    }

    private void resetPath() {
        engine.reset();
        tvSteps.setText("0");
        tvDistance.setText("0.0 m");
        canvasView.updateTrajectory(engine.getTrajectoryPath(), 0);
        Toast.makeText(this, "Trajectory & Step Count Reset.", Toast.LENGTH_SHORT).show();
    }

    private void exportGpxTrajectory() {
        List<PositionPoint> points = engine.getTrajectoryPath();
        if (points.isEmpty()) {
            Toast.makeText(this, "No trajectory points recorded.", Toast.LENGTH_SHORT).show();
            return;
        }

        File exportDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (exportDir == null) exportDir = getFilesDir();
        File gpxFile = new File(exportDir, "dead_reckoning_trajectory_" + System.currentTimeMillis() + ".gpx");

        try (FileWriter writer = new FileWriter(gpxFile)) {
            writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            writer.write("<gpx version=\"1.1\" creator=\"SIH2026-IntelligentDeadReckoning\">\n");
            writer.write("  <trk>\n    <name>SIH 2026 AI Dead Reckoning Route</name>\n    <trkseg>\n");

            // Convert local (X, Y) metric coordinates to standard reference lat/lon
            double refLat = 28.6139; // Default reference latitude (New Delhi / ISRO SIH venue)
            double refLon = 77.2090;

            for (PositionPoint pt : points) {
                double lat = refLat + (pt.y / 111111.0);
                double lon = refLon + (pt.x / (111111.0 * Math.cos(Math.toRadians(refLat))));
                writer.write(String.format("      <trkpt lat=\"%.7f\" lon=\"%.7f\"><time>%d</time></trkpt>\n", lat, lon, pt.timestamp));
            }

            writer.write("    </trkseg>\n  </trk>\n</gpx>\n");
            Toast.makeText(this, "GPX Log Saved: " + gpxFile.getName(), Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "Export Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getCompassCardinal(float deg) {
        if (deg >= 337.5 || deg < 22.5) return "N";
        if (deg >= 22.5 && deg < 67.5) return "NE";
        if (deg >= 67.5 && deg < 112.5) return "E";
        if (deg >= 112.5 && deg < 157.5) return "SE";
        if (deg >= 157.5 && deg < 202.5) return "S";
        if (deg >= 202.5 && deg < 247.5) return "SW";
        if (deg >= 247.5 && deg < 292.5) return "W";
        return "NW";
    }
}
