#pragma once
#include "tetris_core.h"

// 最大 max_receive 行までしか受けない
// 実際に適用した行数を返す
int apply_garbage(Board& b, int lines, int hole_x, int max_receive = 10);

