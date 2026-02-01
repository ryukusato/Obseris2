// tetris_bag.h
#pragma once
#include "tetris_rules.h"
#include <random>
#include <deque>


class PieceBag {
public:
    PieceBag(unsigned int seed = std::random_device{}());

    // 次のミノを1つ取り出す
    PieceType pop();

    // 先頭から n 個見る（ネクスト表示用）
    std::vector<PieceType> peek(int n) const;

private:
    std::mt19937 rng;
    std::deque<PieceType> queue;

    void refill(); // bag を1つ追加
};


