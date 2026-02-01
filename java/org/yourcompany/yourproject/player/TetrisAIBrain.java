package org.yourcompany.yourproject.player;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.yourcompany.yourproject.config.GameAction;
import org.yourcompany.yourproject.config.SpinType;
import org.yourcompany.yourproject.model.Board;
import org.yourcompany.yourproject.model.GameLogic;
import org.yourcompany.yourproject.model.RotationSystem;
import org.yourcompany.yourproject.model.Shape;
import org.yourcompany.yourproject.model.Tetromino;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtLoggingLevel;
import ai.onnxruntime.OrtSession;

public class TetrisAIBrain {
    
    // --- 定数 ---
    private static final double AI_MEAN = -5432.1; 
    private static final double AI_STD  = 1234.5;
    private static final int FEATURE_INPUT_SIZE = 72;
    private static final int NUM_SHAPE_TYPES = 7;
    
    private final boolean isSimpleModel;
    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputNameBoard;
    private final String inputNameFeature;

    private static final Map<Shape.Tetrominoes, Integer> SHAPE_TO_INDEX = Map.of(
        Shape.Tetrominoes.TShape, 0, Shape.Tetrominoes.ZShape, 1,
        Shape.Tetrominoes.SShape, 2, Shape.Tetrominoes.LineShape, 3,
        Shape.Tetrominoes.SquareShape, 4, Shape.Tetrominoes.LShape, 5,
        Shape.Tetrominoes.MirroredLShape, 6, Shape.Tetrominoes.NoShape, -1
    );

    // --- 内部構造 ---
    private record SearchState(int x, int y, int rot, boolean isGrounded) {}

    public static class BeamNode {
        public final Board board;
        public final double cumulativeReward;
        public final LandingSpot firstMove;
        public double aiScore = 0;

        public BeamNode(Board board, double reward, LandingSpot firstMove) {
            this.board = board;
            this.cumulativeReward = reward;
            this.firstMove = firstMove;
        }
        public double getTotalScore() { return cumulativeReward + aiScore; }
    }

    public static class LandingSpot {
        public final List<GameAction> path;
        public final Board futureBoard;
        public final int linesCleared;
        public final long scoreDelta;
        public final boolean usedHold;
        public final int finalX, finalY, finalRot;
        public double aiScore = Double.NEGATIVE_INFINITY;
        public final List<Tetromino> futureNextQueue;

        public LandingSpot(List<GameAction> path, Board futureBoard, int linesCleared,
                           long scoreDelta, boolean usedHold, int finalX, int finalY, int finalRot,
                           List<Tetromino> futureNextQueue) {
            this.path = path;
            this.futureBoard = futureBoard;
            this.linesCleared = linesCleared;
            this.scoreDelta = scoreDelta;
            this.usedHold = usedHold;
            this.finalX = finalX;
            this.finalY = finalY;
            this.finalRot = finalRot;
            this.futureNextQueue = futureNextQueue;
        }
    }

    // --- コンストラクタ ---
    public TetrisAIBrain(String modelResourceName) {
        String dataResourceName = modelResourceName + ".data";
        try {
            this.env = OrtEnvironment.getEnvironment(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR);
            File tempDir = Files.createTempDirectory("onnx_model_").toFile();
            tempDir.deleteOnExit();
            File tempOnnxFile = new File(tempDir, modelResourceName);
            File tempDataFile = new File(tempDir, dataResourceName);
            copyResourceToFile(modelResourceName, tempOnnxFile);
            copyResourceToFile(dataResourceName, tempDataFile);

            this.session = env.createSession(tempOnnxFile.getAbsolutePath(), new OrtSession.SessionOptions());
            Set<String> inputNames = this.session.getInputInfo().keySet();
            
            if (inputNames.contains("board_input") || (inputNames.contains("board_tensor_input") && !inputNames.contains("feature_tensor_input"))) {
                this.inputNameBoard = inputNames.contains("board_input") ? "board_input" : "board_tensor_input";
                this.inputNameFeature = null;
                this.isSimpleModel = true;
            } else {
                this.inputNameBoard = "board_tensor_input";
                this.inputNameFeature = "feature_tensor_input";
                this.isSimpleModel = false;
            }
            System.out.println("AI Loaded: " + modelResourceName + " | Mode: " + (isSimpleModel ? "Simple" : "Multi"));
        } catch (Exception e) {
            throw new RuntimeException("Model Load Error", e);
        }
    }

