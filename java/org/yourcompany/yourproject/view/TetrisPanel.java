package org.yourcompany.yourproject.view;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;

import javax.swing.JPanel;

import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.model.Tetromino;

public class TetrisPanel extends JPanel {
    private final GameLogic gameLogic;
    private static final int HIDDEN_ROW_VISIBLE_HEIGHT = 10;

    public TetrisPanel(GameLogic gameLogic) {
        this.gameLogic = gameLogic;
        // パネルの推奨サイズは「見える」高さで設定
        setPreferredSize(new Dimension(Board.BOARD_WIDTH * 30, Board.VISIBLE_BOARD_HEIGHT * 30 + HIDDEN_ROW_VISIBLE_HEIGHT));
        setBackground(Color.BLACK);
        setFocusable(true);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.translate(0, HIDDEN_ROW_VISIBLE_HEIGHT);

        // 描画順序: グリッド → 設置済みブロック → ゴースト → 操作中ブロック
        drawGrid(g);
        drawBoard(g);

        Tetromino current = gameLogic.getCurrentTetromino();
        if (gameLogic.isGameOver()) {
            // (★) 1. ゲームオーバー時の処理
            Tetromino gameOverPiece = gameLogic.getGameOverTetromino();
            
            if (gameOverPiece != null) {
                // (★) 2.「スポーン失敗」または「ロックアウト」したミノを描画する
                // (★) 隠し行にも描画できるよう、専用メソッドを呼ぶ
                drawGameOverTetromino(g, gameOverPiece);
            }
            
            // (★) 3. "Game Over" の文字を最後に描画
            drawGameOver(g);
        
        }else if (current != null) {
            drawGhostPiece(g, current);
            drawTetromino(g, current);
        }

        g.translate(0, -HIDDEN_ROW_VISIBLE_HEIGHT);
        drawHiddenRow(g, HIDDEN_ROW_VISIBLE_HEIGHT);
        g.setColor(new Color(200, 200, 200, 150));
        g.fillRect(0, HIDDEN_ROW_VISIBLE_HEIGHT - 2, getWidth(), 2);

        drawGarbageBar(g);
    }

    private int getCellWidth() {
        return getWidth() / Board.BOARD_WIDTH;
    }

    private int getCellHeight() {
        return (getHeight()- HIDDEN_ROW_VISIBLE_HEIGHT)/ Board.VISIBLE_BOARD_HEIGHT;
    }

    private void drawGrid(Graphics g) {
        g.setColor(new Color(40, 40, 40));
        int cellWidth = getCellWidth();
        int cellHeight = getCellHeight();
        
        for (int i = 0; i <= Board.BOARD_WIDTH; i++) {
            g.drawLine(i * cellWidth, 0, i * cellWidth, Board.VISIBLE_BOARD_HEIGHT * cellHeight);
        }
        for (int i = 0; i <= Board.VISIBLE_BOARD_HEIGHT; i++) {
            g.drawLine(0, i * cellHeight, getWidth(), i * cellHeight);
        }
    }

    private void drawBoard(Graphics g) {
        Board board = gameLogic.getBoard();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;

        // visibleYは画面上の行 (0~19)
        for (int visibleY = 0; visibleY < Board.VISIBLE_BOARD_HEIGHT; visibleY++) {
            for (int x = 0; x < Board.BOARD_WIDTH; x++) {
                // gridYは内部データ上の行 (20~39)
                int gridY = visibleY + hiddenRows;
                Color color = board.getGridAt(x, gridY);

                if (color != null) {
                    // 描画には画面上の座標(visibleY)を使う
                    drawBlock(g, x * getCellWidth(), visibleY * getCellHeight(), color);
                }
            }
        }
    }

    private void drawTetromino(Graphics g, Tetromino tetromino) {
        int[][] coords = tetromino.getCoords();
        Color color = tetromino.getColor();
        int cellWidth = getCellWidth();
        int cellHeight = getCellHeight();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;

        for (int[] p : coords) {
            // tetYは内部データ上の絶対Y座標
            int tetY = tetromino.getY() + p[1];
            // 画面上の描画Y座標（行数）に変換
            int drawRowY = tetY - hiddenRows;

            // 見える範囲にあるブロックだけ描画
            if (drawRowY >= 0) {
                int drawX = (tetromino.getX() + p[0]) * cellWidth;
                int drawY = drawRowY * cellHeight;
                drawBlock(g, drawX, drawY, color);
            }
        }
    }

