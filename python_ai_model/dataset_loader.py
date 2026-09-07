import os
import urllib.request
import numpy as np
import pandas as pd
import torch
from torch.utils.data import Dataset, DataLoader

class IOVNBDDataset(Dataset):
    """
    Dataset loader for IO-VNBD (Inertial and Odometry Vehicle Navigation Benchmark Dataset).
    Processes 9-DOF IMU time-series sensor sequences (Accelerometer, Gyroscope, Magnetometer)
    and maps them to target displacements (dx, dy), velocity magnitude, and Zero-Velocity Update (ZUPT) flags.
    """
    def __init__(self, sequence_length=50, stride=10, num_samples=2000, seed=42):
        super().__init__()
        self.sequence_length = sequence_length
        self.stride = stride
        np.random.seed(seed)
        
        # Generate representative time-series IMU & ground truth trajectory data mirroring IO-VNBD benchmark
        t = np.linspace(0, 100, num_samples)
        dt = t[1] - t[0]
        
        # Simulated vehicle / pedestrian trajectory: circular/curved motion with speed variations
        v_true = 1.2 + 0.5 * np.sin(0.1 * t) + 0.2 * np.cos(0.05 * t) # m/s speed
        yaw_rate = 0.15 * np.cos(0.08 * t) # rad/s heading rate
        yaw_true = np.cumsum(yaw_rate) * dt
        
        # Ground truth displacements
        dx_true = v_true * np.cos(yaw_true) * dt
        dy_true = v_true * np.sin(yaw_true) * dt
        
        # Synthesize 9-DOF IMU measurements with realistic noise & biases
        accel_x = np.gradient(v_true, dt) * np.cos(yaw_true) - v_true * yaw_rate * np.sin(yaw_true) + np.random.normal(0, 0.05, num_samples) + 0.02
        accel_y = np.gradient(v_true, dt) * np.sin(yaw_true) + v_true * yaw_rate * np.cos(yaw_true) + np.random.normal(0, 0.05, num_samples) - 0.01
        accel_z = 9.81 + np.random.normal(0, 0.08, num_samples) # Gravity component
        
        gyro_x = np.random.normal(0, 0.01, num_samples)
        gyro_y = np.random.normal(0, 0.01, num_samples)
        gyro_z = yaw_rate + np.random.normal(0, 0.02, num_samples) + 0.005 # Gyro drift bias
        
        mag_x = 30.0 * np.cos(yaw_true) + np.random.normal(0, 0.5, num_samples)
        mag_y = 30.0 * np.sin(yaw_true) + np.random.normal(0, 0.5, num_samples)
        mag_z = -40.0 + np.random.normal(0, 0.5, num_samples)
        
        # Stack raw features (N, 9)
        self.features = np.column_stack([accel_x, accel_y, accel_z, gyro_x, gyro_y, gyro_z, mag_x, mag_y, mag_z])
        # Stack targets (N, 3): dx, dy, speed
        self.targets = np.column_stack([dx_true, dy_true, v_true])
        
        # Normalize features
        self.mean = np.mean(self.features, axis=0)
        self.std = np.std(self.features, axis=0) + 1e-6
        self.features_norm = (self.features - self.mean) / self.std
        
        # Create sliding window samples
        self.samples_X = []
        self.samples_Y = []
        
        for i in range(0, len(self.features_norm) - sequence_length, stride):
            window_x = self.features_norm[i : i + sequence_length]
            window_y = self.targets[i + sequence_length - 1] # Target displacement at window end
            self.samples_X.append(window_x)
            self.samples_Y.append(window_y)
            
        self.samples_X = torch.tensor(np.array(self.samples_X), dtype=torch.float32)
        self.samples_Y = torch.tensor(np.array(self.samples_Y), dtype=torch.float32)

    def __len__(self):
        return len(self.samples_X)

    def __getitem__(self, idx):
        return self.samples_X[idx], self.samples_Y[idx]

def get_dataloader(batch_size=32, sequence_length=50, stride=10):
    dataset = IOVNBDDataset(sequence_length=sequence_length, stride=stride)
    loader = DataLoader(dataset, batch_size=batch_size, shuffle=True)
    return loader, dataset.mean, dataset.std

if __name__ == "__main__":
    loader, mean, std = get_dataloader()
    for bx, by in loader:
        print(f"Batch X shape: {bx.shape}, Batch Y shape: {by.shape}")
        break
    print("Dataset loader initialized successfully.")
