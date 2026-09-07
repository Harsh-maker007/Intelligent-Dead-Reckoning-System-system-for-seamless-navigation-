import os
import torch
import torch.nn as nn
import torch.optim as optim
from dataset_loader import get_dataloader
from model_architecture import DeepInertialDeadReckoningModel

def train_model(epochs=15, batch_size=32, lr=1e-3, save_path="dead_reckoning_model.pth"):
    print("=== Training AI-ML Intelligent Dead Reckoning Model (SIH 2026) ===")
    
    loader, mean, std = get_dataloader(batch_size=batch_size, sequence_length=50, stride=5)
    model = DeepInertialDeadReckoningModel(input_dim=9, hidden_dim=64, num_layers=2)
    
    criterion = nn.MSELoss()
    optimizer = optim.Adam(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = optim.lr_scheduler.ReduceLROnPlateau(optimizer, mode='min', factor=0.5, patience=3)
    
    model.train()
    for epoch in range(1, epochs + 1):
        total_loss = 0.0
        batches = 0
        for bx, by in loader:
            optimizer.zero_grad()
            pred = model(bx)
            loss = criterion(pred, by)
            loss.backward()
            optimizer.step()
            
            total_loss += loss.item()
            batches += 1
            
        avg_loss = total_loss / batches
        scheduler.step(avg_loss)
        print(f"Epoch [{epoch:02d}/{epochs:02d}] - Loss (MSE): {avg_loss:.6f} - LR: {optimizer.param_groups[0]['lr']:.6f}")
        
    print("\nTraining completed successfully.")
    
    # Save checkpoint with normalization parameters
    checkpoint = {
        'model_state_dict': model.state_dict(),
        'mean': mean,
        'std': std,
        'input_dim': 9,
        'seq_len': 50
    }
    torch.save(checkpoint, save_path)
    print(f"Model saved to {os.path.abspath(save_path)}")
    return model, checkpoint

if __name__ == "__main__":
    train_model(epochs=10)
