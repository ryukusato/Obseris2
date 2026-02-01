#include "tetris_garbage.h"
#include <algorithm>

int apply_garbage(Board& b, int lines, int hole_x, int max_receive)
{
    int applied = std::min(lines, max_receive);

    for(int i=0;i<applied;i++){
        for(int y = TOTAL_BOARD_HEIGHT-1; y > 0; --y){
            b[y] = b[y-1];
        }

        std::vector<int> row(BOARD_WIDTH, 1);
        if(hole_x >= 0 && hole_x < BOARD_WIDTH)
            row[hole_x] = 0;

        b[0] = row;
    }

    return applied;
}
