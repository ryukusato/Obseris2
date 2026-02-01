//  tetris_search.h
#pragma once
#include "tetris_core.h"
#include "tetris_rules.h"
#include "tetris_path.h"
#include <vector>

enum class ClearKind {
    None,
    Clear1, Clear2, Clear3, Clear4,
    Tspin1, Tspin2, Tspin3,
    MiniTspin1, MiniTspin2
};

struct Landing {
    Board board_after;
    int final_x, final_y, final_rot;
    int lines_cleared;
    int combo;
    PieceType piece;
    std::vector<Action> path;

    ClearKind kind = ClearKind::None;
    bool used_t_piece = false;
    bool perfect_clear = false;
    bool back_to_back = false; // これは後でゲーム状態側から渡してもOK
    bool used_hold = false;
    PieceType piece_after_hold;
    int attack = 0;
};


std::vector<Landing>
enumerate_landings(const Board& board,
                   PieceType piece,
                   int spawn_x,
                   int spawn_y,
                   int current_combo,
                   bool current_b2b);

std::vector<Landing>
enumerate_drop_landings_from_board(const Board& board,
                                    PieceType piece);
                   