package boundary;

import java.util.List;
import java.util.Random;

import entity.enums.Faction;
import entity.pieces.Bishop;
import entity.pieces.King;
import entity.pieces.Knight;
import entity.pieces.Pawn;
import entity.pieces.Piece;
import entity.pieces.Queen;
import entity.pieces.Rook;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.util.Duration;

/**
 * A non-interactive sneak peek for "Fun chess". It plays a one-shot scripted
 * cinematic: the white queen grabs a pawn, the black g7 pawn snaps it back,
 * then
 * White's bishop creeps toward the king while a taunt plays and an "evolved
 * ability" triggers -- the board glitches, cuts to black, and a larger 12x12
 * board is revealed.
 *
 * <p>
 * None of this goes through the real rules ({@code LegalMove} etc.); it is all
 * hand-scripted node animation, so moves chess would forbid (taking the queen
 * into check, closing in on the king) are fine here.
 *
 * <p>
 * Board orientation and piece drawing match the live {@link GameScreen} (a-file
 * on the left, rank 8 at the top). Squares are addressed [x][y] with x = file
 * (a..h -> 0..7) and y = rank (1..8 -> 0..7).
 */
public class TrailerScreen extends Screen {
    private static final int N = 8;
    private static final double SQUARE = 70;
    private static final Color LIGHT = Color.web("#EEEED2");
    private static final Color DARK = Color.web("#769656");

    // cinematic timing
    private static final Duration HOLD_BEFORE = Duration.millis(700);
    private static final Duration SLIDE = Duration.millis(1000);
    private static final Duration GAP = Duration.millis(500);
    private static final Duration BISHOP_SLIDE = Duration.seconds(9); // "very slowly"
    private static final Duration TEXT_SHOWN = Duration.seconds(3); // taunt + glitch finish before the bishop reaches
                                                                    // g7
    private static final Duration DARKNESS = Duration.seconds(3);

    // 12x12 "evolved" board + king-shuffle minigame (homage to Geometry Dash LIMBO)
    private static final int BIG = 12;
    private static final double BIG_SQUARE = 44;
    private static final Duration SHUFFLE_TOTAL = Duration.seconds(8); // all 26 turns finish within this
    private static final Duration BISHOP_PICK = Duration.millis(1100); // the bishop's pick slide (slightly slow)
    private static final Duration SCROLL_CYCLE = Duration.millis(1000); // time to scroll one full board width (constant
                                                                        // speed)
    private static final Duration RETURN_FADE = Duration.seconds(4); // "very slowly" fade back to the 8x8 board
    private static final Color GREEN = Color.web("#43D17A");
    private static final Color PIECE_DARK = Color.web("#202020"); // matches PieceGlyphs' black king fill

    // The eight key slots on the 12x12 board, in reading order TL, TR, R1L, R1R,
    // R2L,
    // R2R, BL, BR (file h = 7, file k = 10; ranks 11, 8, 5, 2 from top to bottom).
    private static final int[][] SLOT_POS = {
            { 7, 10 }, { 10, 10 }, // TL, TR
            { 7, 7 }, { 10, 7 }, // R1L, R1R
            { 7, 4 }, { 10, 4 }, // R2L, R2R
            { 7, 1 }, { 10, 1 }, // BL, BR
    };