    // --- メインロジック: ビームサーチ ---
    public LandingSpot findBestMove(GameLogic myLogic, GameLogic opponentLogic) {
        final int BEAM_WIDTH = 15;
        final int SEARCH_DEPTH = 5;
        List<BeamNode> beam = new ArrayList<>();
        List<LandingSpot> firstLayer = new ArrayList<>();

        // 1手目の生成
        generateMovesForPieceInternal(myLogic.getBoard(), myLogic.getCurrentTetromino().getPieceShape(), false, firstLayer);
        if (myLogic.getCanHold()) {
            generateMovesForPieceInternal(myLogic.getBoard(), getHoldShape(myLogic), true, firstLayer);
        }

        if (firstLayer.isEmpty()) return null;

        // 1手目の評価
        try { evaluateLandingSpots(firstLayer, myLogic, opponentLogic); } catch (OrtException e) { return null; }
        
        firstLayer.sort((a, b) -> Double.compare(b.aiScore, a.aiScore));
        for (int i = 0; i < Math.min(BEAM_WIDTH, firstLayer.size()); i++) {
            LandingSpot m = firstLayer.get(i);
            beam.add(new BeamNode(m.futureBoard, (double)m.scoreDelta, m));
        }

        // 2手目以降の先読み
        for (int d = 1; d < SEARCH_DEPTH; d++) {
            if (d - 1 >= myLogic.getNextQueue().size()) break;
            Shape.Tetrominoes nextShape = myLogic.getNextQueue().get(d - 1).getPieceShape();
            List<BeamNode> nextCandidates = new ArrayList<>();

            for (BeamNode node : beam) {
                List<LandingSpot> children = new ArrayList<>();
                generateMovesForPieceInternal(node.board, nextShape, false, children);
                for (LandingSpot cm : children) {
                    nextCandidates.add(new BeamNode(cm.futureBoard, node.cumulativeReward + cm.scoreDelta, node.firstMove));
                }
            }

            if (nextCandidates.isEmpty()) break;

            try { evaluateBeamNodes(nextCandidates, myLogic, opponentLogic); } catch (OrtException e) { break; }
            nextCandidates.sort((a, b) -> Double.compare(b.getTotalScore(), a.getTotalScore()));
            
            beam.clear();
            for (int i = 0; i < Math.min(BEAM_WIDTH, nextCandidates.size()); i++) beam.add(nextCandidates.get(i));
        }

        return beam.isEmpty() ? null : beam.get(0).firstMove;
    }

