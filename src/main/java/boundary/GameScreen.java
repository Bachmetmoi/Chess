package boundary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import control.BoardSetup;
import control.GameController;
import control.engine.Engine;
import control.engine.Martin;
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
 * The chessboard screen. Renders the board and pieces and drives the game
 * through clicks: select one of the side-to-move's pieces, then click a
 * highlighted square to move. All rules stay in the control/entity layers; this
 * class only draws state and forwards moves to {@link GameController}.
 *
 * <p>In {@link GameMode#VS_ENGINE} the human plays White and the engine
 * (Martin) plays Black, replying automatically after each human move.
 */
public class GameScreen extends Screen {
    // visual constants
    private static final int N = 8;
    private static final double SQUARE = 80;
    private static final Color LIGHT = Color.web("#EEEED2");
    private static final Color DARK = Color.web("#769656");
    private static final Color SELECTED = Color.web("#F6F669");
    private static final Color HINT = Color.web("#000000", 0.18);

    // mode + engine
    private final GameMode mode;
    private Engine engine;          // null in two-player mode
    private Faction engineSide;     // null in two-player mode

    // game
    private GameController gameController;

    // view
    private final StackPane[][] squares = new StackPane[N][N];
    private Label statusLabel;
    private Button undoButton;

    // interaction state
    private Integer selX = null;
    private Integer selY = null;
    private List<Move> selMoves = new ArrayList<>();

    /** Two-player game. */
    public GameScreen(Navigator navigator) {
        this(navigator, GameMode.TWO_PLAYER);
    }

    /** Game in the given mode. */
    public GameScreen(Navigator navigator, GameMode mode) {
        super(navigator);
        this.mode = mode;
    }

    @Override
    public Parent getView() {
        gameController = new GameController();
        gameController.startGame(new BoardSetup());

        // In engine mode, the human plays White and Martin plays Black.
        if (mode == GameMode.VS_ENGINE) {
            engine = new Martin(gameController);
            engineSide = Faction.BLACK;
        }

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);

        // Build squares. Screen row 0 is the top = rank 8 (board y = 7).
        for (int row = 0; row < N; row++) {
            for (int col = 0; col < N; col++) {
                int bx = col; // file a..h -> x 0..7
                int by = N - 1 - row; // top row -> y 7

                Rectangle bg = new Rectangle(SQUARE, SQUARE);
                StackPane cell = new StackPane(bg);
                cell.setUserData(new int[] { bx, by });
                cell.setOnMouseClicked(e -> onClick((int[]) cell.getUserData()));

                squares[bx][by] = cell;
                grid.add(cell, col, row);
            }
        }

        // Top bar: return to the main menu, and undo.
        Button menuButton = new Button("Menu");
        menuButton.setFont(Font.font(14));
        menuButton.setStyle("-fx-padding: 6 18;");
        menuButton.setOnAction(e -> navigator.showMainMenu());

        undoButton = new Button("Undo");
        undoButton.setFont(Font.font(14));
        undoButton.setStyle("-fx-padding: 6 18;");
        undoButton.setOnAction(e -> undo());

        HBox topBar = new HBox(10, menuButton, undoButton);
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

        // In engine mode, ignore board clicks while it is the engine's turn.
        if (mode == GameMode.VS_ENGINE && gameController.getGameState().getTurn() == engineSide) {
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
        redraw();

        // Let the engine reply to the human's move.
        maybeEngineMove();
    }

    /**
     * If we are in engine mode, the game is still ongoing and it is the engine's
     * turn, ask the engine for a move and play it. The engine always promotes to
     * a Queen (there is no human to ask).
     */
    private void maybeEngineMove() {
        if (mode != GameMode.VS_ENGINE) {
            return;
        }
        if (gameController.getGameState().getGameStatus() != Result.ONGOING) {
            return;
        }
        if (gameController.getGameState().getTurn() != engineSide) {
            return;
        }

        Move move = engine.chooseMove(gameController.getGameState());
        if (move == null) {
            return;
        }
        if (move instanceof Promotion) {
            ((Promotion) move).setPiecePromoted(new Queen(engineSide));
        }

        gameController.executes(move);
        gameController.checkGameStatus();
        redraw();
    }

    private void undo() {
        gameController.undoMove();
        gameController.getGameState().setGameStatus(Result.ONGOING);

        // In engine mode a turn is two plies (human + engine). After undoing the
        // engine's reply it is the engine's turn again, so step back once more to
        // return control to the human.
        if (mode == GameMode.VS_ENGINE
                && gameController.getGameState().getTurn() == engineSide
                && !gameController.getGameState().getMoveHistory().isEmpty()) {
            gameController.undoMove();
            gameController.getGameState().setGameStatus(Result.ONGOING);
        }

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
