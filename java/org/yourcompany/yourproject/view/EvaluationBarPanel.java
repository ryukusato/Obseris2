package org.yourcompany.yourproject.view;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JPanel;

import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.model.GameLogic;

/**
 * AIの評価値 (-100 ～ +100) を
 * 左右に動く「横型バー」として描画するパネル。
 */
public class EvaluationBarPanel extends JPanel {

    private GameLogic myLogic;
    private GameLogic opponentLogic;
    private static final double EVAL_SCALE_FACTOR = 200.0;
    private static final double BASELINE_COST = -120.0;
    private static final double DANGER_COST = -250.0;
    private static final double MAX_SENSITIVITY_MULTIPLIER = 8.0;


    public EvaluationBarPanel(GameLogic myLogic, GameLogic opponentLogic) {
        this.myLogic = myLogic;
        this.opponentLogic = opponentLogic;
        setPreferredSize(new Dimension(Board.BOARD_WIDTH * 30*2+30, 50)); // (幅はTetrisPanelと合わせ、高さは25px)
        setBackground(Color.BLACK);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        // (★) 描画を滑らかにする (アンチエイリアス)
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        double myRawScore = myLogic.getAiEvaluationScore();
        double oppRawScore = opponentLogic.getAiEvaluationScore(); // -100 ～ +100
        double averageCost = (myRawScore + oppRawScore) / 2.0;
        double scoreDiff = myRawScore - oppRawScore;
        double progressRatio = (averageCost - BASELINE_COST) / (DANGER_COST - BASELINE_COST);
        progressRatio = Math.max(0.0, Math.min(1.0, progressRatio));
        double sensitivityRatio = Math.pow(progressRatio, 4);
        double sensitivity = 1.0 + (MAX_SENSITIVITY_MULTIPLIER - 1.0) * sensitivityRatio;
        double scaledDiff = scoreDiff * sensitivity;
        double normScore = 100.0 * Math.tanh(scaledDiff / EVAL_SCALE_FACTOR);
        // --- バーのジオメトリ ---
        int panelWidth = getWidth();
        int panelHeight = getHeight();
        
        // (★) 左右の端からのマージン
        int marginX = 10;
        int barY = 5;
        int barHeight = panelHeight - 10;
        
        // (★) ゼロ（互角）地点のX座標 (中央)
        int totalBarWidth = panelWidth - (marginX * 2);
        int startX = marginX;
        
        // (★) バーが動く最大の幅 (中央から端まで)
        double p1Ratio = ((normScore / 100.0) + 1.0) / 2.0;
        
        // (★) スコア（-100～+100）をバーの幅（ピクセル）に変換
        int p1BarWidth = (int) (totalBarWidth * p1Ratio);
        int p2BarWidth = totalBarWidth - p1BarWidth;

        // --- 描画 ---
        
        // 1. バーの背景 (ニュートラルグレー)
        g2d.setColor(Color.WHITE);
        g2d.fillRoundRect(startX, barY, p1BarWidth, barHeight, 5, 5);
        g2d.setColor(Color.RED);
        g2d.fillRoundRect(startX + p1BarWidth, barY, p2BarWidth, barHeight, 5, 5);

    
        
        // 4. 数値の表示 (バーの中央)
        String scoreText = String.format("%.1f", normScore);
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Arial", Font.BOLD, 20));
        int textWidth = g2d.getFontMetrics().stringWidth(scoreText);
        int textX = (panelWidth / 2) - (textWidth / 2);
        g2d.drawString(scoreText, textX, panelHeight / 2 + 5);
    }

    public void setGameLogics(GameLogic myLogic, GameLogic opponentLogic) {
        this.myLogic = myLogic;
        this.opponentLogic = opponentLogic;
    }

}