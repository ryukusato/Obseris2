// tetris_core.h
#pragma once
#include <vector>
#include <utility>

constexpr int BOARD_WIDTH = 10;
constexpr int TOTAL_BOARD_HEIGHT = 40;

using Board  = std::vector<std::vector<int>>;
using Coords = std::vector<std::pair<int,int>>;

// その位置にその形が置けるか
bool is_valid_position(const Board& board,
                       const Coords& shape,
                       int px, int py);

// 下に落としたときの最終y
int drop_piece(const Board& board,
               const Coords& shape,
               int start_x, int start_y);

// ピースを固定した新しい盤面
Board place_piece(const Board& board,
                  const Coords& shape,
                  int px, int py,
                  int value = 1);

// ライン消去して(新盤面, 消去ライン数)
std::pair<Board,int> clear_lines(const Board& board);
