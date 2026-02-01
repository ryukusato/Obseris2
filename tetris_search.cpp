// tetris_search.cpp
#include "tetris_core.h"
#include "tetris_step.h"
#include "tetris_path.h"
#include "tetris_search.h"
#include "tetris_rules.h"
#include "tetris_attack.h"

std::vector<Landing>
enumerate_landings(const Board& board,
                   PieceType piece,
                   int spawn_x,
                   int spawn_y,
                   int current_combo,
                   bool current_b2b)
{
    std::vector<Landing> out;

    // 全回転
    for (int rot = 0; rot < 4; ++rot) {
        const Coords& shape = get_shape(piece, rot);

        // 横全探索
        for (int x = -4; x < BOARD_WIDTH + 4; ++x) {

            int try_spawn_y = spawn_y;
        
            if (!is_valid_position(board, shape, x, try_spawn_y)) {
                try_spawn_y = spawn_y + 1;
                if (!is_valid_position(board, shape, x, try_spawn_y))
                    continue;
            }
        
            int y = drop_piece(board, shape, x, try_spawn_y);
        
            std::vector<Action> path =
                find_path(board, piece,
                          spawn_x, spawn_y, 0,
                          x, y, rot);
        
            if (path.empty())
                continue;
        
                StepResult r = step_lock_piece(board, piece, shape, x, try_spawn_y);

        
            Landing l;
            l.piece         = piece;
            l.board_after   = std::move(r.board_after);
            l.final_x       = x;
            l.final_y       = r.final_y;
            l.final_rot     = rot;
            l.lines_cleared = r.lines_cleared;
            l.path          = std::move(path);
            l.combo         = (current_combo >= 0 && r.lines_cleared > 0) ? current_combo + 1 : 0;
            l.back_to_back  = current_b2b;

            l.used_t_piece  = (piece == PieceType::T);
            l.perfect_clear = r.perfect_clear;
            

            // ClearKind 判定
            if (l.used_t_piece && r.is_tspin) {
                if (r.lines_cleared == 1)      l.kind = ClearKind::Tspin1;
                else if (r.lines_cleared == 2) l.kind = ClearKind::Tspin2;
                else if (r.lines_cleared == 3) l.kind = ClearKind::Tspin3;
                }   
            else if (l.used_t_piece && r.is_mini_tspin) {
                if (r.lines_cleared == 1)      l.kind = ClearKind::MiniTspin1;
                else if (r.lines_cleared == 2) l.kind = ClearKind::MiniTspin2;
                }
            else {
                switch (r.lines_cleared) {
                    case 1: l.kind = ClearKind::Clear1; break;
                    case 2: l.kind = ClearKind::Clear2; break;
                    case 3: l.kind = ClearKind::Clear3; break;
                    case 4: l.kind = ClearKind::Clear4; break;
                    default: l.kind = ClearKind::None;  break;
                    }
                }

            l.attack = compute_attack(l);
            out.push_back(std::move(l));
        }
        
    }
    return out;
}

std::vector<Landing>
enumerate_drop_landings_from_board(const Board& board,
                                   PieceType piece)
{
    std::vector<Landing> out;

    for (int rot = 0; rot < 4; ++rot) {
        const Coords& shape = get_shape(piece, rot);

        for (int x = -4; x < BOARD_WIDTH + 4; ++x) {

            // 上から十分高い位置から単純落下
            int start_y = TOTAL_BOARD_HEIGHT - 1;

            if (!is_valid_position(board, shape, x, start_y))
                continue;

            int y = drop_piece(board, shape, x, start_y);

            StepResult r = step_lock_piece(board, piece, shape, x, start_y);

            Landing l;
            l.piece         = piece;
            l.board_after   = std::move(r.board_after);
            l.final_x       = x;
            l.final_y       = r.final_y;
            l.final_rot     = rot;
            l.lines_cleared = r.lines_cleared;
            l.combo         = 0;
            l.back_to_back  = false;

            l.used_t_piece  = (piece == PieceType::T);
            l.perfect_clear = r.perfect_clear;

            // ClearKind 判定（既存と同じロジック）
            if (l.used_t_piece && r.is_tspin) {
                if (r.lines_cleared == 1)      l.kind = ClearKind::Tspin1;
                else if (r.lines_cleared == 2) l.kind = ClearKind::Tspin2;
                else if (r.lines_cleared == 3) l.kind = ClearKind::Tspin3;
            }
            else if (l.used_t_piece && r.is_mini_tspin) {
                if (r.lines_cleared == 1)      l.kind = ClearKind::MiniTspin1;
                else if (r.lines_cleared == 2) l.kind = ClearKind::MiniTspin2;
            }
            else {
                switch (r.lines_cleared) {
                    case 1: l.kind = ClearKind::Clear1; break;
                    case 2: l.kind = ClearKind::Clear2; break;
                    case 3: l.kind = ClearKind::Clear3; break;
                    case 4: l.kind = ClearKind::Clear4; break;
                    default: l.kind = ClearKind::None;  break;
                }
            }

            l.attack = compute_attack(l);
            out.push_back(std::move(l));
        }
    }
    return out;
}

