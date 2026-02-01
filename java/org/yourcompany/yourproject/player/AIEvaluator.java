package org.yourcompany.yourproject.player;

import org.yourcompany.yourproject.model.GameLogic;

/**
 * (★新規クラス)
 * HumanPlayer の盤面を「監視 (Observe)」し、
 * ムーブが完了するたびに TetrisAIBrain に評価させる「裏AI」。
 */
public class AIEvaluator {

    private final TetrisAIBrain brain;
    private final GameLogic myLogic;
    private final GameLogic opponentLogic;
    private volatile boolean isThinking = false;
    private final int offset;

    public AIEvaluator(GameLogic myLogic, GameLogic opponentLogic, TetrisAIBrain brain,int offset) {
        this.myLogic = myLogic;
        this.opponentLogic = opponentLogic;
        this.brain = brain;
        this.offset = offset;

        // (★) 自分の GameLogic の「ムーブ完了」イベントに自分を登録
        myLogic.setOnStateChangedListener(this::onStateChanged);
    }

    /**
     * GameLogic からムーブ完了の通知 (Trigger) を受けて実行される
     */
    private void onStateChanged(GameLogic logic) {
        if (isThinking || logic.isGameOver()) {
            return;
        }
        isThinking = true;

        new Thread(() -> {
            try {
                // (★) 1. 「脳」に思考を依頼 (AIPlayer と同じ)
                TetrisAIBrain.LandingSpot bestMove = brain.findBestMove(myLogic, opponentLogic);

                if (bestMove != null) {
                    // (★) 2. 評価値を GameLogic (自分) にセット
                    myLogic.setAiEvaluationScore(bestMove.aiScore);
                }
                System.out.println("AIEvaluator"+this.offset+ ": Evaluated score = " + 
                ((bestMove != null) ? bestMove.aiScore : "N/A"));
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isThinking = false;
            }
        }).start();
    }
}