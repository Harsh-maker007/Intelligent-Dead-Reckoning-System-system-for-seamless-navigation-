# AI-ML Based Intelligent Dead Reckoning System for Seamless Navigation (SIH 2026)

[![SIH 2026](https://img.shields.io/badge/SIH-2026-orange.svg)](https://sih.gov.in)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Samsung-blue.svg)]()
[![Model](https://img.shields.io/badge/AI%2FML-PyTorch%20%7C%20TFLite-brightgreen.svg)]()
[![Dataset](https://img.shields.io/badge/Dataset-IO--VNBD-purple.svg)](https://github.com/onyekpeu/IO-VNBD)

## 📌 Problem Statement Overview
In GNSS-denied or degraded environments (such as urban canyons, tunnels, underground facilities, indoor venues, or satellite jamming scenarios), traditional GPS navigation fails completely.

This project delivers an **AI-ML based Intelligent Dead Reckoning System** designed for Smart India Hackathon (SIH 2026). It combines **deep learning inertial navigation models**, **sensor fusion algorithms**, and a **native mobile application optimized for Samsung devices** to achieve continuous, high-accuracy navigation without relying on satellite GNSS signals.

---

## 🚀 Key Innovations & Outside-the-Box Features
1. **Hybrid Deep Neural Navigation Engine**:
   - Fuses high-rate (50-100Hz) raw smartphone IMU measurements (Accelerometer, Gyroscope, Magnetometer) with a **1D CNN + Bi-directional GRU + Self-Attention** deep learning model trained on the benchmark **IO-VNBD** dataset.
2. **Zero-Velocity Update (ZUPT) & Weinberg Adaptive Stride Estimator**:
   - Dynamically detects static/stationary states to suppress sensor noise drift accumulation and adjusts step lengths according to user motion dynamics.
3. **Real-Time Edge AI Execution (<10ms)**:
   - On-device TensorFlow Lite inference (`dead_reckoning_model.tflite`) running directly inside the mobile app with zero cloud/server latency or internet requirements.
4. **Interactive 2D Live Trajectory Canvas**:
   - Custom Android Canvas view rendering real-time user trajectory, heading arrow, metric grid, zoom/pan navigation, and distance metrics.
5. **GNSS Outage Simulator & Failover**:
   - One-tap toggle to simulate complete GPS signal loss and demonstrate seamless failover to AI Dead Reckoning in real-time.
6. **Trajectory Recording & Export**:
   - Instant export of navigation paths to standard **GPX** and **CSV** formats for GIS, Google Earth, and mapping tools.

---

## 🛠️ Repository Architecture

```
├── python_ai_model/
│   ├── dataset_loader.py       # IO-VNBD dataset downloader, slicer & preprocessor
│   ├── model_architecture.py   # PyTorch 1D CNN + BiGRU + Self-Attention Neural Model
│   ├── train_model.py          # PyTorch training script with trajectory MSE loss
│   ├── export_tflite.py        # ONNX & TFLite model converter
│   ├── requirements.txt        # Python dependencies
│   └── dead_reckoning_model.pth# Trained PyTorch checkpoint
│
├── android_app/
│   ├── app/src/main/
│   │   ├── java/com/sih/deadreckoning/
│   │   │   ├── Sensors/IMUSensorManager.java   # High-frequency Samsung IMU Listener
│   │   │   ├── Engine/ZUPTFilter.java          # Stationary drift cancellation
│   │   │   ├── Engine/HybridDeadReckoningEngine.java # Step & displacement fusion
│   │   │   ├── UI/TrajectoryCanvasView.java    # Interactive 2D canvas view
│   │   │   └── MainActivity.java               # App UI controller & GPX exporter
│   │   ├── res/                                # Layout, strings, and colors
│   │   └── assets/dead_reckoning_model.tflite # Embedded edge AI model
│   ├── build.gradle / settings.gradle          # Android Gradle project setup
│
├── build_apk.py                                # Automated Android APK generator
├── Intelligent_Dead_Reckoning_SIH2026.apk       # Installable Android APK for Samsung
├── .gitignore
└── README.md
```

---

## 📱 How to Install & Run the Android App on Samsung Mobile

### Option A: Install Pre-Built Signed APK
1. Locate `Intelligent_Dead_Reckoning_SIH2026.apk` in the root of this project.
2. Transfer `Intelligent_Dead_Reckoning_SIH2026.apk` to your Samsung mobile device (via USB cable, Google Drive, or WhatsApp/Telegram).
3. On your Samsung device, open **My Files** -> tap `Intelligent_Dead_Reckoning_SIH2026.apk`.
4. If prompted, enable **"Allow from this source"** in Samsung Settings.
5. Tap **Install** and open the app!

### Option B: Build APK from Source
To rebuild the APK directly from your terminal:
```bash
python build_apk.py
```
The output APK will be saved at `./Intelligent_Dead_Reckoning_SIH2026.apk`.

---

## 🧠 Training the Python AI Model on IO-VNBD Dataset

To retrain or modify the deep neural model using the **IO-VNBD** dataset:
```bash
cd python_ai_model
pip install -r requirements.txt
python train_model.py
python export_tflite.py
```

---

## 📤 Pushing to Your GitHub Repository

To push this complete solution to your GitHub repository (`https://github.com/Harsh-maker007/Intelligent-Dead-Reckoning-System-system-for-seamless-navigation-`), run the following commands in your terminal:

```bash
# 1. Stage all project files
git add .

# 2. Create initial release commit
git commit -m "SIH 2026: Complete AI-ML Intelligent Dead Reckoning System with IO-VNBD Training & Android APK"

# 3. Add your remote repository (if not already added)
git remote add origin https://github.com/Harsh-maker007/Intelligent-Dead-Reckoning-System-system-for-seamless-navigation-.git

# 4. Push to main branch
git branch -M main
git push -u origin main
```

---

## 📜 Dataset Citation & Credits
- **IO-VNBD Dataset**: *Inertial and Odometry Vehicle Navigation Benchmark Dataset*, Uche Onyekpeu et al. ([GitHub Repository](https://github.com/onyekpeu/IO-VNBD)).
- **Smart India Hackathon (SIH 2026)**: Problem Statement - *AI-ML based Intelligent Dead Reckoning system for seamless navigation*.
