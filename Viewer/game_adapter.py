# viewer/game_adapter.py
import numpy as np
import random
from dataclasses import dataclass
from typing import Callable, Optional, List, Tuple, Any

import tetris_cpp as tc


# -------------------------
# 評価関数
# -------------------------

def heuristic_evaluator(state: tc.GameState, landing: tc.Landing) -> float:
    return float(tc.evaluate_landing(landing))


class CNNEvaluator:
    """
    model(x) がスカラーを返す callable を想定。
    x は (1, 1, 20, 10) の numpy float32。
    """
    def __init__(self, model: Any):
        self.model = model

    def __call__(self, state: tc.GameState, landing: tc.Landing) -> float:
        next_state = tc.apply_move(state, landing)
        board = np.array(next_state.board, dtype=np.float32)
        x = board[None, None, :, :]
        v = self.model(x)
        if hasattr(v, "item"):
            return float(v.item())
        return float(v)


from dataclasses import dataclass

@dataclass
class StepDebug:
    best_score: float = 0.0
    best_landing: Optional[tc.Landing] = None
    topk: List[Tuple[float, tc.Landing]] = None
    board_before_clear: Optional[list] = None
    lines_cleared: int = 0



# -------------------------
# 1P Adapter
# -------------------------

class GameAdapter:
    """
    1P 用。後で 2P にしたいなら DuelGameAdapter を使う。
    """
    def __init__(
        self,
        seed: int = 0,
        evaluator: Optional[Callable[[tc.GameState, tc.Landing], float]] = None,
        top_k: int = 5,
    ):
        random.seed(seed)

        self.state = tc.GameState(seed)
        self.done = False

        self.top_k = int(top_k)
        self._set_evaluator(evaluator)

        self.debug = StepDebug(topk=[])

    def _set_evaluator(self, evaluator):
        if evaluator is None:
            self.evaluator = heuristic_evaluator
            self.evaluator_name = "heuristic"
        else:
            self.evaluator = evaluator
            self.evaluator_name = "cnn"

    def step(self):
        if self.done:
            return

        landings = tc.legal_moves(self.state)
        if not landings:
            self.done = True
            return

        scores = [self.evaluator(self.state, l) for l in landings]

        # best
        best_i = int(np.argmax(scores))
        best_landing = landings[best_i]
        best_score = scores[best_i]

        # top-k
        if self.top_k > 0:
            idx = np.argsort(scores)[::-1][: self.top_k]
            topk = [(float(scores[i]), landings[int(i)]) for i in idx]
        else:
            topk = []

        self.state = tc.apply_move(self.state, best_landing)

        self.debug = StepDebug(best_score=float(best_score), best_landing=best_landing, topk=topk)

        if tc.is_dead_state(self.state):
            self.done = True

    # ---- getters ----
    def is_done(self) -> bool:
        return self.done

    def get_board(self) -> np.ndarray:
        return np.array(self.state.board, dtype=np.int32)

    def get_combo(self) -> int:
        return int(self.state.combo)

    def get_back_to_back(self) -> bool:
        return bool(self.state.back_to_back)

    def get_evaluator_name(self) -> str:
        return self.evaluator_name

    def get_last_eval(self) -> float:
        return float(self.debug.best_score)

    def get_last_landing(self) -> Optional[tc.Landing]:
        return self.debug.best_landing

    def get_topk(self) -> List[Tuple[float, tc.Landing]]:
        return list(self.debug.topk)

    # ---- mode switching ----
    def set_heuristic(self):
        self._set_evaluator(None)

    def set_cnn(self, model):
        self._set_evaluator(CNNEvaluator(model))


# -------------------------
# 2P Adapter（左右表示・同時進行）
# -------------------------