    // --- 探索・評価用メソッド ---
    private void generateMovesForPieceInternal(Board board, Shape.Tetrominoes shape, boolean isHold, List<LandingSpot> results) {
        if (shape == null || shape == Shape.Tetrominoes.NoShape) return;
    
        Set<SearchState> visited = new HashSet<>();
        Queue<SearchState> queue = new LinkedList<>();
        Map<SearchState, SearchState> parentMap = new HashMap<>(); // 親ノードを記録
        Map<SearchState, GameAction> actionMap = new HashMap<>();   // どのアクションで来たか記録
        Set<String> foundLandings = new HashSet<>();
    
        Tetromino piece = new Tetromino(shape);
        // 開始位置の設定
        int startX = 4;
        int startY = 2; 
        SearchState startState = new SearchState(startX, startY, 0, false);
        
        queue.add(startState);
        visited.add(startState);
        parentMap.put(startState, null);
        actionMap.put(startState, GameAction.NONE);
    
        while (!queue.isEmpty()) {
            SearchState curr = queue.poll();
            int[][] coords = piece.getCoordsForRotation(curr.rot());
    
            // --- 着地点の記録 (1手目のために path を復元) ---
            if (curr.isGrounded()) {
                String key = curr.x() + "," + curr.y() + "," + curr.rot();
                if (foundLandings.add(key)) {
                    List<GameAction> path = reconstructPath(parentMap, curr, actionMap);
                    results.add(calculateLandingResultFromBoard(board, shape, curr.x(), curr.y(), curr.rot(), isHold, path));
                }
            } else {
                int finalY = dropPiece(board, coords, curr.x(), curr.y());
                String key = curr.x() + "," + finalY + "," + curr.rot();
                if (foundLandings.add(key)) {
                    List<GameAction> path = reconstructPath(parentMap, curr, actionMap);
                    results.add(calculateLandingResultFromBoard(board, shape, curr.x(), finalY, curr.rot(), isHold, path));
                }
            }
    
            // --- 次の状態への遷移 ---
            for (GameAction action : new GameAction[]{GameAction.MOVE_LEFT, GameAction.MOVE_RIGHT, GameAction.SOFT_DROP, GameAction.ROTATE_LEFT, GameAction.ROTATE_RIGHT}) {
                if (curr.isGrounded() && action == GameAction.SOFT_DROP) continue;
    
                SearchState next = null;
                if (action == GameAction.MOVE_LEFT && board.isValidPosition(coords, curr.x() - 1, curr.y())) 
                    next = new SearchState(curr.x() - 1, curr.y(), curr.rot(), false);
                else if (action == GameAction.MOVE_RIGHT && board.isValidPosition(coords, curr.x() + 1, curr.y())) 
                    next = new SearchState(curr.x() + 1, curr.y(), curr.rot(), false);
                else if (action == GameAction.SOFT_DROP && board.isValidPosition(coords, curr.x(), curr.y() + 1)) 
                    next = new SearchState(curr.x(), curr.y() + 1, curr.rot(), false);
                else if (action == GameAction.ROTATE_LEFT || action == GameAction.ROTATE_RIGHT) {
                    RotationSystem.RotationResult rr = RotationSystem.simulateRotation(curr.x(), curr.y(), curr.rot(), shape, board, action == GameAction.ROTATE_RIGHT);
                    if (rr.success()) next = new SearchState(rr.newX(), rr.newY(), rr.newRot(), false);
                }
    
                if (next != null) {
                    boolean grounded = !board.isValidPosition(piece.getCoordsForRotation(next.rot()), next.x(), next.y() + 1);
                    SearchState finalNext = new SearchState(next.x(), next.y(), next.rot(), grounded);
                    
                    if (!visited.contains(finalNext)) {
                        visited.add(finalNext);
                        parentMap.put(finalNext, curr); // ここで親を記録
                        actionMap.put(finalNext, action); // ここでアクションを記録
                        queue.add(finalNext);
                    }
                }
            }
        }
    }
    

    private void evaluateBeamNodes(List<BeamNode> nodes, GameLogic myLogic, GameLogic opponentLogic) throws OrtException {
        int batchSize = nodes.size();
        FloatBuffer boardBuf = FloatBuffer.allocate(batchSize * 800);
        FloatBuffer featBuf = isSimpleModel ? null : FloatBuffer.allocate(batchSize * FEATURE_INPUT_SIZE);

        for (int i = 0; i < batchSize; i++) {
            fillBoardBuffer(boardBuf, nodes.get(i).board, i * 800);
            if (featBuf != null) fillQueueBuffer(featBuf, myLogic.getNextQueue(), i * FEATURE_INPUT_SIZE);
        }

        boardBuf.rewind();
        OnnxTensor bTensor = OnnxTensor.createTensor(env, boardBuf, new long[]{batchSize, 2, 40, 10});
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(inputNameBoard, bTensor);
        if (featBuf != null) {
            featBuf.rewind();
            inputs.put(inputNameFeature, OnnxTensor.createTensor(env, featBuf, new long[]{batchSize, FEATURE_INPUT_SIZE}));
        }

        try (OrtSession.Result res = session.run(inputs)) {
            float[][] vals = (float[][]) res.get(0).getValue();
            for (int i = 0; i < batchSize; i++) nodes.get(i).aiScore = (vals[i][0] * AI_STD) + AI_MEAN;
        } finally { bTensor.close(); }
    }

