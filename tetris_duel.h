#pragma once
#include "tetris_core.h"
#include "tetris_rules.h"

enum class Winner {
    None,
    Player1,
    Player2,
    Draw
};

Winner judge_winner(const Board& b1, PieceType p1,
                    const Board& b2, PieceType p2);
