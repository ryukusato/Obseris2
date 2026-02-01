#pragma once
#include "tetris_core.h"
#include "tetris_search.h"
#include <array>

struct EvalWeights {
    int height        = -39;
    int bumpiness     = -24;
    int bumpiness_sq  = -7;

    int row_trans     = -5;
    int covered       = -17;
    int covered_sq    = -1;

    int cavity_cells      = -173;
    int cavity_cells_sq   = -3;
    int overhang_cells    = -34;
    int overhang_cells_sq = -1;

    int top_half      = -150;
    int top_quarter   = -511;

    int well_depth    = 57;
    int max_well_cap  = 17;

    // B2B
    int b2b_clear = 104;

    // Tスピン
    int tspin1 = 121;
    int tspin2 = 410;
    int tspin3 = 602;

    // Mini Tスピン（使わないなら後回しでもOK）
    int mini_tspin1 = -158;
    int mini_tspin2 = -93;

    // Perfect Clear
    int perfect_clear = 999;

    // 無駄T置き
    int wasted_t = -152;
    int combo_bonus = 150; // CC の combo_garbage 相当

    
    

    
    std::array<int,10> well_column = {
        20,23,20,50,59,21,59,10,-10,24
    };

    // Tスロット（0,1,2,3ライン消去）
    std::array<int,4> tslot = {8,148,192,407};

    // ライン消去報酬
    int clear1 = -143;
    int clear2 = -100;
    int clear3 = -58;
    int clear4 = 390;
};

// 盤面評価（Tスロット連鎖込み）
int evaluate_board(const Board& board, const EvalWeights& w);

// ライン消去込み評価
int evaluate_landing(const Landing& l, const EvalWeights& w);