    private void evaluateLandingSpots(List<LandingSpot> moves, GameLogic myLogic, GameLogic opponentLogic) throws OrtException {
        int batchSize = moves.size();
        FloatBuffer boardBuf = FloatBuffer.allocate(batchSize * 800);
        for (int i = 0; i < batchSize; i++) {
            fillBoardBuffer(boardBuf, myLogic.getBoard(), i * 800);
            fillPieceMaskBuffer(boardBuf, (moves.get(i).usedHold ? getHoldShape(myLogic) : myLogic.getCurrentTetromino().getPieceShape()), 
                               moves.get(i).finalX, moves.get(i).finalY, moves.get(i).finalRot, (i * 800) + 400);
        }
        boardBuf.rewind();
        OnnxTensor bTensor = OnnxTensor.createTensor(env, boardBuf, new long[]{batchSize, 2, 40, 10});
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(inputNameBoard, bTensor);
        
        try (OrtSession.Result res = session.run(inputs)) {
            float[][] vals = (float[][]) res.get(0).getValue();
            for (int i = 0; i < batchSize; i++) moves.get(i).aiScore = (vals[i][0] * AI_STD) + AI_MEAN;
        } finally { bTensor.close(); }
    }

    // --- ユーティリティ ---
    private void fillBoardBuffer(FloatBuffer buf, Board board, int offset) {
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 10; x++) {
                buf.put(offset + (y * 10) + x, (board.getGridAt(x, y) != null ? 1.0f : 0.0f));
            }
        }
    }

    private void fillPieceMaskBuffer(FloatBuffer buf, Shape.Tetrominoes shape, int x, int y, int rot, int offset) {
        for (int i = 0; i < 400; i++) buf.put(offset + i, 0.0f);
        Tetromino temp = new Tetromino(shape);
        for (int[] p : temp.getCoordsForRotation(rot)) {
            int cx = x + p[0], cy = y + p[1];
            if (cx >= 0 && cx < 10 && cy >= 0 && cy < 40) buf.put(offset + (cy * 10) + cx, 1.0f);
        }
    }

    private void fillQueueBuffer(FloatBuffer buf, List<Tetromino> queue, int offset) {
        for (int i = 0; i < 35; i++) {
            if (queue != null && i / 7 < queue.size()) {
                int idx = SHAPE_TO_INDEX.getOrDefault(queue.get(i / 7).getPieceShape(), -1);
                buf.put(offset + i, (i % 7 == idx ? 1.0f : 0.0f));
            }
        }
    }

    private LandingSpot calculateLandingResultFromBoard(Board boardBefore, Shape.Tetrominoes shape, int x, int y, int rot, boolean isHold, List<GameAction> path) {
        Board futureBoard = new Board(boardBefore);
        Tetromino piece = new Tetromino(shape);
        piece.setSimulatedState(x, y, rot);
        
        futureBoard.placeTetromino(piece);
        int linesCleared = futureBoard.countFullLines();
        if (linesCleared > 0) futureBoard.clearLines();
    
        long scoreDelta = calculateScore(linesCleared, SpinType.NONE); 
    
        // path が null なら空リストにする（NPE対策）
        List<GameAction> finalPath = (path != null) ? path : new ArrayList<>();
    
        return new LandingSpot(finalPath, futureBoard, linesCleared, scoreDelta, isHold, x, y, rot, null);
    }

    private int dropPiece(Board b, int[][] coords, int x, int y) {
        int targetY = y;
        while (b.isValidPosition(coords, x, targetY + 1)) targetY++;
        return targetY;
    }

    private Shape.Tetrominoes getHoldShape(GameLogic logic) {
        return (logic.getHoldTetromino() == null) ? (logic.getNextQueue().isEmpty() ? Shape.Tetrominoes.NoShape : logic.getNextQueue().get(0).getPieceShape()) : logic.getHoldTetromino().getPieceShape();
    }

    private long calculateScore(int lines, SpinType spin) {
        if (lines == 1) return 100; if (lines == 2) return 300; if (lines == 3) return 500; if (lines == 4) return 800; return 0;
    }

    private void copyResourceToFile(String name, File dest) throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(name)) {
            if (in == null) throw new FileNotFoundException(name);
            Files.copy(in, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private List<GameAction> reconstructPath(Map<SearchState, SearchState> parentMap, SearchState endState, Map<SearchState, GameAction> actionMap) {
        LinkedList<GameAction> path = new LinkedList<>();
        SearchState curr = endState;
        // 開始地点 (parentMap.get(curr) == null) に到達するまで遡る
        while (curr != null && parentMap.get(curr) != null) {
            GameAction action = actionMap.get(curr);
            if (action != null && action != GameAction.NONE) {
                path.addFirst(action);
            }
            curr = parentMap.get(curr);
        }
        return path;
    }
}

