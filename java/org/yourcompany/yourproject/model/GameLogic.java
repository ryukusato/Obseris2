package org.yourcompany.yourproject.model;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.yourcompany.yourproject.config.ClearInfo;
import org.yourcompany.yourproject.config.SpinType;
import org.yourcompany.yourproject.controller.VersusManager;

/**
 * ゲームの進行状態とルール全体を管理するモデル。
 * 硬直時間（ARE/Line Clear Delay）を制御するステートマシンを導入。
 */
public class GameLogic {

    // --- 設定可能なパラメータ ---
    public static double SDF = 20.0;
    public static int ARE_FRAMES = 0;
    public static int LINE_CLEAR_DELAY_FRAMES = 0;

    // --- ゲーム進行状態 ---
    private enum GamePhase { PLAYING, CLEAR_ANIMATION, ENTRY_DELAY }
    private GamePhase phase = GamePhase.PLAYING;
    private int delayCounter = 0;

    // --- フィールド宣言 ---
    private Board board;
    private Tetromino currentTetromino;
    private Tetromino holdTetromino;
    private Queue<Shape.Tetrominoes> nextShapesQueue;
    private List<Tetromino> nextQueue;
    private long score;
    private boolean isGameOver;
    private boolean canHold;
    private VersusManager versusManager;
    private int pendingGarbage = 0;
    private int lastKickIndex;
    private boolean lastActionWasRotation;
    private boolean isB2BActive;
    private int comboCount;
    private boolean isLockdownActive;
    private long lockdownStartTime;
    private int lockdownResetCount;
    private static final long LOCKDOWN_DELAY = 500;
    private static final int MAX_LOCKDOWN_RESETS = 15;
    private long lastFallTime;
    private final long fallInterval = 500;
    private ClearInfo lastClearInfo = null;
    private boolean isSoftDropping = false;
    private Tetromino gameOverTetromino = null;
    private volatile double aiEvaluationScore = 0.0;
    private static final int MAX_GARBAGE_PER_ATTACK = 10;

    public GameLogic(){
        reset();
    }

    public void reset() {

        board = new Board(); // (★) final ではないので new できる
        score = 0;
        isGameOver = false;
        holdTetromino = null;
        canHold = true;
        isB2BActive = false;
        comboCount = -1;
        pendingGarbage = 0;

        isLockdownActive = false; 
        lockdownResetCount = 0;
        lockdownStartTime = 0; // (★)
        delayCounter = 0; 
        phase = GamePhase.PLAYING; 
        lastClearInfo = null;
        lastFallTime = 0; // (★)
        gameOverTetromino = null; // (★ user_45 で追加したフィールド)
        aiEvaluationScore = 0.0; // (★ user_54 で追加したフィールド)
        
        nextShapesQueue = new LinkedList<>();
        fillNextShapesQueue();
        fillNextShapesQueue();
        nextQueue = new ArrayList<>(); 
        for (int i = 0; i < 5; i++) {
            nextQueue.add(createNewPieceFromQueue());
        }
        
        // (★) スポーン
        spawnNewTetromino();
    }

    public void setVersusManager(VersusManager manager) {
        this.versusManager = manager;
    }

    public void update() {
        if (isGameOver) return;

        switch (phase) {
            case CLEAR_ANIMATION:
                delayCounter--;
                if (delayCounter <= 0) {
                    board.clearLines();
                    phase = GamePhase.ENTRY_DELAY;
                    delayCounter = ARE_FRAMES;
                }
                break;
            case ENTRY_DELAY:
                delayCounter--;
                if (delayCounter <= 0) {
                    spawnNewTetromino();
                    phase = GamePhase.PLAYING;
                }
                break;
            case PLAYING:
                if (currentTetromino == null) {
                    spawnNewTetromino();
                    if (isGameOver) return;
                }
                updatePlaying();
                break;
        }
    }

