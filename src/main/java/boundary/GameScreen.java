package boundary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import control.BoardSetup;
import control.ChessEngine;
import control.GameController;
import entity.board.Cell;
import entity.enums.Faction;
import entity.enums.Result;
import entity.move.Move;
import entity.move.Promotion;
import entity.pieces.Bishop;
import entity.pieces.King;
import entity.pieces.Knight;
import entity.pieces.Pawn;
import entity.pieces.Piece;
import entity.pieces.Queen;
import entity.pieces.Rook;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * The chessboard screen (two-player). Renders the board and pieces and drives
 * the game through clicks: select one of the side-to-move's pieces, then click
 * a highlighted square to move. All rules stay in the control/entity layers;
 * this class only draws state and forwards moves to {@link GameController}.
 */
public class GameScreen extends Screen {
    // visual constants
    private static final int N = 8;
    private static final double SQUARE = 80;
    private static final Color LIGHT = Color.web("#EEEED2");
    private static final Color DARK = Color.web("#769656");
    private static final Color SELECTED = Color.web("#F6F669");
    private static final Color HINT = Color.web("#000000", 0.18);

    // game
    private GameController gameController;

    // engine opponent (only used in "Play vs Engine" mode)
    private static final int ENGINE_DEPTH = 6; // how many half-moves the engine looks ahead
    private final boolean vsEngine; // true when the human plays against the engine
    private Faction engineSide = Faction.BLACK; // the side the engine plays; flips with the board in engine mode
    private ChessEngine engine; // created on game start when vsEngine is true

    // view
    private final StackPane[][] squares = new StackPane[N][N];
    private GridPane grid; // the board grid; cells are re-laid out on flip
    private Label statusLabel;
    private Button undoButton;

    // board orientation: false = White at the bottom (default), true = rotated 180
    // so Black sits at the bottom. In engine mode the engine plays the side now at
    // the top, so flipping makes it play White instead of Black.
    private boolean flipped = false;

    // interaction state
    private Integer selX = null;
    private Integer selY = null;
    private List<Move> selMoves = new ArrayList<>();

    public GameScreen(Navigator navigator) {
        this(navigator, false);
    }

    public GameScreen(Navigator navigator, boolean vsEngine) {
        super(navigator);
        this.vsEngine = vsEngine;
    }

    @Override
    public Parent getView() {
        gameController = new GameController();
        gameController.startGame(new BoardSetup());

        // In engine mode, build the engine that will answer the human's moves.
        if (vsEngine) {
            engine = new ChessEngine(gameController, ENGINE_DEPTH);
        }

        grid = new GridPane();
        grid.setAlignment(Pos.CENTER);

        // Build one StackPane per board square, indexed by board coordinates. Their
        // position in the grid is decided by layoutGrid(), which depends on the
        // current orientation, so a flip just re-lays out these same cells.
        for (int bx = 0; bx < N; bx++) {
            for (int by = 0; by < N; by++) {
                Rectangle bg = new Rectangle(SQUARE, SQUARE);
                StackPane cell = new StackPane(bg);
                cell.setUserData(new int[] { bx, by });
                cell.setOnMouseClicked(e -> onClick((int[]) cell.getUserData()));

                squares[bx][by] = cell;
            }
        }
        layoutGrid();

        // Top bar: return to the main menu, undo, and flip the board.
        Button menuButton = new Button("Menu");
        menuButton.setFont(Font.font(14));
        menuButton.setStyle("-fx-padding: 6 18;");
        menuButton.setOnAction(e -> navigator.showMainMenu());

        undoButton = new Button("Undo");
        undoButton.setFont(Font.font(14));
        undoButton.setStyle("-fx-padding: 6 18;");
        undoButton.setOnAction(e -> undo());

        Button flipButton = new Button("Flip");
        flipButton.setFont(Font.font(14));
        flipButton.setStyle("-fx-padding: 6 18;");
        flipButton.setOnAction(e -> flip());

        HBox topBar = new HBox(10, menuButton, undoButton, flipButton);
        topBar.setAlignment(Pos.CENTER);
        topBar.setPadding(new Insets(0, 0, 10, 0));

        statusLabel = new Label();
        statusLabel.setFont(Font.font(18));
        statusLabel.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setTop(topBar);
        root.setCenter(grid);
        root.setBottom(statusLabel);
        BorderPane.setAlignment(statusLabel, Pos.CENTER);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #312E2B;");

        redraw();
        return root;
    }