    // The LIMBO key shuffle, decoded frame-by-frame from the reference (limbo.pdf):
    // one
    // row per frame, each value the identity ("colour") of the king in that slot
    // (slot
    // order = SLOT_POS). 27 frames => 26 turns. Kings render black; the colours are
    // only
    // their tracked identities. Colour ids: 0 red, 1 yellow, 2 lime, 3 green, 4
    // blue,
    // 5 cyan, 6 purple, 7 pink. (Decoded by eye -- correct a row if a turn looks
    // wrong.)
    private static final int[][] SHUFFLE_FRAMES = {
            { 3, 1, 4, 6, 7, 5, 2, 0 }, // 1
            { 0, 2, 3, 1, 4, 6, 7, 5 }, // 2
            { 2, 1, 0, 3, 6, 5, 4, 7 }, // 3
            { 1, 3, 2, 0, 5, 7, 6, 4 }, // 4
            { 2, 1, 3, 5, 0, 6, 4, 7 }, // 5
            { 3, 2, 0, 1, 4, 5, 7, 6 }, // 6
            { 4, 5, 7, 6, 3, 2, 0, 1 }, // 7
            { 5, 6, 4, 7, 2, 1, 3, 0 }, // 8
            { 7, 4, 6, 5, 0, 3, 1, 2 }, // 9
            { 6, 7, 5, 4, 3, 2, 0, 1 }, // 10
            { 0, 1, 3, 2, 4, 5, 7, 6 }, // 11
            { 2, 0, 5, 1, 7, 3, 6, 4 }, // 12
            { 1, 2, 3, 0, 4, 5, 6, 7 }, // 13
            { 3, 1, 0, 2, 5, 7, 4, 6 }, // 14
            { 0, 2, 5, 7, 4, 6, 1, 3 }, // 15
            { 5, 0, 7, 2, 6, 3, 4, 1 }, // 16
            { 2, 5, 3, 0, 4, 7, 1, 6 }, // 17
            { 3, 2, 0, 5, 1, 4, 6, 7 }, // 18
            { 0, 3, 5, 2, 6, 1, 7, 4 }, // 19
            { 4, 7, 1, 6, 2, 5, 3, 0 }, // 20
            { 7, 6, 4, 1, 3, 2, 0, 5 }, // 21
            { 4, 1, 3, 2, 0, 5, 6, 7 }, // 22
            { 3, 4, 0, 1, 6, 2, 7, 5 }, // 23
            { 4, 1, 3, 0, 7, 6, 5, 2 }, // 24
            { 3, 4, 0, 1, 5, 7, 2, 6 }, // 25
            { 0, 3, 5, 4, 2, 1, 6, 7 }, // 26
            { 5, 4, 2, 1, 6, 7, 3, 0 }, // 27
    };

    // Which feature this trailer belongs to, so "Back" returns to its
    // under-development screen rather than jumping all the way to the menu.
    private final String feature;

    // The pieces that move during the cinematic (kept so they can be redrawn).
    private final Piece queen = new Queen(Faction.WHITE);
    private final Piece pawn = new Pawn(Faction.BLACK);
    private final Piece bishop = new Bishop(Faction.WHITE);

    // view
    private final StackPane[][] squares = new StackPane[N][N];
    private final Random rnd = new Random();
    private BorderPane content; // header / board / back
    private StackPane board; // the 8x8 board (grid + overlay), swapped out at the reveal
    private Pane overlay; // sits above the grid so a moving piece can cross cells
    private Rectangle glitchRect; // colour-flash layer for the "nhiễu" glitch
    private Label message; // centred taunt text
    private Label choosePrompt; // "Choose the King", pinned to the 12x12 board's top row
    private Region darkness; // full-window blackout
    private StackPane queenMover, pawnMover, bishopMover;
    private TranslateTransition bishopCreep; // the slow c3 approach; abandoned when the screen goes dark
    private SequentialTransition cinematic;

    // 12x12 minigame state -- a looping checkerboard treadmill beneath fixed pieces
    private Pane worldContainer; // holds two identical checkerboard copies; scrolled left and looped
    private Pane pieceLayer; // fixed overlay above the board: kings + bishop + particles (does NOT scroll)
    private StackPane[] kings; // the eight kings on the fixed foreground layer
    private StackPane bishopToken; // the bishop on the fixed foreground layer
    private int[] kingX, kingY; // logical cell of each king on the foreground
    private int realKing;
    private int bishopX, bishopY;
    private Timeline scroll; // the looping horizontal scroll
    private SequentialTransition minigame;

    public TrailerScreen(Navigator navigator, String feature) {
        super(navigator);
        this.feature = feature;
    }

    @Override
    public Parent getView() {
        Piece[][] position = buildPosition();

        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);

