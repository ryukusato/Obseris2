// tetris_core.cpp
#include "tetris_core.h"
#include <algorithm>

bool is_valid_position(const Board& board,
                       const Coords& shape,
                       int px, int py)
{
    for (auto [dx, dy] : shape) {
        int x = px + dx;
        int y = py + dy;

        if (x < 0 || x >= BOARD_WIDTH) return false;
        if (y < 0 || y >= TOTAL_BOARD_HEIGHT) return false;
        if (board[y][x] != 0) return false;
    }
    return true;
}

int drop_piece(const Board& board,
               const Coords& shape,
               int start_x, int start_y)
{
    int y = start_y;
    while (is_valid_position(board, shape, start_x, y - 1)) {
        y--;
    }
    return y;
}

Board place_piece(const Board& board,
                  const Coords& shape,
                  int px, int py,
                  int value)
{
    Board next = board;
    for (auto [dx, dy] : shape) {
        int x = px + dx;
        int y = py + dy;
        next[y][x] = value; // 1 など
    }
    return next;
}

std::pair<Board,int> clear_lines(const Board& board)
{
    Board out(TOTAL_BOARD_HEIGHT,
              std::vector<int>(BOARD_WIDTH, 0));

    int write_row = 0;
    int cleared = 0;

    for (int y = 0; y < TOTAL_BOARD_HEIGHT; ++y) {
        bool full = true;
        for (int x = 0; x < BOARD_WIDTH; ++x) {
            if (board[y][x] == 0) {
                full = false;
                break;
            }
        }
        if (!full) {
            out[write_row++] = board[y];
        } else {
            cleared++;
        }
    }
    // 上側は空行で埋める
    while (write_row < TOTAL_BOARD_HEIGHT) {
        out[write_row++] = std::vector<int>(BOARD_WIDTH, 0);
    }

    return {out, cleared};
}
