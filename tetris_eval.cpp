#include "tetris_eval.h"
#include <algorithm>
#include <cmath>

// ---------- 列高さ ----------
static std::array<int,BOARD_WIDTH>
column_heights(const Board& b){
    std::array<int,BOARD_WIDTH> h{};
    for(int x=0;x<BOARD_WIDTH;x++){
        int y;
        for(y=TOTAL_BOARD_HEIGHT-1;y>=0;y--)
            if(b[y][x]) break;
        h[x]=y+1;
    }
    return h;
}

// ---------- 穴 ----------
static int cavity_cells(const Board& b,
                        const std::array<int,BOARD_WIDTH>& h){
    int holes=0;
    for(int x=0;x<BOARD_WIDTH;x++)
        for(int y=0;y<h[x];y++)
            if(!b[y][x]) holes++;
    return holes;
}

// ---------- 凹凸 ----------
static std::pair<int,int>
bumpiness_excluding_well(const std::array<int,BOARD_WIDTH>& h,
                         int well)
{
    int sum = -1, sum_sq = -1;

    int prev = (well == 0 ? 1 : 0);

    for(int i = 1; i < BOARD_WIDTH; ++i){
        if(i == well) continue;
        int d = std::abs(h[prev] - h[i]);
        sum    += d;
        sum_sq += d*d;
        prev = i;
    }
    return {std::abs(sum), std::abs(sum_sq)};
}


// ---------- row transitions ----------
static int row_transitions(const Board& b){
    int t=0;
    for(int y=0;y<TOTAL_BOARD_HEIGHT;y++){
        int prev=1;
        for(int x=0;x<BOARD_WIDTH;x++){
            int cur=b[y][x]?1:0;
            if(cur!=prev) t++;
            prev=cur;
        }
        if(prev==0) t++;
    }
    return t;
}

// ---------- covered cells ----------
static std::pair<int,int>
covered_cells(const Board& b,
              const std::array<int,BOARD_WIDTH>& h){
    int covered=0, covered_sq=0;
    for(int x=0;x<BOARD_WIDTH;x++){
        bool hole=false;
        for(int y=0;y<h[x];y++){
            if(!b[y][x]) hole=true;
            else if(hole){
                int cells = std::min(6, h[x]-y-1);
                covered += cells;
                covered_sq += cells*cells;

            }
        }
    }
    return {covered,covered_sq};
}

// ---------- 井戸 ----------
static std::pair<int,int>
well_depth(const Board& b,
           const std::array<int,BOARD_WIDTH>& h,
           int cap){
    int well=0;
    for(int x=1;x<BOARD_WIDTH;x++)
        if(h[x]<=h[well]) well=x;

    int depth=0;
    for(int y=h[well];y<TOTAL_BOARD_HEIGHT;y++){
        bool solid=true;
        for(int x=0;x<BOARD_WIDTH;x++){
            if(x==well) continue;
            if(!b[y][x]){solid=false;break;}
        }
        if(!solid) break;
        depth++;
    }
    depth = std::min(depth,cap);
    return {depth,well};
}

// ---------- cavities & overhangs (CC準拠) ----------
static std::pair<int,int>
cavities_and_overhangs(const Board& b,
                       const std::array<int,BOARD_WIDTH>& h)
{
    int cavities = 0;
    int overhangs = 0;

    int maxh = *std::max_element(h.begin(), h.end());

    for(int y = 0; y < maxh; ++y){
        for(int x = 0; x < BOARD_WIDTH; ++x){

            if (y >= h[x]) continue;     // そもそも柱より上
            if (b[y][x]) continue;       // ブロックがある

            bool left_overhang  = false;
            bool right_overhang = false;

            if (x > 1){
                if (h[x-1] <= y-1 && h[x-2] <= y)
                    left_overhang = true;
            }
            if (x < BOARD_WIDTH-2){
                if (h[x+1] <= y-1 && h[x+2] <= y)
                    right_overhang = true;
            }

            if (left_overhang || right_overhang)
                overhangs++;
            else
                cavities++;
        }
    }
    return {cavities, overhangs};
}

static bool is_filled(const Board& b, int x, int y){
    if(x < 0 || x >= BOARD_WIDTH || y < 0 || y >= TOTAL_BOARD_HEIGHT)
        return true; // 壁は埋まっている扱い
    return b[y][x] != 0;
}

static bool t_slot_center(const Board& b, int cx, int cy){
    // 中心の上下左右が空いていて設置可能な空洞
    if(is_filled(b, cx, cy)) return false;
    if(!is_filled(b, cx, cy-1)) return false; // 下に支えがある想定

    int corners =
        is_filled(b, cx-1, cy-1) +
        is_filled(b, cx+1, cy-1) +
        is_filled(b, cx-1, cy+1) +
        is_filled(b, cx+1, cy+1);

    return corners >= 3;
}

static int simulate_tspin_lines(const Board& b, int cx, int cy){
    Board tmp = b;

    // Tミノを上向きで埋める簡易モデル
    const int dx[4] = {0, -1, 0, 1};
    const int dy[4] = {0,  0, 1, 0};

    for(int i=0;i<4;i++){
        int x = cx + dx[i];
        int y = cy + dy[i];
        if(x>=0 && x<BOARD_WIDTH && y>=0 && y<TOTAL_BOARD_HEIGHT)
            tmp[y][x] = 1;
    }

    int cleared = 0;
    for(int y=0;y<TOTAL_BOARD_HEIGHT;y++){
        bool full = true;
        for(int x=0;x<BOARD_WIDTH;x++)
            if(!tmp[y][x]){ full=false; break; }
        if(full) cleared++;
    }
    return cleared; // 0〜3 を想定
}