        // Screen row 0 is the top = rank 8 (board y = 7), matching GameScreen.
        for (int row = 0; row < N; row++) {
            for (int col = 0; col < N; col++) {
                int bx = col; // file a..h -> x 0..7
                int by = N - 1 - row; // top row -> y 7

                Rectangle bg = new Rectangle(SQUARE, SQUARE);
                bg.setFill(((bx + by) % 2 == 0) ? DARK : LIGHT);
                StackPane cell = new StackPane(bg);

                Piece piece = position[bx][by];
                if (piece != null) {
                    cell.getChildren().add(PieceGlyphs.glyph(piece, SQUARE * 0.72));
                }
                squares[bx][by] = cell;
                grid.add(cell, col, row);
            }
        }

        // A transparent overlay the same size as the board lets a moving piece
        // slide smoothly across cell boundaries instead of jumping cell to cell.
        double boardSize = N * SQUARE;
        overlay = new Pane();
        overlay.setMinSize(boardSize, boardSize);
        overlay.setMaxSize(boardSize, boardSize);
        overlay.setMouseTransparent(true);

        glitchRect = new Rectangle(boardSize, boardSize, Color.TRANSPARENT);
        glitchRect.setMouseTransparent(true);

        board = new StackPane(grid, overlay, glitchRect);
        board.setMaxSize(boardSize, boardSize);