    // ----- interaction -----

    private void onClick(int[] pos) {
        if (gameController.getGameState().getGameStatus() != Result.ONGOING) {
            return;
        }

        int x = pos[0];
        int y = pos[1];
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard();
        Piece clicked = board[x][y].getContain();
        Faction turn = gameController.getGameState().getTurn();

        // A piece is already selected: try to move there.
        if (selX != null) {
            Move chosen = findMove(x, y);
            if (chosen != null) {
                play(chosen);
                return;
            }
        }

        // (Re)select one of the side-to-move's pieces.
        if (clicked != null && clicked.getSide() == turn) {
            selectPiece(x, y);
        } else {
            clearSelection();
        }
        redraw();
    }

    private void selectPiece(int x, int y) {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard();
        Piece piece = board[x][y].getContain();
        List<Move> history = gameController.getGameState().getMoveHistory();

        selX = x;
        selY = y;
        selMoves = new ArrayList<>();
        for (Move m : piece.move(board, x, y, history)) {
            if (gameController.getLegalMove().isLegal(m)) {
                selMoves.add(m);
            }
        }
    }

    private Move findMove(int x, int y) {
        for (Move m : selMoves) {
            if (m.getEndXPos() == x && m.getEndYPos() == y) {
                return m;
            }
        }
        return null;
    }

    private void play(Move move) {
        if (move instanceof Promotion) {
            Piece promoted = askPromotion(move.getPiece().getSide());
            ((Promotion) move).setPiecePromoted(promoted);
        }
        gameController.executes(move);

        clearSelection();
        gameController.checkGameStatus();

        maybeEngineMove(); // if we're playing the engine, let it reply now

        redraw();
    }

    /**
     * In engine mode, have the engine play its move whenever it is the engine's
     * turn and the game is still going. The engine already fills in a queen for
     * its own promotions, so no promotion dialog is needed here.
     */
    private void maybeEngineMove() {
        if (!vsEngine || engine == null) {
            return; // two-player game, or engine not ready: nothing to do
        }
        if (gameController.getGameState().getGameStatus() != Result.ONGOING) {
            return; // the human's move just ended the game
        }
        if (gameController.getGameState().getTurn() != engineSide) {
            return; // it's still the human's turn
        }
        Move reply = engine.findBestMove(); // ask the engine for its best move
        if (reply == null) {
            return; // no legal reply (mate/stalemate); checkGameStatus already set the result
        }
        gameController.executes(reply); // play the engine's move
        gameController.checkGameStatus(); // update the result after the engine moved
    }

    /**
     * Places every board square into the grid according to the current
     * orientation. Default: screen row 0 (top) is rank 8 and file a is on the
     * left. Flipped: the board is rotated 180 so rank 1 is at the top and file h
     * is on the left, i.e. the view from Black's side.
     */
    private void layoutGrid() {
        grid.getChildren().clear();
        for (int bx = 0; bx < N; bx++) {
            for (int by = 0; by < N; by++) {
                int col = flipped ? (N - 1 - bx) : bx;
                int row = flipped ? by : (N - 1 - by);
                grid.add(squares[bx][by], col, row);
            }
        }
    }

    /**
     * Flips the board between White's and Black's point of view. In engine mode
     * the engine plays whichever side is now at the top, so flipping hands it the
     * opposite colour (e.g. White instead of Black); if that makes it the engine's
     * turn it replies straight away.
     */
    private void flip() {
        flipped = !flipped;
        if (vsEngine) {
            engineSide = flipped ? Faction.WHITE : Faction.BLACK;
        }
        layoutGrid();
        clearSelection();
        redraw();
    }

    private void undo() {
        gameController.undoMove();
        // In engine mode, one undo takes back the engine's reply and leaves it on
        // the engine's turn; undo once more so control returns to the human.
        if (vsEngine && !gameController.getGameState().getMoveHistory().isEmpty()
                && gameController.getGameState().getTurn() == engineSide) {
            gameController.undoMove();
        }
        gameController.getGameState().setGameStatus(Result.ONGOING);
        clearSelection();
        redraw();
    }