    private void drawGhostPiece(Graphics g, Tetromino tetromino) {
        int ghostY = tetromino.getY();
        while (gameLogic.getBoard().isValidPosition(tetromino.getCoords(), tetromino.getX(), ghostY + 1)) {
            ghostY++;
        }

        if (ghostY <= tetromino.getY()) return;

        int[][] coords = tetromino.getCoords();
        Color ghostColor = new Color(128, 128, 128, 100); // 半透明のグレー
        int cellWidth = getCellWidth();
        int cellHeight = getCellHeight();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;
        
        g.setColor(ghostColor);
        for (int[] p : coords) {
            int ghostBlockY = ghostY + p[1];
            int drawRowY = ghostBlockY - hiddenRows;

            if (drawRowY >= 0) {
                int drawX = (tetromino.getX() + p[0]) * cellWidth;
                int drawY = drawRowY * cellHeight;
                g.fillRect(drawX, drawY, cellWidth, cellHeight);
            }
        }
    }
    
    private void drawBlock(Graphics g, int x, int y, Color color) {
        g.setColor(color);
        g.fillRect(x, y, getCellWidth(), getCellHeight());
        g.setColor(color.darker());
        g.drawRect(x, y, getCellWidth() - 1, getCellHeight() - 1);
    }
    
    private void drawGarbageBar(Graphics g) {
        int pendingGarbage = gameLogic.getPendingGarbage();
        if (pendingGarbage <= 0) return;

        g.setColor(new Color(255, 50, 50));
        int barWidth = 15;
        int barHeight = Math.min(pendingGarbage * getCellHeight(), getHeight());
        
        g.fillRect(0, getHeight() - barHeight, barWidth, barHeight);
        
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 12));
        g.drawString(String.valueOf(pendingGarbage), 2, getHeight() - 5);
    }

    private void drawGameOver(Graphics g) {
        String msg = "Game Over";
        Font font = new Font("Helvetica", Font.BOLD, 30);
        FontMetrics metrics = getFontMetrics(font);
        
        g.setColor(new Color(0, 0, 0, 150));
        g.fillRect(0, getHeight() / 2 - 30, getWidth(), 50);

        g.setColor(Color.WHITE);
        g.setFont(font);
        g.drawString(msg, (getWidth() - metrics.stringWidth(msg)) / 2, getHeight() / 2);
    }
    private void drawHiddenRow(Graphics g, int clipHeight) {
        Board board = gameLogic.getBoard();
        int cellWidth = getCellWidth();
        int cellHeight = getCellHeight(); // 1セルの「完全な」高さ (e.g., 30px)
        
        // (★) 隠し行の最下段 (gridY = 19)
        int gridY = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT - 1;
        if (gridY < 0) return; // 隠し行がない場合は何もしない

        // (★) 描画領域を上部の clipHeight (15px) に限定
        g.setClip(0, 0, getWidth(), clipHeight);

        // 1. 設置済みのブロックを描画
        for (int x = 0; x < Board.BOARD_WIDTH; x++) {
            Color color = board.getGridAt(x, gridY);
            if (color != null) {
                // (★) 描画Y座標を「セルの下半分」が見えるように調整
                // (例: cellH=30, clipH=15 -> drawY = -30 + 15 = -15)
                // (これにより、(x, -15) から (x, 15) まで描画される)
                int drawY = -cellHeight + clipHeight;
                drawBlock(g, x * cellWidth, drawY, color);
            }
        }
        
        // 2. 現在操作中のミノも描画 (gridY = 19 にある部分)
        Tetromino current = gameLogic.getCurrentTetromino();
        if (current != null) {
            int[][] coords = current.getCoords();
            Color color = current.getColor();
            for (int[] p : coords) {
                int tetY = current.getY() + p[1];
                if (tetY == gridY) { // (★) ブロックが隠し行 (gridY=19) にあるか
                    int drawX = (current.getX() + p[0]) * cellWidth;
                    int drawY = -cellHeight + clipHeight;
                    drawBlock(g, drawX, drawY, color);
                }
            }
        }
        
        // (★) クリップを元に戻す
        g.setClip(0, 0, getWidth(), getHeight());
    }
    private void drawGameOverTetromino(Graphics g, Tetromino tetromino) {
        int[][] coords = tetromino.getCoords();
        Color color = tetromino.getColor();
        int cellWidth = getCellWidth();
        int cellHeight = getCellHeight();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;

        for (int[] p : coords) {
            int tetY = tetromino.getY() + p[1];
            
            // (★) 内部データ上のY座標 (tetY) から画面上の描画Y座標（行数）に変換
            int drawRowY = tetY - hiddenRows;

            // (★) ゲームオーバー時は「隠し行」(drawRowY < 0) も描画対象とするため、
            // (★) if (drawRowY >= 0) のチェックを「行わない」
            
            int drawX = (tetromino.getX() + p[0]) * cellWidth;
            int drawY = drawRowY * cellHeight;
            
            // (★) スポーン失敗時、盤面にあるブロックに「上書き」する
            drawBlock(g, drawX, drawY, color);
        }
    }
}

