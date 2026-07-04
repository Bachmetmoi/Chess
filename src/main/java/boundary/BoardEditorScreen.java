package boundary;

import java.util.Optional;

import control.BoardSetup;
import control.CustomBoardSetup;
import entity.board.Cell;
import entity.enums.Faction;
import entity.move.Move;
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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

/**
 * The "Customize" screen: build a position by dragging pieces from a palette
 * onto the board (right-click a square to clear it), pick which side moves
 * first, then launch a game. It only edits a {@code Piece[][]} placement; the
 * actual game is created through {@link CustomBoardSetup}, so all rules stay in
 * the control/entity layers.
 */
public class BoardEditorScreen extends Screen {
    private static final int N = 8;
    private static final double SQUARE = 48;
    private static final double TILE = 42;
    private static final Color LIGHT = Color.web("#EEEED2");
    private static final Color DARK = Color.web("#769656");

    // placement[x][y]: file, rank; null = empty. Matches ChessBoard.getBoard().
    private final Piece[][] placement = new Piece[N][N];
    private final StackPane[][] squares = new StackPane[N][N];
    private Faction sideToMove = Faction.WHITE;
    private Label message;

    public BoardEditorScreen(Navigator navigator) {
        super(navigator);
    }

    @Override
    public Parent getView() {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);

        // Screen row 0 is the top = rank 8 (board y = 7), matching GameScreen.
        for (int row = 0; row < N; row++) {
            for (int col = 0; col < N; col++) {
                int bx = col;
                int by = N - 1 - row;
                squares[bx][by] = makeCell(bx, by);
                grid.add(squares[bx][by], col, row);
            }
        }

        message = new Label();
        message.setFont(Font.font("Segoe UI", 14));
        message.setTextFill(Color.web("#E6A0A0"));

        VBox root = new VBox(10,
                topBar(),
                grid,
                paletteHint(),
                paletteRow(Faction.WHITE),
                paletteRow(Faction.BLACK),
                utilBar(),
                message,
                startBar());
        root.setAlignment(Pos.CENTER);
        root.setPadding(new Insets(12));
        root.setStyle("-fx-background-color:" + BG + ";");

