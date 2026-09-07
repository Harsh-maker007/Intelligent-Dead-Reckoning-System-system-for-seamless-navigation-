package com.sih.deadreckoning;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.sih.deadreckoning.engine.NavigationEngine;
import com.sih.deadreckoning.ui.MapCanvasView;

public class MainActivity extends Activity implements NavigationEngine.NavigationListener {

    private static final int PERMISSION_REQUEST_CODE = 1234;

    private MapCanvasView mapCanvas;
    private TextView tvGpsSignal;
    private TextView tvAccuracy;
    private Button btnRecenter, btnZoomIn, btnZoomOut;

    private NavigationEngine navEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapCanvas = findViewById(R.id.mapCanvas);
        tvGpsSignal = findViewById(R.id.tvGpsSignal);
        tvAccuracy = findViewById(R.id.tvAccuracy);

        btnRecenter = findViewById(R.id.btnRecenter);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);

        btnRecenter.setOnClickListener(v -> mapCanvas.setFollowUser(true));
        
        // Simple zoom controls via scale gesture injection or simple property (we'll just use simple touch scaling in the view)
        // Here we can just show a toast for simplicity, real zoom is pinch-to-zoom
        btnZoomIn.setOnClickListener(v -> Toast.makeText(this, "Pinch to zoom", Toast.LENGTH_SHORT).show());
        btnZoomOut.setOnClickListener(v -> Toast.makeText(this, "Pinch to zoom", Toast.LENGTH_SHORT).show());

        navEngine = new NavigationEngine(this, this);

        checkPermissionsAndStart();
    }

    private void checkPermissionsAndStart() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        } else {
            navEngine.start();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                navEngine.start();
            } else {
                Toast.makeText(this, "Location permission required for Navigation", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            navEngine.start();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        navEngine.stop();
    }

    @Override
    public void onLocationUpdate(double lat, double lon, float accuracy, float speedMs, float headingDeg, NavigationEngine.NavMode mode, int stepCount, double totalDistanceM) {
        int modeColor = mode == NavigationEngine.NavMode.GPS ? Color.parseColor("#4CAF50") : Color.parseColor("#FF9800");
        String modeStr = mode == NavigationEngine.NavMode.GPS ? "GPS Active" : "AI Dead Reckoning";

        tvAccuracy.setText(String.format("±%.0f m", accuracy));

        mapCanvas.updateLocation(lat, lon, headingDeg, modeStr, speedMs, totalDistanceM, stepCount, modeColor);
        mapCanvas.addTrackPoint(lat, lon, mode == NavigationEngine.NavMode.GPS);
    }

    @Override
    public void onModeChanged(NavigationEngine.NavMode newMode) {
        if (newMode == NavigationEngine.NavMode.GPS) {
            tvGpsSignal.setText("● GPS");
            tvGpsSignal.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            tvGpsSignal.setText("○ No GPS");
            tvGpsSignal.setTextColor(Color.parseColor("#F44336"));
            Toast.makeText(this, "GPS Lost. Switching to AI Dead Reckoning.", Toast.LENGTH_LONG).show();
        }
    }
}
