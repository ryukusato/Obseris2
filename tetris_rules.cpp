// tetris_rules.cpp
#include "tetris_rules.h"
#include "tetris_core.h"

#include <array>
#include <stdexcept>

static int norm_rot(int r){ r%=4; if(r<0) r+=4; return r; }

// ===== 形状 =====
static const std::array<Coords,4> SHAPE_T = {{
    {{-1,0},{0,0},{1,0},{0, 1}},
    {{0,-1},{0,0},{0,1},{1,0}},
    {{-1,0},{0,0},{1,0},{0,-1}},
    {{0,-1},{0,0},{0,1},{-1,0}},
}};
static const std::array<Coords,4> SHAPE_I = {{
    {{-1,0},{0,0},{1,0},{2,0}},
    {{1,-1},{1,0},{1,1},{1,2}},
    {{-1,1},{0,1},{1,1},{2,1}},
    {{0,-1},{0,0},{0,1},{0,2}},
}};
static const std::array<Coords,4> SHAPE_O = {{
    {{0,0},{1,0},{0,1},{1,1}},
    {{0,0},{1,0},{0,1},{1,1}},
    {{0,0},{1,0},{0,1},{1,1}},
    {{0,0},{1,0},{0,1},{1,1}},
}};
static const std::array<Coords,4> SHAPE_J = {{
    {{-1,0},{0,0},{1,0},{-1,1}},
    {{0,-1},{0,0},{0,1},{1,1}},
    {{-1,0},{0,0},{1,0},{1,-1}},
    {{0,-1},{0,0},{0,1},{-1,-1}},
}};
static const std::array<Coords,4> SHAPE_L = {{
    {{-1,0},{0,0},{1,0},{1,1}},
    {{0,-1},{0,0},{0,1},{1,-1}},
    {{-1,0},{0,0},{1,0},{-1,-1}},
    {{0,-1},{0,0},{0,1},{-1,1}},
}};
static const std::array<Coords,4> SHAPE_S = {{
    {{-1,0},{0,0},{0,1},{1,1}},
    {{0,1},{0,0},{1,0},{1,-1}},
    {{-1,-1},{0,-1},{0,0},{1,0}},
    {{-1,1},{-1,0},{0,0},{0,-1}},
}};
static const std::array<Coords,4> SHAPE_Z = {{
    {{-1,1},{0,1},{0,0},{1,0}},
    {{1,1},{1,0},{0,0},{0,-1}},
    {{-1,0},{0,0},{0,-1},{1,-1}},
    {{-1,1},{-1,0},{0,0},{0,-1}},
}};

const Coords& get_shape(PieceType p, int rot){
    rot = norm_rot(rot);
    switch(p){
        case PieceType::T: return SHAPE_T[rot];
        case PieceType::I: return SHAPE_I[rot];
        case PieceType::O: return SHAPE_O[rot];
        case PieceType::J: return SHAPE_J[rot];
        case PieceType::L: return SHAPE_L[rot];
        case PieceType::S: return SHAPE_S[rot];
        case PieceType::Z: return SHAPE_Z[rot];
    }
    throw std::runtime_error("bad piece");
}

// ===== SRS kicks（dyは下が-1系に合わせ済み）=====
static const std::vector<std::pair<int,int>> K0 = {{0,0}};

static const std::array<std::vector<std::pair<int,int>>,8> K_JLSTZ = {{
    {{ {0,0},{-1,0},{-1, 1},{0,-2},{-1,-2} }},
    {{ {0,0},{ 1,0},{ 1,-1},{0, 2},{ 1, 2} }},
    {{ {0,0},{ 1,0},{ 1,-1},{0, 2},{ 1, 2} }},
    {{ {0,0},{-1,0},{-1, 1},{0,-2},{-1,-2} }},
    {{ {0,0},{ 1,0},{ 1, 1},{0,-2},{ 1,-2} }},
    {{ {0,0},{-1,0},{-1,-1},{0, 2},{-1, 2} }},
    {{ {0,0},{-1,0},{-1,-1},{0, 2},{-1, 2} }},
    {{ {0,0},{ 1,0},{ 1, 1},{0,-2},{ 1,-2} }},
}};
static const std::array<std::vector<std::pair<int,int>>,8> K_I = {{
    {{ {0,0},{-2,0},{ 1,0},{-2,-1},{ 1, 2} }},
    {{ {0,0},{ 2,0},{-1,0},{ 2, 1},{-1,-2} }},
    {{ {0,0},{-1,0},{ 2,0},{-1, 2},{ 2,-1} }},
    {{ {0,0},{ 1,0},{-2,0},{ 1,-2},{-2, 1} }},
    {{ {0,0},{ 2,0},{-1,0},{ 2, 1},{-1,-2} }},
    {{ {0,0},{-2,0},{ 1,0},{-2,-1},{ 1, 2} }},
    {{ {0,0},{ 1,0},{-2,0},{ 1,-2},{-2, 1} }},
    {{ {0,0},{-1,0},{ 2,0},{-1, 2},{ 2,-1} }},
}};

static int kick_index(int f,int t){
    if(f==0&&t==1)return 0; if(f==1&&t==0)return 1;
    if(f==1&&t==2)return 2; if(f==2&&t==1)return 3;
    if(f==2&&t==3)return 4; if(f==3&&t==2)return 5;
    if(f==3&&t==0)return 6; if(f==0&&t==3)return 7;
    return -1;
}

const std::vector<std::pair<int,int>>&
get_kicks(PieceType p,int f,int t){
    if(p==PieceType::O) return K0;
    int i=kick_index(f,t);
    if(i<0) return K0;
    if(p==PieceType::I) return K_I[i];
    return K_JLSTZ[i];
}

// ===== スポーン位置再現 =====
std::pair<int,int>
spawn_position_with_fallback(const Board& board,
                             PieceType piece)
{
    int x = 4;
    int y = 20;  

    const Coords& s = get_shape(piece, 0);

    // 通常スポーン
    if (is_valid_position(board, s, x, y))
        return {x, y};

    // 1マス上にずらして再試行（公式仕様）
    if (is_valid_position(board, s, x, y + 1))
        return {x, y + 1};

    // どちらも無理ならゲームオーバー相当
    return {-1, -1};
}

