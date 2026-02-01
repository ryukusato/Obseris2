// tetris_step.cpp
#include "tetris_core.h"
#include "tetris_step.h"

static bool occupied_or_wall(const Board& b, int x, int y){
    if(x < 0 || x >= BOARD_WIDTH || y < 0 || y >= TOTAL_BOARD_HEIGHT)
        return true; // 壁は埋まっている扱い
    return b[y][x] != 0;
}


StepResult step_lock_piece(const Board& board,
    PieceType piece,
    const Coords& shape,
    int x, int start_y)
{
// 1. ハードドロップ
int y = drop_piece(board, shape, x, start_y);

// 2. 固定前の盤面に置く
Board placed = place_piece(board, shape, x, y);

// ===== T-spin 判定（ライン消去前の盤面で）=====
bool is_tspin = false;
bool is_mini  = false;

if(piece == PieceType::T){
// 回転中心（実装依存だが多くは (x+1, y+1)）
int cx = x + 1;
int cy = y + 1;

int corners =
occupied_or_wall(placed, cx-1, cy-1) +
occupied_or_wall(placed, cx+1, cy-1) +
occupied_or_wall(placed, cx-1, cy+1) +
occupied_or_wall(placed, cx+1, cy+1);

if(corners >= 3){
is_tspin = true;

// 簡易 mini 判定：
// 上側2角が空いていたら mini 扱い
bool upper_left  = occupied_or_wall(placed, cx-1, cy+1);
bool upper_right = occupied_or_wall(placed, cx+1, cy+1);

if(!(upper_left && upper_right)){
is_mini = true;
}
}
}

// 3. ライン消去
auto [after_clear, lines] = clear_lines(placed);

// 4. Perfect Clear 判定
bool pc = true;
for(int yy=0; yy<TOTAL_BOARD_HEIGHT && pc; ++yy)
for(int xx=0; xx<BOARD_WIDTH; ++xx)
if(after_clear[yy][xx]){
pc = false;
break;
}

StepResult r;
r.board_after     = std::move(after_clear);
r.final_y         = y;
r.lines_cleared   = lines;
r.is_tspin        = is_tspin;
r.is_mini_tspin   = is_tspin && is_mini;
r.perfect_clear   = pc;
return r;
}
