package org.yourcompany.yourproject.player;

import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.player.TetrisAIBrain.LandingSpot;


    public class BeamNode {
    public final Board board;
    public final double cumulativeReward; // 道中の消去得点の合計
    public final LandingSpot firstMove;   // 最初に実行すべき1手目
    public double aiScore = 0;           // CNNによる盤面評価値

    public BeamNode(Board board, double reward, LandingSpot firstMove) {
        this.board = board;
        this.cumulativeReward = reward;
        this.firstMove = firstMove;
    }
    
    public double getTotalScore() {
        return cumulativeReward + aiScore;
    }
}
    