        VBox header = new VBox(6, titleLabel(feature), subtitleLabel(""));
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(0, 0, 12, 0));

        Button back = secondaryButton("Back");
        back.setOnAction(e -> navigator.showUnderDevelopment(feature));

        content = new BorderPane();
        content.setTop(header);
        content.setCenter(board);
        content.setBottom(back);
        BorderPane.setAlignment(back, Pos.CENTER);
        BorderPane.setMargin(back, new Insets(12, 0, 0, 0));
        content.setPadding(new Insets(16));
        content.setStyle("-fx-background-color:" + BG + ";");

        message = horrorLabel();

        darkness = new Region();
        darkness.setStyle("-fx-background-color:black;");
        darkness.setVisible(false);

        StackPane sceneRoot = new StackPane(content, message, darkness);

        startCinematic(sceneRoot);
        return sceneRoot;
    }

    // ----- cinematic -----

    private void startCinematic(Parent sceneRoot) {
        queenMover = mover(queen, 6, 5); // g6
        pawnMover = mover(pawn, 6, 6); // g7
        bishopMover = mover(bishop, 2, 2); // c3

        // The bishop creeps slowly from c3 toward g7. It runs fire-and-forget (kicked
        // off
        // when armed) so the screen can cut to black the instant the glitch ends -- the
        // bishop may never reach g7, and that is fine.
        bishopCreep = slide(bishopMover, 2, 2, 6, 6, BISHOP_SLIDE);

        cinematic = new SequentialTransition(
                new PauseTransition(HOLD_BEFORE),
                action(() -> arm(queenMover, 6, 5)), // lift queen off g6
                slide(queenMover, 6, 5, 7, 5, SLIDE), // g6 -> h6
                action(() -> land(queenMover, 7, 5, queen)), // queen takes the h6 pawn
                new PauseTransition(GAP),
                action(() -> arm(pawnMover, 6, 6)), // lift pawn off g7
                slide(pawnMover, 6, 6, 7, 5, SLIDE), // g7 -> h6
                action(() -> land(pawnMover, 7, 5, pawn)), // pawn takes the queen
                new PauseTransition(GAP),
                action(() -> { // lift bishop off c3 and start its slow creep (fire-and-forget)
                    arm(bishopMover, 2, 2);
                    bishopCreep.playFromStart();
                }),
                action(() -> showMessage("")),
                new PauseTransition(TEXT_SHOWN),
                action(() -> showMessage("")),
                new PauseTransition(TEXT_SHOWN),
                action(this::hideMessage),
                buildGlitch(), // the board "nhiễu" while the bishop is still mid-creep
                action(this::goDark), // black the instant the glitch ends (bishop may not have reached g7)
                new PauseTransition(DARKNESS), // hold darkness ~3s
                action(this::revealEvolved)); // fade up to the 12x12 board
        cinematic.setCycleCount(1);

        sceneRoot.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                cinematic.stop();
                if (minigame != null) {
                    minigame.stop();
                }
                if (scroll != null) {
                    scroll.stop();
                }
            }
        });
        cinematic.play();
    }

    /** A near-instant step in a transition sequence that just runs {@code r}. */
    private PauseTransition action(Runnable r) {
        PauseTransition p = new PauseTransition(Duration.millis(1));
        p.setOnFinished(e -> r.run());
        return p;
    }

    /**
     * Creates a hidden glyph on the overlay, positioned over square (fromX, fromY).
     */
    private StackPane mover(Piece piece, int fromX, int fromY) {
        StackPane m = new StackPane(PieceGlyphs.glyph(piece, SQUARE * 0.72));
        m.setPrefSize(SQUARE, SQUARE);
        m.setLayoutX(fromX * SQUARE);
        m.setLayoutY((N - 1 - fromY) * SQUARE);
        m.setMouseTransparent(true);
        m.setVisible(false);
        overlay.getChildren().add(m);
        return m;
    }

    /**
     * A translate carrying {@code m} from square (fromX,fromY) to (toX,toY) over
     * {@code d}.
     */
    private TranslateTransition slide(StackPane m, int fromX, int fromY, int toX, int toY, Duration d) {
        TranslateTransition tt = new TranslateTransition(d, m);
        tt.setFromX(0);
        tt.setFromY(0);
        tt.setToX((toX - fromX) * SQUARE);
        tt.setToY((fromY - toY) * SQUARE); // screen y grows down as rank decreases
        tt.setInterpolator(Interpolator.EASE_BOTH);
        return tt;
    }

    /** Lift a piece off its square onto the overlay, ready to slide. */
    private void arm(StackPane m, int fromX, int fromY) {
        setCellPiece(fromX, fromY, null);
        m.setTranslateX(0);
        m.setTranslateY(0);
        m.setVisible(true);
    }

    /** Drop the mover onto its destination square and hide it. */
    private void land(StackPane m, int toX, int toY, Piece piece) {
        m.setVisible(false);
        setCellPiece(toX, toY, piece);
    }

    /**
     * A hidden, bold horror-font label with black text and no box (taunt / prompt).
     */
    private Label horrorLabel() {
        Label l = new Label();
        l.setFont(Font.font(pickHorrorFont(), FontWeight.BOLD, 54));
        l.setTextFill(Color.BLACK);
        l.setWrapText(true);
        l.setMaxWidth(620);
        l.setAlignment(Pos.CENTER);
        l.setStyle("-fx-text-alignment:center; -fx-padding:18 34;");
        l.setVisible(false);
        return l;
    }

    private void showMessage(String text) {
        showLabel(message, text);
    }

    private void showLabel(Label label, String text) {
        label.setText(text);
        label.setOpacity(0);
        label.setVisible(true);
        FadeTransition fade = new FadeTransition(Duration.millis(250), label);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    private void hideMessage() {
        message.setVisible(false);
    }

    /**
     * Picks the first installed "horror"-ish font, falling back to a serif so the
     * taunt still renders something heavier than the default sans if none exist.
     */
    private String pickHorrorFont() {
        List<String> families = Font.getFamilies();
        for (String name : new String[] { "Chiller", "Old English Text MT", "Blackadder ITC", "Jokerman" }) {
            if (families.contains(name)) {
                return name;
            }
        }
        return "Serif";
    }

    /** A short burst of static: jitter, flashing colour and flickering opacity. */
    private Timeline buildGlitch() {
        Timeline t = new Timeline();
        int frames = 12;
        double stepMs = 90;
        for (int i = 0; i < frames; i++) {
            t.getKeyFrames().add(new KeyFrame(Duration.millis(stepMs * i), e -> glitchTick()));
        }
        t.getKeyFrames().add(new KeyFrame(Duration.millis(stepMs * frames), e -> clearGlitch()));
        return t;
    }

    private void glitchTick() {
        board.setTranslateX((rnd.nextDouble() - 0.5) * 18);
        board.setTranslateY((rnd.nextDouble() - 0.5) * 18);
        board.setOpacity(0.45 + rnd.nextDouble() * 0.55);
        glitchRect.setFill(Color.color(rnd.nextDouble(), rnd.nextDouble(), rnd.nextDouble(), 0.35));
    }

    private void clearGlitch() {
        board.setTranslateX(0);
        board.setTranslateY(0);
        board.setOpacity(1);
        glitchRect.setFill(Color.TRANSPARENT);
    }

    /** Cut to black, swapping the populated 12x12 board in behind the darkness. */
    private void goDark() {
        if (bishopCreep != null) {
            bishopCreep.stop(); // abandon the creep wherever it got to
        }
        hideMessage();
        clearGlitch();
        content.setCenter(buildEvolvedBoard());
        darkness.setOpacity(1);
        darkness.setVisible(true);
    }

    /**
     * Fade the darkness away to reveal the 12x12 board, then run the king shuffle.
     */
    private void revealEvolved() {
        scroll.play(); // the 12x12 board scrolls continuously until we go back to 8x8
        FadeTransition fade = new FadeTransition(Duration.millis(700), darkness);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setOnFinished(e -> {
            darkness.setVisible(false);
            startKingShuffle();
        });
        fade.play();
    }

    /**
     * Replace a square's contents with the glyph for {@code piece}, or clear it
     * when null.
     */
    private void setCellPiece(int bx, int by, Piece piece) {
        StackPane cell = squares[bx][by];
        cell.getChildren().retainAll(cell.getChildren().get(0)); // keep the background rectangle
        if (piece != null) {
            cell.getChildren().add(PieceGlyphs.glyph(piece, SQUARE * 0.72));
        }
    }

    // ----- 12x12 "evolved" board + king-shuffle minigame (homage to GD LIMBO)
    // -----

    /**
     * Builds the 12x12 board as a scrolling checkerboard treadmill (TWO identical
     * copies side by side) beneath a FIXED foreground layer that carries the eight
     * kings and the bishop. Scrolling the checkerboard left by a board width and
     * looping makes the squares flow past the stationary pieces, so the bishop and
     * kings all look like they are travelling across an endless board (a
     * Geometry-Dash camera); the kings additionally shuffle on the foreground.
     */
    private Node buildEvolvedBoard() {
        double size = BIG * BIG_SQUARE;

        realKing = SHUFFLE_FRAMES[0][2]; // the king in the second row, first column (slot R1L) at the start
        bishopX = 2; // c6
        bishopY = 5;

        // the scrolling background: two identical checkerboard copies, side by side
        worldContainer = new Pane();
        for (int c = 0; c < 2; c++) {
            GridPane grid = new GridPane();
            grid.setAlignment(Pos.CENTER);
            for (int row = 0; row < BIG; row++) {
                for (int col = 0; col < BIG; col++) {
                    Rectangle bg = new Rectangle(BIG_SQUARE, BIG_SQUARE);
                    bg.setFill(((row + col) % 2 == 0) ? DARK : LIGHT);
                    grid.add(new StackPane(bg), col, row);
                }
            }
            grid.setLayoutX(c * size); // the second copy sits one board-width to the right
            worldContainer.getChildren().add(grid);
        }

        // the fixed foreground: kings + bishop sit ABOVE the board and do NOT scroll,
        // so
        // the checkerboard flows beneath them and they all look like they are
        // travelling
        // across an endless board. The kings additionally shuffle among their slots.
        pieceLayer = new Pane();
        pieceLayer.setMinSize(size, size);
        pieceLayer.setMaxSize(size, size);
        pieceLayer.setMouseTransparent(true);
        // place the eight (black) kings at their frame-0 slots, indexed by colour
        // identity
        kings = new StackPane[8];
        kingX = new int[8];
        kingY = new int[8];
        int[] frame0 = SHUFFLE_FRAMES[0];
        for (int s = 0; s < frame0.length; s++) {
            int c = frame0[s];
            kings[c] = token(pieceLayer, new King(Faction.BLACK), SLOT_POS[s][0], SLOT_POS[s][1]);
            kingX[c] = SLOT_POS[s][0];
            kingY[c] = SLOT_POS[s][1];
        }
        bishopToken = token(pieceLayer, new Bishop(Faction.WHITE), bishopX, bishopY);

        // a clipped viewport one board wide, so only one checkerboard copy shows at a
        // time
        Pane viewport = new Pane(worldContainer, pieceLayer);
        viewport.setMinSize(size, size);
        viewport.setMaxSize(size, size);
        viewport.setClip(new Rectangle(size, size));

        // continuous leftward scroll of the checkerboard at a CONSTANT speed: a single
        // linear glide across one full board width, looped FOREVER (until we go back to
        // the 8x8 board). The leftmost column wraps around to the end; seamless because
        // the
        // two copies are identical, so when a cycle ends at -size it looks identical to
        // the
        // start at 0. The pieces are NOT in here -- they sit on the fixed foreground
        // above
        // so the board flows beneath them.
        scroll = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(worldContainer.translateXProperty(), 0.0, Interpolator.LINEAR)),
                new KeyFrame(SCROLL_CYCLE,
                        new KeyValue(worldContainer.translateXProperty(), -size, Interpolator.LINEAR)));
        scroll.setCycleCount(Animation.INDEFINITE);

        // "Choose the King" rides above the board on its FIRST ROW only, in a normal
        // font.
        // Constraining its height to one square keeps it inside the top row.
        choosePrompt = new Label();
        choosePrompt.setFont(Font.font(30));
        choosePrompt.setTextFill(Color.BLACK);
        choosePrompt.setAlignment(Pos.CENTER);
        choosePrompt.setMinHeight(BIG_SQUARE);
        choosePrompt.setPrefHeight(BIG_SQUARE);
        choosePrompt.setMaxHeight(BIG_SQUARE);
        choosePrompt.setVisible(false);
        StackPane boardStack = new StackPane(viewport, choosePrompt);
        boardStack.setMaxSize(size, size);
        StackPane.setAlignment(choosePrompt, Pos.TOP_CENTER);
        return boardStack;
    }

    /** A movable glyph on the given overlay, positioned over square (x, y). */
    private StackPane token(Pane overlay, Piece piece, int x, int y) {
        StackPane t = new StackPane(PieceGlyphs.glyph(piece, BIG_SQUARE * 0.78));
        t.setPrefSize(BIG_SQUARE, BIG_SQUARE);
        t.setLayoutX(bigLayoutX(x));
        t.setLayoutY(bigLayoutY(y));
        t.setMouseTransparent(true);
        overlay.getChildren().add(t);
        return t;
    }

    private double bigLayoutX(int x) {
        return x * BIG_SQUARE;
    }

    private double bigLayoutY(int y) {
        return (BIG - 1 - y) * BIG_SQUARE;
    }

    /**
     * Plays the LIMBO-style minigame: flash the real king green, then run the 26
     * LIMBO
     * key turns decoded from the reference frames WHILE the checkerboard scrolls
     * and
     * loops beneath the fixed foreground, so the kings and bishop all appear to
     * travel
     * across the board. After a "Choose the King" prompt the bishop slides to a
     * wrong
     * king, the screen blacks out right before it arrives, and we return to the 8x8
     * board. Nothing here is random -- the same sequence plays every time for the
     * demo.
     */
    private void startKingShuffle() {
        SequentialTransition seq = new SequentialTransition();

        // 1) reveal the real king (green for exactly 2s), then back to black (board
        // still)
        seq.getChildren().addAll(
                new PauseTransition(Duration.millis(600)),
                action(() -> colorKing(realKing, GREEN)),
                new PauseTransition(Duration.seconds(1)),
                action(() -> colorKing(realKing, PIECE_DARK)), // back to black
                new PauseTransition(Duration.millis(400)));

        // 2) the 26 LIMBO key turns from the reference frames, all packed into
        // SHUFFLE_TOTAL.
        // Each turn slides every king from its slot in one frame to its slot in the
        // next;
        // kings are identical black, so the colour ids are only their tracked
        // identities.
        SequentialTransition shuffle = new SequentialTransition();
        Duration stepTime = SHUFFLE_TOTAL.divide(SHUFFLE_FRAMES.length - 1); // 26 turns in SHUFFLE_TOTAL
        for (int p = 0; p + 1 < SHUFFLE_FRAMES.length; p++) {
            int[] from = slotOfColor(SHUFFLE_FRAMES[p]);
            int[] to = slotOfColor(SHUFFLE_FRAMES[p + 1]);
            ParallelTransition step = new ParallelTransition();
            for (int c = 0; c < 8; c++) {
                int a = from[c], b = to[c];
                step.getChildren().add(moveToken(kings[c],
                        SLOT_POS[a][0], SLOT_POS[a][1], SLOT_POS[b][0], SLOT_POS[b][1], stepTime));
            }
            shuffle.getChildren().add(step);
        }
        int[] finalSlot = slotOfColor(SHUFFLE_FRAMES[SHUFFLE_FRAMES.length - 1]);
        for (int c = 0; c < 8; c++) { // commit final cells
            kingX[c] = SLOT_POS[finalSlot[c]][0];
            kingY[c] = SLOT_POS[finalSlot[c]][1];
        }

        // the turns play while the checkerboard keeps scrolling underneath on its own
        // (started in revealEvolved and never stopped until we go back to 8x8)
        seq.getChildren().add(shuffle);

        // 3) "Choose the King" on the board's top row for 3s (a player would pick here;
        // we wait)
        seq.getChildren().addAll(
                action(() -> showLabel(choosePrompt, "Choose the King")),
                new PauseTransition(Duration.seconds(3)),
                action(() -> choosePrompt.setVisible(false)));

        // 4) the bishop slides to a WRONG king; cut to black RIGHT BEFORE it arrives,
        // so the
        // "capture" is hidden. The slide runs on its own so the blackout can overlap
        // its end.
        int target = (realKing + 4) % kings.length;
        TranslateTransition bishopMove = moveToken(bishopToken, bishopX, bishopY,
                kingX[target], kingY[target], BISHOP_PICK);
        seq.getChildren().addAll(
                new PauseTransition(Duration.millis(400)),
                action(bishopMove::play),
                new PauseTransition(BISHOP_PICK.subtract(Duration.millis(350))), // almost the whole slide
                action(this::backToEightByEight)); // black for 5s, then return VERY SLOWLY to 8x8

        minigame = seq;
        seq.play();
    }

    /** Inverts a frame (slot -> colour id) into colour id -> slot. */
    private int[] slotOfColor(int[] frame) {
        int[] slotOf = new int[frame.length];
        for (int s = 0; s < frame.length; s++) {
            slotOf[frame[s]] = s;
        }
        return slotOf;
    }

    /**
     * A translate carrying a 12x12 token from (fromX,fromY) to (toX,toY), then
     * committing the move to the token's layout so later moves start from there.
     */
    private TranslateTransition moveToken(StackPane token, int fromX, int fromY, int toX, int toY, Duration d) {
        TranslateTransition tt = new TranslateTransition(d, token);
        tt.setFromX(0);
        tt.setFromY(0);
        tt.setToX(bigLayoutX(toX) - bigLayoutX(fromX));
        tt.setToY(bigLayoutY(toY) - bigLayoutY(fromY));
        tt.setInterpolator(Interpolator.EASE_BOTH);
        tt.setOnFinished(e -> {
            token.setLayoutX(bigLayoutX(toX));
            token.setLayoutY(bigLayoutY(toY));
            token.setTranslateX(0);
            token.setTranslateY(0);
        });
        return tt;
    }

    private void colorKing(int index, Color color) {
        Text glyph = (Text) kings[index].getChildren().get(0);
        glyph.setFill(color);
    }

    /**
     * Black out, hold black for 5s, then return VERY SLOWLY to the 8x8 board --
     * shown in
     * the position AFTER Qxh6 and gxh6 (so g6 and g7 are empty, a black pawn sits
     * on h6)
     * and with the bishop missing from c3.
     */
    private void backToEightByEight() {
        if (scroll != null) {
            scroll.stop(); // the 12x12 board finally stops moving
        }
        darkness.setOpacity(0);
        darkness.setVisible(true);
        FadeTransition toBlack = new FadeTransition(Duration.millis(250), darkness);
        toBlack.setFromValue(0);
        toBlack.setToValue(1);
        toBlack.setOnFinished(e -> {
            Piece[][] pos = buildPosition();
            pos[6][5] = null; // g6 empty: the queen moved to h6 (and was captured there)
            pos[6][6] = null; // g7 empty: that pawn captured on h6 (already a black pawn at h6)
            pos[2][2] = null; // bishop missing from c3
            content.setCenter(buildStaticBoard(pos, N, SQUARE));
            PauseTransition hold = new PauseTransition(Duration.seconds(5)); // black for 5s
            hold.setOnFinished(ev -> {
                FadeTransition up = new FadeTransition(RETURN_FADE, darkness); // very slow fade-in
                up.setFromValue(1);
                up.setToValue(0);
                up.setOnFinished(e2 -> darkness.setVisible(false));
                up.play();
            });
            hold.play();
        });
        toBlack.play();
    }

    /** A static (non-animated) board rendered from {@code pos}. */
    private Node buildStaticBoard(Piece[][] pos, int n, double square) {
        GridPane grid = new GridPane();
        grid.setAlignment(Pos.CENTER);
        for (int row = 0; row < n; row++) {
            for (int col = 0; col < n; col++) {
                int bx = col;
                int by = n - 1 - row;
                Rectangle bg = new Rectangle(square, square);
                bg.setFill(((bx + by) % 2 == 0) ? DARK : LIGHT);
                StackPane cell = new StackPane(bg);
                Piece piece = pos[bx][by];
                if (piece != null) {
                    cell.getChildren().add(PieceGlyphs.glyph(piece, square * 0.72));
                }
                grid.add(cell, col, row);
            }
        }
        return grid;
    }

    /**
     * The hand-placed preview position, indexed {@code [x][y]} with x = file
     * (a..h -> 0..7) and y = rank (1..8 -> 0..7), the same convention the rest of
     * the app uses.
     */
    private Piece[][] buildPosition() {
        Piece[][] b = new Piece[N][N];

        // White
        b[0][0] = new Rook(Faction.WHITE); // a1
        b[4][0] = new Rook(Faction.WHITE); // e1
        b[6][0] = new King(Faction.WHITE); // g1
        b[1][1] = new Pawn(Faction.WHITE); // b2
        b[5][1] = new Pawn(Faction.WHITE); // f2
        b[6][1] = new Pawn(Faction.WHITE); // g2
        b[7][1] = new Pawn(Faction.WHITE); // h2
        b[0][2] = new Pawn(Faction.WHITE); // a3
        b[2][2] = new Bishop(Faction.WHITE); // c3
        b[6][5] = new Queen(Faction.WHITE); // g6
        b[7][6] = new Bishop(Faction.WHITE); // h7

        // Black
        b[2][3] = new Pawn(Faction.BLACK); // c4
        b[1][4] = new Pawn(Faction.BLACK); // b5
        b[3][4] = new Bishop(Faction.BLACK); // d5
        b[0][5] = new Pawn(Faction.BLACK); // a6
        b[2][5] = new Knight(Faction.BLACK); // c6
        b[3][5] = new Bishop(Faction.BLACK); // d6
        b[3][6] = new Queen(Faction.BLACK); // d7
        b[6][6] = new Pawn(Faction.BLACK); // g7
        b[7][5] = new Pawn(Faction.BLACK); // h6
        b[0][7] = new Rook(Faction.BLACK); // a8
        b[3][7] = new Rook(Faction.BLACK); // d8
        b[7][7] = new King(Faction.BLACK); // h8

        return b;
    }
}