static bool cutout_once(Board& b,int& lines){
    for(int y=1;y<TOTAL_BOARD_HEIGHT-1;y++){
        for(int x=1;x<BOARD_WIDTH-1;x++){
            if(!t_slot_center(b,x,y)) continue;

            lines = simulate_tspin_lines(b,x,y);
            if(lines==0) continue;

            // 実際に埋めてライン消去
            const int dx[4]={0,-1,0,1};
            const int dy[4]={0,0,1,0};
            for(int i=0;i<4;i++){
                int xx=x+dx[i], yy=y+dy[i];
                if(0<=xx&&xx<BOARD_WIDTH&&0<=yy&&yy<TOTAL_BOARD_HEIGHT)
                    b[yy][xx]=1;
            }

            // ライン消去
            for(int yy=0;yy<TOTAL_BOARD_HEIGHT;yy++){
                bool full=true;
                for(int xx=0;xx<BOARD_WIDTH;xx++)
                    if(!b[yy][xx]){full=false;break;}
                if(full){
                    for(int k=yy;k<TOTAL_BOARD_HEIGHT-1;k++)
                        b[k]=b[k+1];
                    std::fill(b[TOTAL_BOARD_HEIGHT-1].begin(), b[TOTAL_BOARD_HEIGHT-1].end(), 0);
                    yy--;
                }
            }
            return true;
        }
    }
    return false;
}

static std::array<int,4> detect_tslots(const Board& b){
    std::array<int,4> cnt{0,0,0,0};

    for(int y=1;y<TOTAL_BOARD_HEIGHT-1;y++){
        for(int x=1;x<BOARD_WIDTH-1;x++){
            if(!t_slot_center(b, x, y)) continue;

            int lines = simulate_tspin_lines(b, x, y);
            if(lines >=0 && lines <=3)
                cnt[lines]++; // tslot[lines]
        }
    }
    return cnt;
}

static const int COMBO_TABLE[12] = {
    0, // combo 0
    0, // 1
    1, // 2
    1, // 3
    2, // 4
    2, // 5
    3, // 6
    3, // 7
    4, // 8
    4, // 9
    4, // 10
    5  // 11+
};



// ---------- 盤面評価 ----------
int evaluate_board(const Board& board, const EvalWeights& w)
{
    auto h = column_heights(board);
    int maxh = *std::max_element(h.begin(),h.end());

    auto [cavities, overhangs] = cavities_and_overhangs(board, h);
    int cavities_sq   = cavities * cavities;
    int overhangs_sq  = overhangs * overhangs;
    int rtrans = row_transitions(board);
    auto [cov,cov_sq] = covered_cells(board,h);
    auto [wdepth,wcol] = well_depth(board, h, w.max_well_cap);
    auto [bump,bump_sq] = bumpiness_excluding_well(h, wcol);

    int score=0;
    Board tmp = board;

    score += w.height       * maxh;
    score += w.bumpiness    * bump;
    score += w.bumpiness_sq * bump_sq;
    score += w.row_trans    * rtrans;
    score += w.covered      * cov;
    score += w.covered_sq   * cov_sq;
    score += w.cavity_cells     * cavities;
    score += w.cavity_cells_sq  * cavities_sq;
    score += w.overhang_cells   * overhangs;
    score += w.overhang_cells_sq* overhangs_sq;

    
    while(true){
        int lines=0;
        if(!cutout_once(tmp,lines)) break;
        if(0<=lines&&lines<=3)
            score += w.tslot[lines];
    }


    // 上側危険度
    score += w.top_half    * std::max(0, maxh-10);
    score += w.top_quarter * std::max(0, maxh-15);

    // 井戸
    if(wdepth>0){
        score += w.well_depth * wdepth;
        score += w.well_column[wcol];
    }

    return score;
}



// ---------- ライン消去報酬込み ----------
int evaluate_landing(const Landing& l, const EvalWeights& w)
{
    int score = evaluate_board(l.board_after, w);

    // ---- Perfect Clear ----
    if(l.perfect_clear){
        score += w.perfect_clear;
    }

    // ---- Back-to-Back ----
    bool is_b2b_action =
       l.kind == ClearKind::Tspin1
    || l.kind == ClearKind::Tspin2
    || l.kind == ClearKind::Tspin3
    || l.kind == ClearKind::Clear4;

    if(l.back_to_back && is_b2b_action){
        score += w.b2b_clear;
        }


    // ---- 消し方ごとの報酬 ----
    switch(l.kind){
        case ClearKind::Clear1: score += w.clear1; break;
        case ClearKind::Clear2: score += w.clear2; break;
        case ClearKind::Clear3: score += w.clear3; break;
        case ClearKind::Clear4: score += w.clear4; break;

        case ClearKind::Tspin1: score += w.tspin1; break;
        case ClearKind::Tspin2: score += w.tspin2; break;
        case ClearKind::Tspin3: score += w.tspin3; break;

        case ClearKind::MiniTspin1: score += w.mini_tspin1; break;
        case ClearKind::MiniTspin2: score += w.mini_tspin2; break;

        default: break;
    }

    // ---- wasted T（Tを使ったのにTスピンでない）----
    if(l.used_t_piece && l.lines_cleared == 0){
        bool is_tspin =
            l.kind == ClearKind::Tspin1 ||
            l.kind == ClearKind::Tspin2 ||
            l.kind == ClearKind::Tspin3;

        if(!is_tspin){
            score += w.wasted_t;
        }
    }

    if(l.lines_cleared > 0){
        int c = std::min(l.combo, 11);
        score += w.combo_bonus * COMBO_TABLE[c];
    }
    

    return score;
}
