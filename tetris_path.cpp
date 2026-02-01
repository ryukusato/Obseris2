// tetris_path.cpp
#include "tetris_path.h"
#include "tetris_rules.h"
#include <queue>
#include <map>
#include <tuple>
#include <algorithm>

// BFS用ノード
struct Node {
    int x;
    int y;
    int rot;

    bool operator<(const Node& other) const {
        if (x != other.x) return x < other.x;
        if (y != other.y) return y < other.y;
        return rot < other.rot;
    }
    bool operator==(const Node& other) const {
        return x == other.x && y == other.y && rot == other.rot;
    }
};

std::vector<Action>
find_path(const Board& board,
          PieceType piece,
          int start_x, int start_y, int start_rot,
          int target_x, int target_y, int target_rot)
{
    std::queue<Node> q;
    std::map<Node, Node> parent;              // 親ノード
    std::map<Node, Action> action_from_parent; // 親からの操作

    Node start{start_x, start_y, start_rot};
    Node goal {target_x, target_y, target_rot};

    q.push(start);
    parent[start] = start; // 開始点の親は自分

    while (!q.empty()) {
        Node cur = q.front(); q.pop();

        if (cur == goal)
            break;

        // 現在形状
        const Coords& shape = get_shape(piece, cur.rot);

        // ===== 平行移動 =====
        const struct {
            int dx, dy;
            Action act;
        } moves[3] = {
            {-1, 0, Action::MoveLeft},
            { 1, 0, Action::MoveRight},
            { 0,-1, Action::SoftDrop}
        };

        for (auto m : moves) {
            Node nxt{cur.x + m.dx, cur.y + m.dy, cur.rot};

            if (parent.count(nxt)) continue;
            if (!is_valid_position(board, shape, nxt.x, nxt.y)) continue;

            parent[nxt] = cur;
            action_from_parent[nxt] = m.act;
            q.push(nxt);
        }

        // ===== 回転（SRSキック付き）=====
        for (int dir = 0; dir < 2; ++dir) {
            bool cw = (dir == 0);
            Action act = cw ? Action::RotateCW : Action::RotateCCW;

            int next_rot = (cur.rot + (cw ? 1 : 3)) % 4;
            const Coords& next_shape = get_shape(piece, next_rot);

            const auto& kicks = get_kicks(piece, cur.rot, next_rot);

            for (auto [kx, ky] : kicks) {
                Node nxt{cur.x + kx, cur.y + ky, next_rot};

                if (parent.count(nxt)) continue;
                if (!is_valid_position(board, next_shape, nxt.x, nxt.y)) continue;

                parent[nxt] = cur;
                action_from_parent[nxt] = act;
                q.push(nxt);
            }
        }
    }

    // ゴール未到達
    if (!parent.count(goal))
        return {};

    // ===== 経路復元 =====
    std::vector<Action> path;
    Node cur = goal;

    while (!(cur == start)) {
        Action a = action_from_parent[cur];
        path.push_back(a);
        cur = parent[cur];
    }

    std::reverse(path.begin(), path.end());
    return path;
}