    private void updatePlaying() {
        if (isGrounded()) {
            if (!isLockdownActive) {
                isLockdownActive = true;
                lockdownStartTime = System.currentTimeMillis();
            } else if (System.currentTimeMillis() - lockdownStartTime > LOCKDOWN_DELAY) {
                placeAndStartDelay();
            }
        } else {
            deactivateLockdown();
            long currentTime = System.currentTimeMillis();
            final long currentFallInterval;
            if (isSoftDropping) {
                // ソフトドロップが有効なら、SDFに基づいた間隔（例: 500ms / 20.0 = 25ms）
                currentFallInterval = (long) (this.fallInterval / SDF);
            } else {
                // 通常時は、設定された間隔（例: 500ms）
                currentFallInterval = this.fallInterval;
            }
            if (currentTime - lastFallTime > currentFallInterval) {
                moveDown();
                
                // 3. ソフトドロップ中に落下したらスコア加算 (元の softDrop() から移動)
                if (isSoftDropping) {
                    score += 1;
                }
            }
        }
    }

    private void placeAndStartDelay() {
        if (currentTetromino == null) return;
        lastClearInfo = null;
        

        if (isLockedOut(currentTetromino)) {
            setGameOver(currentTetromino);
            board.placeTetromino(currentTetromino); 
            currentTetromino = null;
            return;
        }
        board.placeTetromino(currentTetromino);

        int linesToClearCount = countFullLines();
        SpinType spinType = getSpinType();
        boolean isDifficultClear = (spinType != SpinType.NONE) || linesToClearCount == 4;
        boolean b2bBonusApplied = this.isB2BActive && isDifficultClear;
        boolean isPerfectClear = linesToClearCount > 0 && board.isBoardEmpty();
        int attackPower = 0;

        currentTetromino = null;


        if (linesToClearCount > 0) {
            comboCount++;
            attackPower = calculateAttack(linesToClearCount, spinType, b2bBonusApplied, comboCount);
            long baseScore = calculateScore(linesToClearCount, spinType);
            if (b2bBonusApplied) baseScore *= 1.5;
            score += baseScore + (comboCount > 0 ? 50L * comboCount : 0);
            this.isB2BActive = isDifficultClear;

            String clearType = createClearTypeText(linesToClearCount, spinType);
            this.lastClearInfo = new ClearInfo(clearType, linesToClearCount, spinType, b2bBonusApplied, comboCount, isPerfectClear);

            if (versusManager != null && attackPower > 0) {
                int remainingAttack = versusManager.offsetGarbage(this, attackPower);
                if (remainingAttack > 0) versusManager.sendAttack(this, remainingAttack);
            }
            if (isPerfectClear) {
                score += 3000;
                if (versusManager != null) versusManager.sendAttack(this, 10);
            }
            
            phase = GamePhase.CLEAR_ANIMATION;
            delayCounter = LINE_CLEAR_DELAY_FRAMES;
        } else {
            comboCount = -1;
            phase = GamePhase.ENTRY_DELAY;
            delayCounter = ARE_FRAMES;
        }
        if (applyGarbage()) {
            setGameOver(null); // お邪魔でゲームオーバー
            return;
        }

        
    }

    private int countFullLines() {
        int count = 0;
        for (int y = 0; y < Board.TOTAL_BOARD_HEIGHT; y++) {
            boolean lineIsFull = true;
            for (int x = 0; x < Board.BOARD_WIDTH; x++) {
                if (board.getGridAt(x, y) == null) {
                    lineIsFull = false;
                    break;
                }
            }
            if (lineIsFull) count++;
        }
        return count;
    }

    private String createClearTypeText(int linesCleared, SpinType spinType) {
        String clearType = "";
        if (spinType == SpinType.T_SPIN) clearType = "T-SPIN ";
        else if (spinType == SpinType.T_SPIN_MINI) clearType = "T-SPIN MINI ";
        
        switch (linesCleared) {
            case 1: clearType += "SINGLE"; break;
            case 2: clearType += "DOUBLE"; break;
            case 3: clearType += "TRIPLE"; break;
            case 4: clearType = "QUAD"; break;
        }
        return clearType;
    }

    private Consumer<GameLogic> onStateChangedListener;
    
    // (★) リスナーを登録するメソッド
    public void setOnStateChangedListener(Consumer<GameLogic> listener) {
        this.onStateChangedListener = listener;
    }

