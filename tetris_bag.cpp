// tetris_bag.cpp
#include "tetris_bag.h"

PieceBag::PieceBag(unsigned int seed) : rng(seed) {
    refill();
    refill(); // 最初は2bag分入れておく
}

void PieceBag::refill() {
    std::array<PieceType, PIECE_COUNT> bag = ALL_PIECES;
    std::shuffle(bag.begin(), bag.end(), rng);
    for (auto p : bag) queue.push_back(p);
}

PieceType PieceBag::pop() {
    if (queue.size() <= PIECE_COUNT)
        refill();

    PieceType p = queue.front();
    queue.pop_front();
    return p;
}

std::vector<PieceType> PieceBag::peek(int n) const {
    std::vector<PieceType> out;
    for (int i = 0; i < n && i < (int)queue.size(); ++i)
        out.push_back(queue[i]);
    return out;
}
