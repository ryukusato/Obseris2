import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
import numpy as np
from tqdm import tqdm

# --- Phase 1 と共通のブロック ---
class ResidualBlock(nn.Module):
    def __init__(self, ch):
        super().__init__()
        self.net = nn.Sequential(
            nn.Conv2d(ch, ch, 3, padding=1),
            nn.BatchNorm2d(ch),
            nn.ReLU(),
            nn.Conv2d(ch, ch, 3, padding=1),
            nn.BatchNorm2d(ch)
        )
        self.act = nn.ReLU()

    def forward(self, x):
        return self.act(x + self.net(x))

# --- フェーズ2用モデル ---
class TetrisValueNetwork(nn.Module):
    def __init__(self, pt_path):
        super().__init__()

        # 1. Encoder部分 (Phase 1 の定義をそのまま記述)
        self.encoder = nn.Sequential(
            nn.Conv2d(2, 64, 3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(),
            ResidualBlock(64),
            ResidualBlock(64),
            ResidualBlock(64),
            nn.Conv2d(64, 8, 1),
            nn.ReLU()
        )

        # 2. 重みのロード (名前のズレを自動修正)
        checkpoint = torch.load(pt_path, map_location="cpu")
        clean_dict = {k.replace("encoder.", ""): v for k, v in checkpoint.items()}
        self.encoder.load_state_dict(clean_dict)
        
        # 3. Encoderを固定
        for p in self.encoder.parameters():
            p.requires_grad = False
        
        # 4. Value Head (評価値計算用)
        self.head = nn.Sequential(
            nn.Flatten(),
            nn.Linear(8 * 40 * 10, 512),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(512, 128),
            nn.ReLU(),
            nn.Linear(128, 1)
        )

    def forward(self, x):
        with torch.no_grad():
            feat = self.encoder(x)
        return self.head(feat)

# --- データセット (正規化) ---
class TetrisDataset(Dataset):
    def __init__(self, path):
        data = np.load(path)
        self.boards = data["boards"].astype(np.float32)
        raw_scores = data["scores"].astype(np.float32)

        # Z-score正規化
        self.mean, self.std = raw_scores.mean(), raw_scores.std() + 1e-7
        self.scores = (raw_scores - self.mean) / self.std
        print(f"Mean: {self.mean:.1f}, Std: {self.std:.1f}")

    def __len__(self):
        return len(self.boards)

    def __getitem__(self, i):
        return torch.from_numpy(self.boards[i]), torch.tensor([self.scores[i]])

# --- 学習メイン ---
def train():
    device = "mps" if torch.backends.mps.is_available() else "cpu"
    
    dataset = TetrisDataset("phase2_dataset.npz")
    loader = DataLoader(dataset, batch_size=1024, shuffle=True)

    model = TetrisValueNetwork("encoder_phase1.pt").to(device)
    optimizer = optim.Adam(model.head.parameters(), lr=1e-3)
    criterion = nn.HuberLoss()

    for epoch in range(20):
        model.train()
        pbar = tqdm(loader, desc=f"Epoch {epoch+1}")
        
        for boards, targets in pbar:
            boards, targets = boards.to(device), targets.to(device)
            
            optimizer.zero_grad()
            preds = model(boards)
            loss = criterion(preds, targets)
            loss.backward()
            optimizer.step()
            
            # stdを見て「サボり（全員に同じ回答）」がないかチェック
            pbar.set_postfix({"loss": f"{loss.item():.5f}", "std": f"{preds.std().item():.3f}"})

    torch.save(model.state_dict(), "value_model.pt")
    print("Saved as value_model.pt")

if __name__ == "__main__":
    train()