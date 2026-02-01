// tetris_state.cpp
#include "tetris_state.h"

GameState::GameState(unsigned int seed)
    : board(TOTAL_BOARD_HEIGHT, std::vector<int>(BOARD_WIDTH, 0)),
      bag(seed)
{
    current = bag.pop();
    next    = bag.pop();

    auto [sx, sy] = spawn_position_with_fallback(board, current);
    spawn_x = sx;
    spawn_y = sy;
}

std::vector<Landing>
legal_moves(const GameState& s)
{
    if (s.spawn_x < 0) return {};

    std::vector<Landing> moves;

    // ===== 1) 通常の current を置く =====
    auto normal = enumerate_landings(
        s.board,
        s.current,
        s.spawn_x,
        s.spawn_y,
        s.combo,
        s.back_to_back
    );

    for(auto& l : normal){
        l.used_hold = false;
        l.piece_after_hold = s.current;
        moves.push_back(l);
    }

    // ===== 2) hold を使う手 =====
    if(!s.used_hold_this_turn){
        PieceType new_current;
        bool new_has_hold = true;

        if(!s.has_hold){
            // 初回hold: currentをholdへ、nextを使う
            new_current = s.next;
        } else {
            // holdとcurrentを交換
            new_current = s.hold_piece;
        }

        auto [sx, sy] = spawn_position_with_fallback(s.board, new_current);
        if(sx >= 0){
            auto hold_moves = enumerate_landings(
                s.board,
                new_current,
                sx, sy,
                s.combo,
                s.back_to_back
            );

            for(auto& l : hold_moves){
                l.used_hold = true;
                l.piece_after_hold = new_current;
                moves.push_back(l);
            }
        }
    }

    return moves;
}


GameState apply_move(GameState s, const Landing& l)
{
    // 盤面更新
    s.board = l.board_after;



    if(l.lines_cleared > 0)
    s.combo = s.combo + 1;
    else
    s.combo = 0;

    bool is_b2b_action =
       l.kind == ClearKind::Tspin1
    || l.kind == ClearKind::Tspin2
    || l.kind == ClearKind::Tspin3
    || l.kind == ClearKind::Clear4;

    if(is_b2b_action){
        // 今回B2B対象技を打った
        // 次もB2Bボーナスを受けられる状態に
    s.back_to_back = true;
    }
    else if(l.lines_cleared > 0){
        // ラインは消したがB2B対象ではない → 途切れ
    s.back_to_back = false;
    }
    // ラインを消していない場合は状態維持
    // ===== hold を使ったかどうかで分岐 =====
    if(l.used_hold){
        if(!s.has_hold){
            // 初回hold
            s.hold_piece = s.current;
            s.has_hold = true;

            s.current = s.next;
            s.next = s.bag.pop();
        } else {
            // swap hold
            std::swap(s.current, s.hold_piece);
        }
        s.used_hold_this_turn = true;
    } else {
        // 通常進行
        s.current = s.next;
        s.next = s.bag.pop();
        s.used_hold_this_turn = false;
    }

    auto [sx, sy] = spawn_position_with_fallback(s.board, s.current);
    s.spawn_x = sx;
    s.spawn_y = sy;

    return s;
}

bool is_dead_state(const GameState& s)
{
    // スポーンできない＝即死
    if (s.spawn_x < 0) return true;

    // 合法手を列挙
    auto moves = enumerate_landings(
        s.board,
        s.current,
        s.spawn_x,
        s.spawn_y,
        s.combo,
        s.back_to_back
    );

    // 1手も置けない＝詰み
    return moves.empty();
}