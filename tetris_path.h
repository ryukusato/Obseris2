// tetris_path.h
#pragma once
#include "tetris_core.h"
#include "tetris_rules.h"
#include <vector>

enum class Action {
    MoveLeft,
    MoveRight,
    SoftDrop,
    RotateCW,
    RotateCCW
};

// 到達可能なら操作列を返す。
// 不可能なら空ベクタ。
std::vector<Action>
find_path(const Board& board,
          PieceType piece,
          int start_x, int start_y, int start_rot,
          int target_x, int target_y, int target_rot);
