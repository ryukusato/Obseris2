import torch
import torch.nn as nn

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

class TetrisAutoEncoder(nn.Module):
    def __init__(self):
        super().__init__()

        # ===== Encoder =====
        self.encoder = nn.Sequential(
            nn.Conv2d(2, 64, 3, padding=1),   # (2,40,10)->(64,40,10)
            nn.BatchNorm2d(64),
            nn.ReLU(),

            ResidualBlock(64),
            ResidualBlock(64),
            ResidualBlock(64),

            # 情報を「圧縮」するが座標は保持
            nn.Conv2d(64, 8, 1),              # (8,40,10)
            nn.ReLU()
        )

        # ===== Decoder =====
        self.decoder = nn.Sequential(
            nn.Conv2d(8, 64, 1),
            nn.ReLU(),

            ResidualBlock(64),
            ResidualBlock(64),

            nn.Conv2d(64, 2, 3, padding=1),   # (2,40,10) に戻す
            nn.Sigmoid()                     # 0/1復元用
        )

    def forward(self, x):
        z = self.encoder(x)
        y = self.decoder(z)
        return y

import numpy as np
import torch
from torch.utils.data import Dataset

class TetrisBoardDataset(Dataset):
    def __init__(self, file_path):
        obj = np.load(file_path)
        if isinstance(obj, np.lib.npyio.NpzFile):
            self.data = obj["arr"].astype(np.float32)
        else:
            self.data = obj.astype(np.float32)


    def __len__(self):
        return len(self.data)

    def __getitem__(self, i):
        board = self.data[i]  # (2,40,10)

        # 左右反転データ拡張
        if np.random.rand() > 0.5:
            board = np.flip(board, axis=2).copy()

        x = torch.from_numpy(board)
        return x, x   # AEなので入力=教師

def ae_loss(pred, target, alpha=0.5):
    # ch0: 盤面, ch1: ミノ
    loss_board = torch.mean((pred[:,0] - target[:,0])**2)
    loss_piece = torch.mean((pred[:,1] - target[:,1])**2)
    return loss_board + alpha * loss_piece
def ae_loss_parts(pred, target):
    loss_board = torch.mean((pred[:,0] - target[:,0])**2)
    loss_piece = torch.mean((pred[:,1] - target[:,1])**2)
    return loss_board, loss_piece

import torch
from torch.utils.data import DataLoader
from tqdm import tqdm

def train_phase1(npz_path, epochs=20, batch_size=256, lr=1e-3):
    # ===== device 自動選択 =====
    if torch.cuda.is_available():
        device = "cuda"
    elif torch.backends.mps.is_available():
        device = "mps"   # M2 GPU
    else:
        device = "cpu"
    print("device:", device)

    ds = TetrisBoardDataset(npz_path)
    dl = DataLoader(
        ds,
        batch_size=batch_size,
        shuffle=True,
        num_workers=0,
        pin_memory=(device=="cuda")
    )

    model = TetrisAutoEncoder().to(device)
    opt = torch.optim.Adam(model.parameters(), lr=lr)

    for ep in range(epochs):
        model.train()
        total_loss = 0.0
        total_lb = 0.0
        total_lp = 0.0

        pbar = tqdm(dl, desc=f"epoch {ep+1}/{epochs}", leave=False)

        for x, y in pbar:
            x = x.to(device)
            y = y.to(device)

            out = model(x)

            lb, lp = ae_loss_parts(out, y)
            loss = lb + 0.5 * lp  # alpha=0.5

            opt.zero_grad()
            loss.backward()
            opt.step()

            bs = x.size(0)
            total_loss += loss.item() * bs
            total_lb   += lb.item()   * bs
            total_lp   += lp.item()   * bs

            # 出力の平均（0に潰れてないかチェック）
            m0 = out[:,0].mean().item()
            m1 = out[:,1].mean().item()

            pbar.set_postfix({
                "L":  f"{loss.item():.4e}",
                "Lb": f"{lb.item():.4e}",
                "Lp": f"{lp.item():.4e}",
                "m0": f"{m0:.3f}",
                "m1": f"{m1:.3f}",
            })

        epoch_loss = total_loss / len(ds)
        epoch_lb   = total_lb   / len(ds)
        epoch_lp   = total_lp   / len(ds)

        print(
            f"epoch {ep+1}: "
            f"L={epoch_loss:.6e} "
            f"Lb={epoch_lb:.6e} "
            f"Lp={epoch_lp:.6e}"
        )
        model.eval()
        with torch.no_grad():
            # データセットから適当に1枚取り出す
            test_x, _ = ds[0] 
            test_x = test_x.unsqueeze(0).to(device)
            recon = model(test_x).cpu().squeeze(0).numpy()
            original = test_x.cpu().squeeze(0).numpy()

            original_bottom = (original[0, :20, :] > 0.5).astype(int)
            recon_bottom = (recon[0, :20, :] > 0.5).astype(int)

            print("--- Original (Bottom 10 lines) ---")
            print(original_bottom)
            print("\n--- Reconstructed (Bottom 10 lines) ---")
            print(recon_bottom)
    
            # 全マスの不一致数をカウント
            diff = np.sum((original > 0.5) != (recon > 0.5))
            print(f"Total Bit Errors: {diff} / 800")


    torch.save(model.encoder.state_dict(), "encoder_phase1.pt")
    print("saved encoder_phase1.pt")
    torch.save(model.state_dict(), "autoencoder_test.pt")
    print("saved autoencoder_test.pt")

    return model



if __name__ == "__main__":
    model = train_phase1(
        npz_path="phase1_boards.npz",  # 生成したデータ
        epochs=2,                     # まずは10〜20で様子見
        batch_size=512,                # GPUなら256〜512, CPUなら64〜128
        lr=1e-3                         # 学習率はこれでOK
    )