    private void spawnNewTetromino() {
        if (onStateChangedListener != null) {
            onStateChangedListener.accept(this);
        }
        currentTetromino = nextQueue.remove(0);
        nextQueue.add(createNewPieceFromQueue());
        currentTetromino.resetPositionAndState();
        canHold = true;
        resetLockdownState();
        lastFallTime = System.currentTimeMillis();
        lastActionWasRotation = false;
        if (!board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY())) {
            currentTetromino.moveBy(0, -1);
            if (!board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY())) {
                setGameOver(currentTetromino);
            }
        }
    }

    private boolean applyGarbage() {
        if (this.pendingGarbage <= 0) return false;
        int lineCount = Math.min(this.pendingGarbage, MAX_GARBAGE_PER_ATTACK);
        this.pendingGarbage -= lineCount;
        if (board.addGarbageLines(lineCount)) {
            setGameOver();
            return true;
        }
        return false;
    }

    private void moveDown() {
        if (isGameOver || currentTetromino == null) return;
        if (board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY() + 1)) {
            currentTetromino.moveDown();
            lastFallTime = System.currentTimeMillis();
            lastActionWasRotation = false;
            this.lockdownResetCount = 0;
        }
    }

    public void rotateRight() { handleRotation(true); }
    public void rotateLeft() { handleRotation(false); }
    public void moveLeft() { handleMove(-1, 0); }
    public void moveRight() { handleMove(1, 0); }

    public void setSoftDropState(boolean active) {
        // ゲームプレイ中でなければ状態を変更しない
        if (phase != GamePhase.PLAYING || currentTetromino == null) {
            this.isSoftDropping = false;
            return;
        }

        this.isSoftDropping = active;
        
        // (★ オプション: 操作感向上のため)
        // ソフトドロップが「オン」になった瞬間に、
        // 即座に1マス落下を試みると、プレイヤーの操作感が向上します。
        if (active) {
            long softDropInterval = (long) (this.fallInterval / SDF);
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastFallTime >= softDropInterval) {
                moveDown();
                score += 1; // 即時落下させた分のスコア
            }
        }
    }

    public void hardDrop() {
        if (isGameOver || currentTetromino == null) return;
        int cellsDropped = 0;
        while (board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY() + 1)) {
            currentTetromino.moveDown();
            cellsDropped++;
        }
        this.lockdownResetCount = 0;
        score += cellsDropped * 2L;
        placeAndStartDelay();
    }

    public void hold() {
        if (isGameOver || currentTetromino == null || !canHold || phase != GamePhase.PLAYING) return;
        Tetromino temp = currentTetromino;
        if (holdTetromino == null) {
            holdTetromino = temp;
            phase = GamePhase.ENTRY_DELAY;
            delayCounter = ARE_FRAMES;
            currentTetromino = null;
        } else {
            currentTetromino = holdTetromino;
            holdTetromino = temp;
            currentTetromino.resetPositionAndState();
            if (!board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY())) {
                setGameOver();
            }
        }
        holdTetromino.resetPositionAndState();
        canHold = false;
        resetLockdownState();
    }
    
    private void handleRotation(boolean clockwise) {
        if (isGameOver || currentTetromino == null) return;
        RotationSystem.RotationResult result = RotationSystem.tryRotate(currentTetromino, board, clockwise);
        if (result.success()) {
            lastActionWasRotation = true;
            lastKickIndex = result.kickIndex();
            tryResetLockdownTimer();
        }
    }

    private void handleMove(int dx, int dy) {
        if (isGameOver || currentTetromino == null) return;
        if (board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX() + dx, currentTetromino.getY() + dy)) {
            currentTetromino.moveBy(dx, dy);
            lastActionWasRotation = false;
            tryResetLockdownTimer();
        }
    }
    
    private void setGameOver() {
        setGameOver(null);
    }
    private void setGameOver(Tetromino piece) {
        isGameOver = true;
        
        if (piece != null) {
            this.gameOverTetromino = piece;
        }
        
        // (★) ゲームオーバー時は操作ミノを null にする (スポーン失敗時を除く)
        if (this.gameOverTetromino != currentTetromino) {
             currentTetromino = null;
        }
        if (onStateChangedListener != null) {
            onStateChangedListener.accept(this);
        }
    }
    public Tetromino getGameOverTetromino() {
        return this.gameOverTetromino;
    }


    private void tryResetLockdownTimer() {
        if (isGrounded()) {
            if (lockdownResetCount < MAX_LOCKDOWN_RESETS) {
                lockdownStartTime = System.currentTimeMillis();
                lockdownResetCount++;
            } else {
                placeAndStartDelay();
            }
        }
    }

    public void receiveGarbage(int lines) { 
        this.pendingGarbage += lines; 
        if (onStateChangedListener != null) {
            onStateChangedListener.accept(this);
        }
    
    }
    
    public int offsetGarbage(int attackPower) {
        this.pendingGarbage -= attackPower;
        int overflow = -this.pendingGarbage;
        if (this.pendingGarbage < 0) this.pendingGarbage = 0;
        return overflow > 0 ? overflow : 0;
    }
    
    private int calculateAttack(int linesCleared, SpinType spinType, boolean isB2B, int combo) {
        int attack = 0;
        if (spinType == SpinType.T_SPIN) attack = linesCleared * 2;
        else if (spinType == SpinType.T_SPIN_MINI) attack = (linesCleared == 2) ? 2 : 1;
        else {
            switch (linesCleared) {
                case 1: attack = 0; break;
                case 2: attack = 1; break;
                case 3: attack = 2; break;
                case 4: attack = 4; break;
            }
        }
        if (isB2B && attack > 0) attack += 1;
        if (combo >= 1) {
            int[] comboBonus = {0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 4, 5};
            attack += (combo < comboBonus.length) ? comboBonus[combo] : comboBonus[comboBonus.length - 1];
        }
        return attack;
    }
    
    private SpinType getSpinType() {
        // Tミノでない、または最後の操作が回転でなければ T-Spin ではない
        if (currentTetromino == null || currentTetromino.getPieceShape() != Shape.Tetrominoes.TShape || !lastActionWasRotation) {
            return SpinType.NONE;
        }

        int x = currentTetromino.getX();
        int y = currentTetromino.getY();
        int rotation = currentTetromino.getRotationState(); // Tミノの現在の向き (0, 1, 2, 3)

        // --- 1. 4隅のチェック ---
        // Tミノの中心 (x, y) から見た4隅の座標
        // (左上, 右上, 左下, 右下)
        int[][] corners = {
            {y - 1, x - 1}, {y - 1, x + 1},
            {y + 1, x - 1}, {y + 1, x + 1}
        };

        int occupiedCorners = 0;
        for (int[] corner : corners) {
            // isOccupied は (x, y) の順で座標を渡す
            if (isOccupied(corner[1], corner[0])) {
                occupiedCorners++;
            }
        }

        // 3隅以上埋まっていなければ T-Spin ではない
        if (occupiedCorners < 3) {
            return SpinType.NONE;
        }

        // --- 2. 前面2隅のチェック (C++のロジックを移植) ---
        // Tミノの向きに応じて「前面」とされる2隅を定義する
        int[][] frontCorners;
        switch (rotation) {
            case 0: // 初期状態 (上向き)
                frontCorners = new int[][]{{y - 1, x - 1}, {y - 1, x + 1}}; // 上の2隅
                break;
            case 1: // 時計回り1回 (右向き)
                frontCorners = new int[][]{{y - 1, x + 1}, {y + 1, x + 1}}; // 右の2隅
                break;
            case 2: // 時計回り2回 (下向き)
                frontCorners = new int[][]{{y + 1, x - 1}, {y + 1, x + 1}}; // 下の2隅
                break;
            case 3: // 時計回り3回 (左向き)
                frontCorners = new int[][]{{y - 1, x - 1}, {y + 1, x - 1}}; // 左の2隅
                break;
            default:
                return SpinType.NONE; // 該当なし
        }

        int occupiedFrontCorners = 0;
        for (int[] corner : frontCorners) {
            if (isOccupied(corner[1], corner[0])) {
                occupiedFrontCorners++;
            }
        }

        // --- 3. 判定 ---
        // 3隅以上が埋まっており、かつ、前面2隅が埋まっている場合 -> T-SPIN (Full)
        if (occupiedFrontCorners == 2) {
            return SpinType.T_SPIN;
        }
        // 3隅以上が埋まっているが、前面2隅は埋まっていない場合 -> T-SPIN MINI
        else {
            return SpinType.T_SPIN_MINI;
        }
    }

    private long calculateScore(int linesCleared, SpinType spinType) {
        return switch (spinType) {
            case T_SPIN -> switch (linesCleared) {
                case 1 -> 800; case 2 -> 1200; case 3 -> 1600; default -> 400;
            };
            case T_SPIN_MINI -> switch (linesCleared) {
                case 1 -> 200; case 2 -> 400; default -> 100;
            };
            case NONE -> switch (linesCleared) {
                case 1 -> 100; case 2 -> 300; case 3 -> 500; case 4 -> 800; default -> 0;
            };
        };
    }

    private void resetLockdownState() {
        isLockdownActive = false;
        lockdownResetCount = 0;
    }

    private void deactivateLockdown() { isLockdownActive = false; }

    private void fillNextShapesQueue() {
        List<Shape.Tetrominoes> shapes = Arrays.stream(Shape.Tetrominoes.values()).filter(s -> s != Shape.Tetrominoes.NoShape).collect(Collectors.toList());
        Collections.shuffle(shapes);
        nextShapesQueue.addAll(shapes);
    }

    private Tetromino createNewPieceFromQueue() {
        if (nextShapesQueue.size() <= 7) fillNextShapesQueue();
        return new Tetromino(nextShapesQueue.poll());
    }

    private boolean isGrounded() {
        if (currentTetromino == null) return false;
        return !board.isValidPosition(currentTetromino.getCoords(), currentTetromino.getX(), currentTetromino.getY() + 1);
    }

    private boolean isOccupied(int x, int y) {
        return x < 0 || x >= Board.BOARD_WIDTH || y < 0 || y >= Board.TOTAL_BOARD_HEIGHT || board.getGridAt(x, y) != null;
    }

    private boolean isLockedOut(Tetromino tetromino) {
        int[][] coords = tetromino.getCoords();
        int pieceY = tetromino.getY();
        int hiddenRows = Board.TOTAL_BOARD_HEIGHT - Board.VISIBLE_BOARD_HEIGHT;
        for (int[] p : coords) {
            if (pieceY + p[1] >= hiddenRows) return false;
        }
        return true;
    }
    public Tetromino getSixthPiece() {
        if (this.nextShapesQueue != null && !this.nextShapesQueue.isEmpty()) {
            // nextShapesQueue の先頭（＝6個目のミノ）を覗き見 (peek) し、
            // それを Tetromino オブジェクトとして AI に渡す
            Shape.Tetrominoes shape = this.nextShapesQueue.peek();
            if (shape != null) {
                return new Tetromino(shape);
            }
        }
        return null;
    }

    public void setAiEvaluationScore(double rawScore) {
        this.aiEvaluationScore = rawScore; // 生の値を保存 (デバッグ用)

        // 1. ゲームオーバー (ペナルティ値) の場合
        if (rawScore < -900000000.0) {
            this.aiEvaluationScore = -999999.0; // 敗北
        } 
        // 2. 通常の評価値の場合
        else {
            this.aiEvaluationScore = rawScore;
        }
    }
    public double getAiEvaluationScore() {
        return this.aiEvaluationScore;
    }
    
    public Board getBoard() { return board; }
    public Tetromino getCurrentTetromino() { return currentTetromino; }
    public Tetromino getHoldTetromino() { return holdTetromino; }
    public long getScore() { return score; }
    public List<Tetromino> getNextQueue() { return nextQueue; }
    public boolean isGameOver() { return isGameOver; }
    public int getPendingGarbage() { return pendingGarbage; }
    public ClearInfo getLastClearInfo() { return lastClearInfo; }
    public int getComboCount(){return comboCount;}
    public boolean isB2BActive(){return isB2BActive;}
    public boolean getCanHold() { return this.canHold; }
}