// tetris_control.cpp
#include "tetris_core.h"
#include "tetris_control.h"

// 左右移動
bool try_move(const Board& board,
              const Coords& shape,
              int& x, int& y,
              int dx)
{
    int nx = x + dx;
    if (is_valid_position(board, shape, nx, y)) {
        x = nx;
        return true;
    }
    return false;
}

// 下移動（ソフトドロップ1マス）
bool try_soft_drop(const Board& board,
                   const Coords& shape,
                   int& x, int& y)
{
    int ny = y - 1;
    if (is_valid_position(board, shape, x, ny)) {
        y = ny;
        return true;
    }
    return false;
}

// ハードドロップ位置だけ計算（盤面は変えない）
int hard_drop_y(const Board& board,
                const Coords& shape,
                int x, int start_y)
{
    return drop_piece(board, shape, x, start_y);
}