        redraw();
        return root;
    }

    // ----- top bar: back + side to move -----

    private HBox topBar() {
        Button back = secondaryButton("Back");
        back.setPrefWidth(110);
        back.setOnAction(e -> navigator.showModeSelect());

        Label sideLabel = new Label("Side to move:");
        sideLabel.setTextFill(CREAM);
        sideLabel.setFont(Font.font("Segoe UI", 15));

        ToggleGroup group = new ToggleGroup();
        ToggleButton white = sideToggle("White", group);
        ToggleButton black = sideToggle("Black", group);
        white.setSelected(true);
        group.selectedToggleProperty().addListener((obs, old, cur) -> {
            if (cur == null) {
                // keep exactly one side selected
                if (old != null) {
                    old.setSelected(true);
                }
            } else {
                sideToMove = (cur == white) ? Faction.WHITE : Faction.BLACK;
            }
        });

        Region spacer = new Region();
        spacer.setMinWidth(24);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(8, back, spacer, sideLabel, white, black);
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private ToggleButton sideToggle(String text, ToggleGroup group) {
        ToggleButton b = new ToggleButton(text);
        b.setToggleGroup(group);
        b.setFont(Font.font("Segoe UI", 14));
        b.setStyle("-fx-padding:6 16;");
        return b;
    }

    // ----- board cells -----

    private StackPane makeCell(int bx, int by) {
        Rectangle bg = new Rectangle(SQUARE, SQUARE);
        bg.setFill(((bx + by) % 2 == 0) ? DARK : LIGHT);
        StackPane cell = new StackPane(bg);
        cell.setUserData(new int[] { bx, by });

        // right-click clears the square
        cell.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.SECONDARY) {
                placement[bx][by] = null;
                redraw();
            }
        });

        // pieces already on the board can be dragged (to move or remove)
        cell.setOnDragDetected(e -> {
            if (placement[bx][by] == null) {
                return;
            }
            Dragboard db = cell.startDragAndDrop(TransferMode.COPY_OR_MOVE);
            ClipboardContent cc = new ClipboardContent();
            cc.putString("board:" + bx + ":" + by);
            db.setContent(cc);
            e.consume();
        });

        cell.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });

        cell.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean done = false;
            if (db.hasString()) {
                done = applyDrop(db.getString(), bx, by);
            }
            e.setDropCompleted(done);
            if (done) {
                redraw();
            }
            e.consume();
        });

        return cell;
    }

    /** Applies a drag payload dropped on square (tx, ty). Returns true if handled. */
    private boolean applyDrop(String payload, int tx, int ty) {
        String[] parts = payload.split(":");
        if (parts.length == 3 && parts[0].equals("palette")) {
            placement[tx][ty] = newPiece(Faction.valueOf(parts[1]), parts[2].charAt(0));
            return true;
        }
        if (parts.length == 3 && parts[0].equals("board")) {
            int sx = Integer.parseInt(parts[1]);
            int sy = Integer.parseInt(parts[2]);
            if (sx != tx || sy != ty) {
                placement[tx][ty] = placement[sx][sy];
                placement[sx][sy] = null;
            }
            return true;
        }
        return false;
    }

    // ----- palette -----

    private Label paletteHint() {
        Label l = subtitleLabel("Drag a piece onto the board — right-click a square to clear it.");
        l.setFont(Font.font("Segoe UI", 14));
        return l;
    }

    private HBox paletteRow(Faction faction) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER);
        for (char type : new char[] { 'K', 'Q', 'R', 'B', 'N', 'P' }) {
            row.getChildren().add(paletteTile(faction, type));
        }
        return row;
    }

    private StackPane paletteTile(Faction faction, char type) {
        Rectangle bg = new Rectangle(TILE, TILE);
        bg.setArcWidth(10);
        bg.setArcHeight(10);
        bg.setFill(Color.web("#3C3935"));
        bg.setStroke(Color.web("#5A5651"));

        Text glyph = PieceGlyphs.glyph(newPiece(faction, type), TILE * 0.72);
        glyph.setMouseTransparent(true);

        StackPane tile = new StackPane(bg, glyph);
        tile.setOnDragDetected(e -> {
            Dragboard db = tile.startDragAndDrop(TransferMode.COPY);
            ClipboardContent cc = new ClipboardContent();
            cc.putString("palette:" + faction.name() + ":" + type);
            db.setContent(cc);
            e.consume();
        });
        return tile;
    }

    // ----- utility bar (starting position / clear / remove drop zone) -----

    private HBox utilBar() {
        Button startPos = secondaryButton("Starting position");
        startPos.setOnAction(e -> loadStartingPosition());

        Button clear = secondaryButton("Clear board");
        clear.setOnAction(e -> {
            for (int x = 0; x < N; x++) {
                for (int y = 0; y < N; y++) {
                    placement[x][y] = null;
                }
            }
            redraw();
        });

        HBox bar = new HBox(10, startPos, clear, trashZone());
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private StackPane trashZone() {
        Label label = new Label("Drag here to remove");
        label.setTextFill(Color.web("#B9B6AE"));
        label.setFont(Font.font("Segoe UI", 13));
        label.setMouseTransparent(true);

        StackPane zone = new StackPane(label);
        zone.setPrefSize(180, 34);
        zone.setStyle("-fx-background-color:#2A2724; -fx-border-color:#5A5651; "
                + "-fx-border-radius:8; -fx-background-radius:8; "
                + "-fx-border-style: segments(6, 6);");

        zone.setOnDragOver(e -> {
            if (e.getDragboard().hasString()) {
                e.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            }
            e.consume();
        });
        zone.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean done = false;
            if (db.hasString()) {
                String[] parts = db.getString().split(":");
                if (parts.length == 3 && parts[0].equals("board")) {
                    placement[Integer.parseInt(parts[1])][Integer.parseInt(parts[2])] = null;
                    done = true;
                    redraw();
                }
            }
            e.setDropCompleted(done);
            e.consume();
        });
        return zone;
    }

    // ----- start buttons (reuse the Play menu's actions) -----

    private HBox startBar() {
        Button twoPlayer = menuButton("2 Player");
        twoPlayer.setOnAction(e -> onTwoPlayer());

        Button engine = menuButton("Engine");
        engine.setOnAction(e -> onEngine());

        HBox bar = new HBox(12, twoPlayer, engine);
        bar.setAlignment(Pos.CENTER);
        return bar;
    }

    private void onTwoPlayer() {
        String error = validate();
        if (error != null) {
            message.setText(error);
            return;
        }
        navigator.showGame(new CustomBoardSetup(snapshot()), sideToMove);
    }

    private void onEngine() {
        String error = validate();
        if (error != null) {
            message.setText(error);
            return;
        }
        Faction humanSide = askHumanSide();
        if (humanSide == null) {
            return; // dialog dismissed: stay in the editor with the position intact
        }
        // The engine takes the other colour; whoever the "Side to move" toggle
        // names moves first, so the engine may open the game.
        navigator.showEngineGame(new CustomBoardSetup(snapshot()), sideToMove,
                humanSide == Faction.BLACK);
    }

    /**
     * Asks which colour the human will play against the engine. Returns
     * {@code null} when the dialog is cancelled, so the caller stays in the
     * editor (leaving via a navigator screen would discard the position).
     */
    private Faction askHumanSide() {
        ButtonType white = new ButtonType("White");
        ButtonType black = new ButtonType("Black");

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Play vs Engine");
        dialog.setHeaderText("Play as:");
        dialog.getDialogPane().getButtonTypes().addAll(white, black, ButtonType.CANCEL);

        Optional<ButtonType> choice = dialog.showAndWait();
        if (choice.isEmpty() || choice.get() == ButtonType.CANCEL) {
            return null;
        }
        return choice.get() == white ? Faction.WHITE : Faction.BLACK;
    }

    // ----- helpers -----

    private void redraw() {
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                StackPane cell = squares[x][y];
                Rectangle bg = (Rectangle) cell.getChildren().get(0);
                cell.getChildren().retainAll(bg);
                Piece piece = placement[x][y];
                if (piece != null) {
                    Text glyph = PieceGlyphs.glyph(piece, SQUARE * 0.72);
                    glyph.setMouseTransparent(true);
                    cell.getChildren().add(glyph);
                }
            }
        }
        message.setText("");
    }

    private void loadStartingPosition() {
        Cell[][] board = new BoardSetup().setUp().getBoard();
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                placement[x][y] = board[x][y].getContain();
            }
        }
        sideToMove = Faction.WHITE;
        redraw();
    }

    /** A shallow copy of the current placement, safe to hand to the game. */
    private Piece[][] snapshot() {
        Piece[][] copy = new Piece[N][N];
        for (int x = 0; x < N; x++) {
            System.arraycopy(placement[x], 0, copy[x], 0, N);
        }
        return copy;
    }

    /**
     * Checks the position is legal enough to play: one king per side, no pawns on
     * the back ranks, and the side not to move is not left in check (which would
     * let the mover capture a king). Returns an error message, or null if valid.
     */
    private String validate() {
        int whiteKings = 0;
        int blackKings = 0;
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                Piece p = placement[x][y];
                if (p == null) {
                    continue;
                }
                if (p instanceof King) {
                    if (p.getSide() == Faction.WHITE) {
                        whiteKings++;
                    } else {
                        blackKings++;
                    }
                }
                if (p instanceof Pawn && (y == 0 || y == 7)) {
                    return "Pawns can't be on the first or last rank.";
                }
            }
        }
        if (whiteKings != 1) {
            return "Place exactly one white king.";
        }
        if (blackKings != 1) {
            return "Place exactly one black king.";
        }
        if (opponentKingAttacked()) {
            return "Illegal position: the side not to move is in check.";
        }
        return null;
    }

    /** True if a piece of the side to move already attacks the opponent's king. */
    private boolean opponentKingAttacked() {
        Cell[][] board = new CustomBoardSetup(snapshot()).setUp().getBoard();
        Faction defender = (sideToMove == Faction.WHITE) ? Faction.BLACK : Faction.WHITE;

        int kingX = -1;
        int kingY = -1;
        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                Piece p = board[x][y].getContain();
                if (p instanceof King && p.getSide() == defender) {
                    kingX = x;
                    kingY = y;
                }
            }
        }
        if (kingX < 0) {
            return false;
        }

        for (int x = 0; x < N; x++) {
            for (int y = 0; y < N; y++) {
                Piece p = board[x][y].getContain();
                if (p == null || p.getSide() != sideToMove) {
                    continue;
                }
                for (Move m : p.move(board, x, y)) {
                    if (m.getEndXPos() == kingX && m.getEndYPos() == kingY) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Piece newPiece(Faction faction, char type) {
        switch (type) {
            case 'K':
                return new King(faction);
            case 'Q':
                return new Queen(faction);
            case 'R':
                return new Rook(faction);
            case 'B':
                return new Bishop(faction);
            case 'N':
                return new Knight(faction);
            default:
                return new Pawn(faction);
        }
    }
}
