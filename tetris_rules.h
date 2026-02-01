// tetris_rules.h
#pragma once
#include <vector>
#include <utility>
#include <array>
#include "tetris_core.h"

// ===== ピース種類 =====
enum class PieceType { I, O, T, S, Z, J, L };

constexpr int PIECE_COUNT = 7;

// 7bag 用
inline constexpr std::array<PieceType, PIECE_COUNT> ALL_PIECES = {
    PieceType::I, PieceType::O, PieceType::T,
    PieceType::S, PieceType::Z,
    PieceType::J, PieceType::L
};

// ===== 形状 =====
const Coords& get_shape(PieceType p, int rot);

// ===== SRS wall kick =====
const std::vector<std::pair<int,int>>&
get_kicks(PieceType p, int from_rot, int to_rot);

// ===== スポーン位置（忠実再現版）=====
// 基本位置に置けなければ y+1 を1回だけ試す
std::pair<int,int>
spawn_position_with_fallback(const Board& board,
                             PieceType piece);
