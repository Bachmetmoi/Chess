# Chess

A fully playable two-player chess game built from scratch in **Java 17** with a **JavaFX** board interface. It implements the complete rules of chess — including castling, en passant, and pawn promotion — and detects check, checkmate, and stalemate.

The codebase follows a clean **Entity–Control–Boundary (ECB)** layered architecture, keeping all game rules in the control/entity layers while the JavaFX UI only draws state and forwards clicks.

## Features

- Full legal-move generation for all pieces (King, Queen, Rook, Bishop, Knight, Pawn)
- Special moves: **castling** (king- and queen-side), **en passant**, and **pawn promotion** (choose Queen, Rook, Bishop, or Knight)
- Check detection — illegal moves that leave your own king in check are prevented
- End-of-game detection: **checkmate**, **stalemate/draw**
- Click-to-move JavaFX interface with move hints, square highlighting, and a turn/result status bar

## Requirements

- **JDK 17** or later
- **Maven 3.6+**

JavaFX is pulled in automatically as a Maven dependency, so no separate SDK install is needed.

## Running the game

From the project root:

```bash
mvn clean javafx:run
```

Alternatively, using the exec plugin:

```bash
mvn clean compile exec:java
```

The application window opens with a standard starting position. Click one of your pieces to see its legal moves (highlighted with dots), then click a highlighted square to move. When a pawn reaches the last rank, a dialog lets you choose the promotion piece.

## Project structure

The source is organized into three layers under `src/main/java`:

```
src/main/java/
├── App.java                      # Entry point — launches the JavaFX application
├── boundary/
│   ├── ChessApp.java             # JavaFX Application; creates the Navigator
│   ├── Navigator.java            # Owns the Stage/Scene and switches between screens
│   ├── Screen.java               # Base class shared by all screens
│   ├── MainMenuScreen.java       # Main menu
│   ├── ModeSelectScreen.java     # Choose 2 Player or Engine mode
│   ├── PlayAsScreen.java         # Pick which side to play against the engine
│   ├── BoardEditorScreen.java    # Set up a custom starting position
│   ├── GameScreen.java           # The board itself: rendering and click handling
│   ├── PieceGlyphs.java          # Piece glyphs used for drawing
│   ├── AboutScreen.java          # About page
│   └── UnderDevelopmentScreen.java
├── control/
│   ├── BoardSetup.java           # Builds the initial board with pieces
│   ├── CustomBoardSetup.java     # Builds a board from an editor-defined position
│   ├── GameController.java       # Drives turns, executes moves, checks game status
│   ├── LegalMove.java            # Validates moves (king-safety / check logic)
│   ├── ChessEngine.java          # Engine opponent: minimax search with alpha-beta pruning
│   ├── OpeningMove.java          # Opening book for the engine's first move as White
│   └── evaluate/                 # Position scoring: MainEvaluation blends
│                                 #   MiddleGameEvalation and EndGameEvaluation
└── entity/
    ├── board/                    # Cell, ChessBoard
    ├── enums/                    # BoardColor, Faction, Result
    ├── move/                     # Move, NormalMove, Castling, EnPassant, Promotion, SpecialMove
    ├── pieces/                   # Piece + King, Queen, Rook, Bishop, Knight, Pawn
    └── state/                    # GameState (turn, move history, status, board)
```

| Layer | Responsibility |
|-------|----------------|
| **Boundary** | User interaction and rendering (JavaFX screens) |
| **Control** | Game flow, move validation, board setup, chess engine |
| **Entity** | Domain model: board, pieces, moves, game state |

Class diagrams for each layer are available in the [`Class diagram ver 1/`](Class%20diagram%20ver%201/) folder.

## How it works

1. `App` launches `ChessApp`, whose `Navigator` walks you through the menu screens to a `GameScreen`, which creates a `GameController` and sets up the board via `BoardSetup`.
2. Each piece knows how to generate its candidate moves; `LegalMove` filters out any move that would leave its own king in check.
3. `GameController.executes()` applies the chosen move (handling castling and en passant specially), records it in the move history, and switches the turn.
4. After every move, `GameController.checkGameStatus()` determines whether the game is ongoing, a win, or a draw.

## Built with

- Java 17
- JavaFX 17
- Maven

---

*A personal project developed to practice object-oriented design and game logic implementation.*
