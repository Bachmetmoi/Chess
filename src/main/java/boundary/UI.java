package boundary;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import control.BoardSetup;
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
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

/**
 * JavaFX board UI. Renders the chessboard and pieces and drives the game purely
 * through clicks: select one of your pieces, then click a highlighted square to
 * move. All game rules stay in the control/entity layers; this class only draws
 * the state and forwards moves to {@link GameController}.
 */
public class UI extends Application {
    // visual constants
    private static final int N = 8;
    private static final double SQUARE = 80;
    private static final Color LIGHT = Color.web("#EEEED2");
    private static final Color DARK = Color.web("#769656");
    private static final Color SELECTED = Color.web("#F6F669");
    private static final Color HINT = Color.web("#000000", 0.18);

    // game
    private GameController gameController;

    // view
    private final StackPane[][] squares = new StackPane[N][N];
    private Label statusLabel;

    // interaction state
    private Integer selX = null;
    private Integer selY = null;
    private List<Move> selMoves = new ArrayList<>();

    @Override
    public void start(Stage stage) {
        gameController = new GameController();
        gameController.startGame(new BoardSetup());

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);

        // Build squares. Screen row 0 is the top = rank 8 (board y = 7).
        for (int row = 0; row < N; row++) {
            for (int col = 0; col < N; col++) {
                int bx = col;        // file a..h -> x 0..7
                int by = N - 1 - row; // top row -> y 7

                Rectangle bg = new Rectangle(SQUARE, SQUARE);
                StackPane cell = new StackPane(bg);
                cell.setUserData(new int[] { bx, by });
                cell.setOnMouseClicked(e -> onClick((int[]) cell.getUserData()));

                squares[bx][by] = cell;
                grid.add(cell, col, row);
            }
        }

        statusLabel = new Label();
        statusLabel.setFont(Font.font(18));
        statusLabel.setPadding(new Insets(10));

        BorderPane root = new BorderPane();
        root.setCenter(grid);
        root.setBottom(statusLabel);
        BorderPane.setAlignment(statusLabel, Pos.CENTER);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color: #312E2B;");

        redraw();

        stage.setTitle("Chess");
        stage.setScene(new Scene(root));
        stage.setResizable(false);
        stage.show();
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
        gameController.executes(move);

        // Promotion: the engine moves the pawn but keeps it a pawn, so the UI
        // asks the player what to promote to and swaps the piece on the board.
        if (move instanceof Promotion) {
            Cell[][] board = gameController.getGameState().getChessBoard().getBoard();
            Piece pawn = board[move.getEndXPos()][move.getEndYPos()].getContain();
            Piece promoted = askPromotion(pawn.getSide());
            board[move.getEndXPos()][move.getEndYPos()].setContain(promoted);
        }

        clearSelection();
        gameController.checkGameStatus();
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
            case WHITE_WIN -> statusLabel.setText("Checkmate — White wins");
            case BLACK_WIN -> statusLabel.setText("Checkmate — Black wins");
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
            return "♚";
        }
        if (piece instanceof Queen) {
            return "♛";
        }
        if (piece instanceof Rook) {
            return "♜";
        }
        if (piece instanceof Bishop) {
            return "♝";
        }
        if (piece instanceof Knight) {
            return "♞";
        }
        if (piece instanceof Pawn) {
            return "♟";
        }
        return "";
    }
}
