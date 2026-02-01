// tetris_state.h
#pragma once
#include "tetris_core.h"
#include "tetris_rules.h"
#include "tetris_search.h"
#include "tetris_bag.h"

struct GameState {
    Board board;

    PieceType current;
    PieceType next;

    PieceBag bag;

    int spawn_x;
    int spawn_y;

    int combo;
    bool back_to_back;

    bool has_hold = false;
    PieceType hold_piece;          // has_hold=false のときは未使用
    bool used_hold_this_turn = false;

    GameState(unsigned int seed = 0);
};

std::vector<Landing>
legal_moves(const GameState& s);
GameState apply_move(GameState s, const Landing& l);

bool is_dead_state(const GameState& s);