    private void clearSelection() {
        selX = null;
        selY = null;
        selMoves = new ArrayList<>();
    }

    // ----- rendering -----

    private void redraw() {
        Cell[][] board = gameController.getGameState().getChessBoard().getBoard();

        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                StackPane cell = squares[x][y];
                Rectangle bg = (Rectangle) cell.getChildren().get(0);

                // base colour
                boolean selectedSquare = selX != null && selX == x && selY == y;
                if (selectedSquare) {
                    bg.setFill(SELECTED);
                } else {
                    bg.setFill(((x + y) % 2 == 0) ? DARK : LIGHT);
                }

                // clear everything except the background rectangle
                cell.getChildren().retainAll(bg);

                // piece glyph
                Piece piece = board[x][y].getContain();
                if (piece != null) {
                    cell.getChildren().add(glyph(piece));
                }

                // move hint
                if (isTarget(x, y)) {
                    Circle hint = new Circle(piece == null ? SQUARE * 0.16 : SQUARE * 0.45);
                    hint.setFill(piece == null ? HINT : Color.TRANSPARENT);
                    if (piece != null) {
                        hint.setStroke(HINT);
                        hint.setStrokeWidth(6);
                    }
                    cell.getChildren().add(hint);
                }
            }
        }

        // Nothing to undo at the start position.
        undoButton.setDisable(gameController.getGameState().getMoveHistory().isEmpty());

        updateStatus();
    }

    private boolean isTarget(int x, int y) {
        return findMove(x, y) != null;
    }

    private Text glyph(Piece piece) {
        Text t = new Text(symbol(piece));
        t.setFont(Font.font("Segoe UI Symbol", SQUARE * 0.72));
        if (piece.getSide() == Faction.WHITE) {
            t.setFill(Color.WHITE);
            t.setStroke(Color.web("#333333"));
            t.setStrokeWidth(1.2);
        } else {
            t.setFill(Color.web("#202020"));
            t.setStroke(Color.web("#202020"));
            t.setStrokeWidth(1.2);
        }
        return t;
    }

    private void updateStatus() {
        Result status = gameController.getGameState().getGameStatus();
        statusLabel.setTextFill(Color.WHITE);
        switch (status) {
            case WHITE_WIN -> statusLabel.setText("Checkmate \u2014 White wins");
            case BLACK_WIN -> statusLabel.setText("Checkmate \u2014 Black wins");
            case DRAW -> statusLabel.setText("Draw");
            default -> {
                Faction turn = gameController.getGameState().getTurn();
                statusLabel.setText((turn == Faction.WHITE ? "White" : "Black") + " to move");
            }
        }
    }

    // ----- promotion dialog -----

    private Piece askPromotion(Faction side) {
        ButtonType queen = new ButtonType("Queen");
        ButtonType rook = new ButtonType("Rook");
        ButtonType bishop = new ButtonType("Bishop");
        ButtonType knight = new ButtonType("Knight");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Promotion");
        dialog.setHeaderText("Promote pawn to:");
        dialog.getDialogPane().getButtonTypes().addAll(queen, rook, bishop, knight);

        Optional<ButtonType> choice = dialog.showAndWait();
        ButtonType picked = choice.orElse(queen);
        if (picked == rook) {
            return new Rook(side);
        }
        if (picked == bishop) {
            return new Bishop(side);
        }
        if (picked == knight) {
            return new Knight(side);
        }
        return new Queen(side);
    }

    // ----- glyphs -----

    private String symbol(Piece piece) {
        // Solid (filled) glyphs are used for both colours; the fill colour set in
        // glyph() distinguishes white from black, which reads best on the board.
        if (piece instanceof King) {
            return "\u265A";
        }
        if (piece instanceof Queen) {
            return "\u265B";
        }
        if (piece instanceof Rook) {
            return "\u265C";
        }
        if (piece instanceof Bishop) {
            return "\u265D";
        }
        if (piece instanceof Knight) {
            return "\u265E";
        }
        if (piece instanceof Pawn) {
            return "\u265F";
        }
        return "";
    }
}
