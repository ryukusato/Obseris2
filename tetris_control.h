// tetris_control.h
#pragma once
#include "tetris_core.h"

// 左右移動 (dx = -1 or +1)
bool try_move(const Board& board,
              const Coords& shape,
              int& x, int& y,
              int dx);

// ソフトドロップ1マス
bool try_soft_drop(const Board& board,
                   const Coords& shape,
                   int& x, int& y);

// ハードドロップ先の y を求める
int hard_drop_y(const Board& board,
                const Coords& shape,
                int x, int start_y);
