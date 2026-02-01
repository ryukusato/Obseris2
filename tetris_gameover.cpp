//tetris_gameover.cpp
#include "tetris_gameover.h"

bool is_dead(const Board& board, PieceType piece)
{
    // ① スポーン可能か（y と y+1 の両方で失敗なら死亡）
    auto [sx, sy] = spawn_position_with_fallback(board, piece);
    if (sx < 0) {
        return true; // 出現不能トップアウト
    }

    // ② 実際にそのミノを落としてみる
    const Coords& shape = get_shape(piece, 0); // スポーン回転

    int final_y = drop_piece(board, shape, sx, sy);

    // 仮に置いてみる（ライン消去はしない）
    Board placed = place_piece(board, shape, sx, final_y);

    // ③ その結果、最上段を使ってしまうか
    int top = TOTAL_BOARD_HEIGHT - 1;
    for (int x = 0; x < BOARD_WIDTH; ++x) {
        if (placed[top][x] != 0) {
            return true; // このミノを置くと上端に触れる＝死亡
        }
    }

    return false;
}
