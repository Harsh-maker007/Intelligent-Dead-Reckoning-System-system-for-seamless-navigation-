import torch
import torch.nn as nn
import torch.nn.functional as F

class SelfAttention(nn.Module):
    """
    Self-Attention module for weighting critical inertial feature windows.
    """
    def __init__(self, hidden_dim):
        super().__init__()
        self.projection = nn.Sequential(
            nn.Linear(hidden_dim, 64),
            nn.Tanh(),
            nn.Linear(64, 1)
        )

    def forward(self, encoder_outputs):
        # encoder_outputs: (batch_size, seq_len, hidden_dim)
        energy = self.projection(encoder_outputs) # (batch_size, seq_len, 1)
        weights = F.softmax(energy, dim=1)
        context = torch.sum(weights * encoder_outputs, dim=1) # (batch_size, hidden_dim)
        return context, weights

class DeepInertialDeadReckoningModel(nn.Module):
    """
    Hybrid Deep Neural Network for Intelligent Dead Reckoning.
    Combines 1D CNN for high-frequency motion extraction + BiGRU for temporal sequence modeling + Attention.
    """
    def __init__(self, input_dim=9, hidden_dim=64, num_layers=2):
        super().__init__()
        
        # 1D Convolutional Feature Extractor
        self.conv1 = nn.Conv1d(in_channels=input_dim, out_channels=32, kernel_size=3, padding=1)
        self.bn1 = nn.BatchNorm1d(32)
        self.conv2 = nn.Conv1d(in_channels=32, out_channels=64, kernel_size=3, padding=1)
        self.bn2 = nn.BatchNorm1d(64)
        
        # Recurrent Sequence Model
        self.gru = nn.GRU(
            input_size=64,
            hidden_size=hidden_dim,
            num_layers=num_layers,
            batch_first=True,
            bidirectional=True
        )
        
        # Attention Layer
        self.attention = SelfAttention(hidden_dim * 2)
        
        # Prediction Heads
        self.fc_displacement = nn.Sequential(
            nn.Linear(hidden_dim * 2, 64),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(64, 2) # dx, dy output
        )
        
        self.fc_speed = nn.Sequential(
            nn.Linear(hidden_dim * 2, 32),
            nn.ReLU(),
            nn.Linear(32, 1) # Velocity magnitude output
        )

    def forward(self, x):
        # Input shape: (batch_size, seq_len, input_dim)
        # Transpose for Conv1d: (batch_size, input_dim, seq_len)
        x_conv = x.transpose(1, 2)
        x_conv = F.relu(self.bn1(self.conv1(x_conv)))
        x_conv = F.relu(self.bn2(self.conv2(x_conv)))
        
        # Transpose back for GRU: (batch_size, seq_len, 64)
        x_seq = x_conv.transpose(1, 2)
        gru_out, _ = self.gru(x_seq)
        
        # Attention Pooling
        context, _ = self.attention(gru_out)
        
        # Prediction Outputs
        dx_dy = self.fc_displacement(context)
        speed = self.fc_speed(context)
        
        # Concatenate outputs: [dx, dy, speed]
        out = torch.cat([dx_dy, speed], dim=-1)
        return out

if __name__ == "__main__":
    dummy_input = torch.randn(16, 50, 9) # Batch 16, 50 time steps, 9 IMU channels
    model = DeepInertialDeadReckoningModel()
    output = model(dummy_input)
    print(f"Model output shape: {output.shape}") # Expected: (16, 3) -> [dx, dy, speed]
    print("Model architecture compiled successfully.")
