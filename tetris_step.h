// tetris_step.h
#pragma once
#include "tetris_core.h"
#include "tetris_rules.h"

struct StepResult {
    Board board_after;
    int final_y;
    int lines_cleared;

    bool is_tspin = false;
    bool is_mini_tspin = false;
    bool perfect_clear = false;
};


// 1手確定（固定＋ライン消去）
StepResult step_lock_piece(const Board& board,
    PieceType piece,
    const Coords& shape,
    int x, int start_y);

