package org.yourcompany.yourproject.player;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.yourcompany.yourproject.config.GameAction;
import org.yourcompany.yourproject.model.GameLogic;

/**
 * CNN (ONNX) モデルで盤面評価と即時報酬の計算を行うAIプレイヤー。
 * Python の agent.py のロジックを移植。
 */
public class AIPlayer implements Player {

    private final ConcurrentLinkedQueue<GameAction> actionQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isThinking = false;
    private final TetrisAIBrain brain;
    private final GameLogic myLogic;
    private final GameLogic opponentLogic;
    private static final double GAME_FPS = 60.0;
    private static final double MS_PER_FRAME = 1000.0 / GAME_FPS;
    private static final double SOFT_DROP_FACTOR = GameLogic.SDF; 
    private static final long FALL_INTERVAL_MS = 500; 
    private static final int FRAMES_PER_CELL_DROP = (int) Math.ceil(((double)FALL_INTERVAL_MS / SOFT_DROP_FACTOR) / MS_PER_FRAME);
    private final int actionDelayFrames;
    private final int offset;
    // --- AIPlayer メインロジック ---

    /**
     * コンストラクタでONNXモデルをロードする
     */
    public AIPlayer(GameLogic myLogic, GameLogic opponentLogic, TetrisAIBrain brain, int actionDelayFrames,int offset) {
        this.myLogic = myLogic;
        this.opponentLogic = opponentLogic;
        this.brain = brain; // (★) 脳を受け取る
        this.actionDelayFrames = Math.max(0, actionDelayFrames);
        this.offset = offset;
    }
    @Override
    public GameAction getAction(GameLogic gameState) {
        GameAction action = actionQueue.poll();
        if (action != null) {
            return action;
        }
        if (!isThinking && actionQueue.isEmpty()) {
            requestBestMove(gameState);
        }
        return GameAction.NONE;
    }

    public void requestBestMove(GameLogic mylogic) {
        // (★) myLogic / opponentLogic はフィールド変数を使う
        if (isThinking || !actionQueue.isEmpty() || myLogic.isGameOver()) {
            return;
        }
        isThinking = true;

        new Thread(() -> {
            try {
                // (★) 1. 「脳」に思考を依頼
                TetrisAIBrain.LandingSpot bestMove = brain.findBestMove(myLogic, opponentLogic);

                if (bestMove != null) {
                    
                    System.out.println("AIPlayer"+this.offset+": Best Move Score = " + bestMove.aiScore);
                    // (★) 3. 「スマート翻訳機」 (user_31 のロジック)
                    Queue<GameAction> executionPlan = new LinkedList<>();
                    if (bestMove.usedHold) {
                        executionPlan.add(GameAction.HOLD);
                        for (int i = 0; i < this.actionDelayFrames; i++) {
                            executionPlan.add(GameAction.NONE);
                        }
                    }
                    
                    boolean pathUsesSoftDrop = false;
                    for (GameAction action : bestMove.path) {
                        if (action == GameAction.SOFT_DROP) {
                            pathUsesSoftDrop = true;
                            break;
                        }
                    }

                    if (pathUsesSoftDrop) {
                        // (A) 複雑なパス (タッキング/SD)
                        for (int i = 0; i < this.actionDelayFrames; i++) {
                            executionPlan.add(GameAction.NONE);
                        }
                        boolean isCurrentlySoftDropping = false;
                        for (GameAction action : bestMove.path) {
                            if (action == GameAction.SOFT_DROP) {
                                if (!isCurrentlySoftDropping) {
                                    executionPlan.add(GameAction.START_SOFT_DROP);
                                    isCurrentlySoftDropping = true;
                                }
                                for (int i = 0; i < FRAMES_PER_CELL_DROP; i++) {
                                    executionPlan.add(GameAction.NONE);
                                }
                            } else {
                                if (isCurrentlySoftDropping) {
                                    executionPlan.add(GameAction.STOP_SOFT_DROP);
                                    isCurrentlySoftDropping = false;
                                }
                                executionPlan.add(action);
                            }
                        }
                        if (isCurrentlySoftDropping) {
                            executionPlan.add(GameAction.STOP_SOFT_DROP);
                        }
                    } else {
                        // (B) 単純なパス (HD)
                        executionPlan.addAll(bestMove.path);
                        for (int i = 0; i < this.actionDelayFrames; i++) {
                            executionPlan.add(GameAction.NONE);
                        }
                        
                    }
                    
                    // (★) 4. 最後に HARD_DROP (user_31 のバグ修正)
                    executionPlan.add(GameAction.HARD_DROP);
                    
                    actionQueue.addAll(executionPlan);
                }
            } catch (Exception e) {
                 e.printStackTrace();
            } finally {
                isThinking = false;
            }
        }).start();
    }

    public void clearActionQueue() { 
        actionQueue.clear();
        this.isThinking = false; 
    }

}