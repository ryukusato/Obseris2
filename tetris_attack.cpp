#include "tetris_attack.h"
#include <algorithm>

static const int BASE_ATTACK_CLEAR[5] = {
    0, // 0 lines
    0, // single
    1, // double
    2, // triple
    4  // tetris
};

static const int BASE_ATTACK_TSPIN[4] = {
    0, // 0
    2, // tspin1
    4, // tspin2
    6  // tspin3
};

static const int BASE_ATTACK_MINI[3] = {
    0, // 0
    0, // mini1
    1  // mini2
};

// CC と同様の簡易 combo テーブル
static const int COMBO_TABLE[12] = {
    0,0,1,1,2,2,3,3,4,4,4,5
};

int compute_attack(const Landing& l)
{
    int atk = 0;

    switch(l.kind){
        case ClearKind::Clear1: atk = BASE_ATTACK_CLEAR[1]; break;
        case ClearKind::Clear2: atk = BASE_ATTACK_CLEAR[2]; break;
        case ClearKind::Clear3: atk = BASE_ATTACK_CLEAR[3]; break;
        case ClearKind::Clear4: atk = BASE_ATTACK_CLEAR[4]; break;

        case ClearKind::Tspin1: atk = BASE_ATTACK_TSPIN[1]; break;
        case ClearKind::Tspin2: atk = BASE_ATTACK_TSPIN[2]; break;
        case ClearKind::Tspin3: atk = BASE_ATTACK_TSPIN[3]; break;

        case ClearKind::MiniTspin1: atk = BASE_ATTACK_MINI[1]; break;
        case ClearKind::MiniTspin2: atk = BASE_ATTACK_MINI[2]; break;

        default: atk = 0; break;
    }

    // B2B ボーナス
    bool is_b2b_action =
           l.kind == ClearKind::Tspin1
        || l.kind == ClearKind::Tspin2
        || l.kind == ClearKind::Tspin3
        || l.kind == ClearKind::Clear4;

    if(is_b2b_action && l.back_to_back)
        atk += 1;

    // combo ボーナス
    if(l.lines_cleared > 0){
        int c = std::min(l.combo, 11);
        atk += COMBO_TABLE[c];
    }

    // Perfect Clear ボーナス（簡易）
    if(l.perfect_clear)
        atk += 10;

    return atk;
}