class DuelGameAdapter:
    """
    2P 用：state1/state2 を同時に進める簡易デュエル。
    （現bindingには「お邪魔送受信」の関数がないので、
     ここでは “2盤面を同時に進め、死亡/ジャッジで勝敗” を行う）
    """
    def __init__(
        self,
        seed: int = 0,
        evaluator1: Optional[Callable[[tc.GameState, tc.Landing], float]] = None,
        evaluator2: Optional[Callable[[tc.GameState, tc.Landing], float]] = None,
        top_k: int = 5,
    ):
        random.seed(seed)

        self.state1 = tc.GameState(seed)
        self.state2 = tc.GameState(seed + 1)

        self.top_k = int(top_k)

        self._set_eval(1, evaluator1)
        self._set_eval(2, evaluator2)

        self.debug1 = StepDebug(topk=[])
        self.debug2 = StepDebug(topk=[])


        self.done = False
        self.winner = tc.Winner.Undecided

    def _set_eval(self, which: int, evaluator):
        if evaluator is None:
            ev = heuristic_evaluator
            name = "heuristic"
        else:
            ev = evaluator
            name = "cnn"
        if which == 1:
            self.evaluator1 = ev
            self.evaluator1_name = name
        else:
            self.evaluator2 = ev
            self.evaluator2_name = name

    def step_one(self, state: tc.GameState, evaluator, top_k: int):
        landings = tc.legal_moves(state)
        if not landings:
            return state, StepDebug(topk=[])

        # --- 評価 ---
        scores = [evaluator(state, l) for l in landings]
        best_i = int(np.argmax(scores))
        best_landing = landings[best_i]
        best_score = float(scores[best_i])

        # --- top-k ---
        if top_k > 0:
            idx = np.argsort(scores)[::-1][: top_k]
            topk = [(float(scores[i]), landings[int(i)]) for i in idx]
        else:
            topk = []

        prev_board = [row[:] for row in state.board]

        board_before_clear = [row[:] for row in prev_board]

        try:
            cells = tc.get_shape_cells(
                best_landing.piece,
                int(best_landing.final_rot)
            )
            for dx, dy in cells:
                x = best_landing.final_x + dx
                y = best_landing.final_y + dy
                if 0 <= x < 10 and 0 <= y < 20:
                    board_before_clear[y][x] = 1
        except Exception:
            board_before_clear = None  # 念のため

        # (3) C++ 側で state を進める（消去後）
        new_state = tc.apply_move(state, best_landing)

        # (4) debug に保存
        dbg = StepDebug(
            best_score=best_score,
            best_landing=best_landing,
            topk=topk,
            board_before_clear=board_before_clear,
            lines_cleared=int(best_landing.lines_cleared),
        )

        return new_state, dbg


    def step(self):
        if self.done:
            return

        self.state1, self.debug1 = self.step_one(self.state1, self.evaluator1, self.top_k)
        self.state2, self.debug2 = self.step_one(self.state2, self.evaluator2, self.top_k)

        # 死亡/勝敗判定
        dead1 = tc.is_dead_state(self.state1)
        dead2 = tc.is_dead_state(self.state2)

        # judge_winner は board と piece を要求してるので current piece を渡す
        try:
            self.winner = tc.judge_winner(self.state1.board, self.state1.current,
                                          self.state2.board, self.state2.current)
        except Exception:
            # フォールバック（最低限）
            if dead1 and dead2:
                self.winner = tc.Winner.Draw
            elif dead1:
                self.winner = tc.Winner.Player2
            elif dead2:
                self.winner = tc.Winner.Player1
            else:
                self.winner = tc.Winner.Undecided

        if self.winner != tc.Winner.Undecided or dead1 or dead2:
            self.done = True

    # ---- getters ----
    def is_done(self) -> bool:
        return bool(self.done)

    def get_winner(self):
        return self.winner

    def get_board1(self) -> np.ndarray:
        return np.array(self.state1.board, dtype=np.int32)

    def get_board2(self) -> np.ndarray:
        return np.array(self.state2.board, dtype=np.int32)

    def get_info1(self) -> dict:
        return {
            "mode": self.evaluator1_name,
            "eval": float(self.debug1.best_score),
            "combo": int(self.state1.combo),
            "b2b": bool(self.state1.back_to_back),
        }

    def get_info2(self) -> dict:
        return {
            "mode": self.evaluator2_name,
            "eval": float(self.debug2.best_score),
            "combo": int(self.state2.combo),
            "b2b": bool(self.state2.back_to_back),
        }

    def get_last_landing1(self):
        return self.debug1.best_landing

    def get_last_landing2(self):
        return self.debug2.best_landing

    def get_topk1(self):
        return list(self.debug1.topk)

    def get_topk2(self):
        return list(self.debug2.topk)

    # ---- mode switching ----
    def set_heuristic(self, which: int):
        self._set_eval(which, None)

    def set_cnn(self, which: int, model):
        self._set_eval(which, CNNEvaluator(model))




