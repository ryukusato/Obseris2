package org.yourcompany.yourproject.view;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JFrame; // (★)
import javax.swing.JPanel; // (★)

import org.yourcompany.yourproject.config.PlayerType;
import org.yourcompany.yourproject.controller.GameController;
import org.yourcompany.yourproject.controller.VersusManager;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.player.AIEvaluator;
import org.yourcompany.yourproject.player.AIPlayer;
import org.yourcompany.yourproject.player.HumanPlayer;
import org.yourcompany.yourproject.player.Player;
import org.yourcompany.yourproject.player.TetrisAIBrain;

public class VersusGameFrame extends JFrame {

    private final PlayerUIPanel player1UI;
    private final PlayerUIPanel player2UI;
    private final VersusManager versusManager; // (★) Manager を保持
    private final EvaluationBarPanel evaluationBar;
    private static final String EVALUATION_BRAIN_MODEL_NAME = "tetris_value_final.onnx";
    // (★) AIの脳をモデル名ごとにキャッシュする (ONNXロードを1回にする)
    private final Map<String, TetrisAIBrain> brainCache = new HashMap<>();

    public VersusGameFrame(PlayerType player1Type, PlayerType player2Type,String player1modelResourceName,
    String player2modelResourceName,int p1SpeedPercent, int p2SpeedPercent) {
        
        // (★) 1. ロジックを先に作成
        GameLogic logic1 = new GameLogic();
        GameLogic logic2 = new GameLogic();
        
        // (★) 2. 必要な「脳」をロード (またはキャッシュから取得)
        TetrisAIBrain brain1 =(player1Type == PlayerType.AI) ? 
        getBrain(player1modelResourceName) : null;
        TetrisAIBrain brain2 = (player2Type == PlayerType.AI) ? 
        getBrain(player2modelResourceName) : null;
        TetrisAIBrain evaluationBrain = getBrain(EVALUATION_BRAIN_MODEL_NAME);
        
        // (★) 3. モードに応じて Player と Evaluator を作成
        Player player1 = createPlayer(player1Type, logic1, logic2, brain1,p1SpeedPercent,1);
        Player player2 = createPlayer(player2Type, logic2, logic1, brain2,p2SpeedPercent,2);
        
        // (★) 4. HumanPlayer の場合は「裏AI」を起動
        if (evaluationBrain != null) {
            new AIEvaluator(logic1, logic2, evaluationBrain,1);
            new AIEvaluator(logic2, logic1, evaluationBrain,2);
        }
        
        // (★) 5. VersusManager を作成 (修正版コンストラクタ)
        this.versusManager = new VersusManager(logic1, logic2, player1, player2);
        
        // (★) 6. UIコンポーネントを生成 (PlayerUIPanel が Bar を内蔵)
        this.player1UI = new PlayerUIPanel(logic1);
        this.player2UI = new PlayerUIPanel(logic2);
        
        // (★) 7. Controllerを生成
        GameController gameController = new GameController(versusManager, this, player1, player2);

        // --- ウィンドウのセットアップ ---
        setTitle("Tetris Versus [" + player1Type + " vs " + player2Type + "]");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        mainPanel.add(player1UI);
        mainPanel.add(player2UI);
        add(mainPanel, BorderLayout.CENTER);

        this.evaluationBar = new EvaluationBarPanel(logic1, logic2);
        add(evaluationBar, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(null);
        
        setVisible(true);
        gameController.startGame();
    }

    /**
     * (★) 必要なAIの「脳」を取得またはロードする
     */
    private TetrisAIBrain getBrain(String modelName) {
        // AIでもHumanでもない (またはモデル不要) なら null
        if (modelName == null || modelName.isEmpty()) {
            // (★) HumanPlayer が P2 でモデル名が指定されなかった場合など
            // (★) AIPlayer (P1) と同じ脳を使うか、デフォルト脳を使うか
            // (★) ここでは null を返すが、P1の脳 (brain1) を返す設計もアリ
            return null; 
        }

        // (★) キャッシュにあればそれを返す
        if (brainCache.containsKey(modelName)) {
            return brainCache.get(modelName);
        }
        
        // (★) なければロードしてキャッシュに保存
        System.out.println("Loading AI Brain: " + modelName);
        TetrisAIBrain brain = new TetrisAIBrain(modelName);
        brainCache.put(modelName, brain);
        return brain;
    }

    /**
     * (★) PlayerTypeに応じてPlayerインスタンスを返す (修正版)
     */
    private Player createPlayer(PlayerType type, GameLogic myLogic, GameLogic opponentLogic, TetrisAIBrain brain,int speedPercent,int offset) {
        if (type == PlayerType.HUMAN) {
            return new HumanPlayer();
        } else {
            // (★) AIPlayer は Logic と Brain を受け取る
            int delay = (int)Math.max(0, (100.0 / (double)speedPercent) - 1.0);
            return new AIPlayer(myLogic, opponentLogic, brain, 2 *delay,offset);
        }
    }

    /**
     * (★) resetUI のロジックを変更
     * resetGame() は VersusManager に移動
     */
    public void resetUI() {
        // (★) Manager にリセットを指示 (Logic のリセット)
        versusManager.resetGame();
        
        // (★) UI にもリセットを指示 (View のリセット)
        player1UI.resetGameLogic(versusManager.getPlayer1Logic());
        player2UI.resetGameLogic(versusManager.getPlayer2Logic());
    }
}