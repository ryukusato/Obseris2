import os
import torch
import numpy as np
from phase2_train import TetrisValueNetwork
# フェーズ2で定義したモデルクラスをインポート（またはここに記述）
# from model import TetrisValueNetwork 

def export_model(pth_path, output_name_base):
    """
    pth_path: 学習済み重みファイル (value_model.pt など)
    output_name_base: 保存するファイル名のベース
    """
    print(f"Loading weights from {pth_path}...")
    
    # 1. モデルの初期化 (以前の ResidualBlock, TetrisValueNetwork の定義を使用)
    # ここではインスタンス化のみ行う
    model = TetrisValueNetwork("encoder_phase1.pt") 
    
    try:
        # 重みをロード
        model.load_state_dict(torch.load(pth_path, map_location="cpu"))
        model.eval()
        print("Model loaded successfully.")
    except Exception as e:
        print(f"Error loading model: {e}")
        return

    # 2. ダミー入力の作成 (Batch=1, Channel=2, Height=40, Width=10)
    dummy_input = torch.randn(1, 2, 40, 10)

    # --- パターンA: TorchScript (C++ LibTorch 用に推奨) ---
    try:
        traced_model = torch.jit.trace(model, dummy_input)
        traced_model.save(f"{output_name_base}.pt")
        print(f"TorchScript exported: {output_name_base}.pt")
    except Exception as e:
        print(f"TorchScript export failed: {e}")

    # --- パターンB: ONNX (ONNX Runtime などを使う場合) ---
    try:
        torch.onnx.export(
            model,
            dummy_input,
            f"{output_name_base}.onnx",
            export_params=True,
            opset_version=15,
            do_constant_folding=True,
            input_names=['board_input'],
            output_names=['value_output'],
            dynamic_axes={'board_input': {0: 'batch_size'}, 'value_output': {0: 'batch_size'}}
        )
        print(f"ONNX exported: {output_name_base}.onnx")
    except Exception as e:
        print(f"ONNX export failed: {e}")

if __name__ == "__main__":
    # 学習済みモデルのパスを指定
    pth_file = "value_model.pt"
    if os.path.exists(pth_file):
        export_model(pth_file, "tetris_value_final")
    else:
        print(f"File not found: {pth_file}")