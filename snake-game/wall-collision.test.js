/**
 * Snake Game - Wall Collision Detection Tests
 *
 * Tests for Scenario: Wall Collision Detection
 * - Test Case 1: Snake head reaches top wall - Game over triggered
 * - Test Case 2: Snake head reaches bottom wall - Game over triggered
 * - Test Case 3: Snake head reaches left wall - Game over triggered
 * - Test Case 4: Snake head reaches right wall - Game over triggered
 * - Test Case 5: Snake moves along wall without collision - Game continues normally
 */

const { SnakeGame, CONFIG, GameState, Direction } = require('./game.js');

// Mock canvas context
const createMockContext = () => ({
    fillStyle: '',
    strokeStyle: '',
    lineWidth: 0,
    fillRect: jest.fn(),
    strokeRect: jest.fn(),
    beginPath: jest.fn(),
    arc: jest.fn(),
    fill: jest.fn(),
    stroke: jest.fn(),
    clearRect: jest.fn()
});

describe('Snake Game - Wall Collision Detection', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        // Create mock context
        mockCtx = createMockContext();

        // Set up DOM environment with mocked canvas
        document.body.innerHTML = `
            <div id="game-container">
                <div id="score-board">
                    <span id="score-label">Score: <span id="score">0</span></span>
                    <span id="high-score-label">High Score: <span id="high-score">0</span></span>
                </div>
                <canvas id="game-board"></canvas>
                <div id="game-status">Press any key to start</div>
            </div>
        `;

        // Mock canvas getContext to return our mock context
        const canvas = document.getElementById('game-board');
        canvas.getContext = jest.fn(() => mockCtx);

        // Mock localStorage
        const localStorageMock = {
            getItem: jest.fn(),
            setItem: jest.fn(),
            clear: jest.fn()
        };
        Object.defineProperty(window, 'localStorage', {
            value: localStorageMock,
            writable: true
        });

        // Mock requestAnimationFrame and cancelAnimationFrame
        global.requestAnimationFrame = jest.fn(cb => setTimeout(cb, 0));
        global.cancelAnimationFrame = jest.fn();

        // Create new game instance
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.clearAllMocks();
        jest.useRealTimers();
    });

    /**
     * Test Case 1: Snake head reaches top wall
     * Input: Snake head reaches top wall (y < 0)
     * Expected: Game over triggered
     */
    describe('Test Case 1: Top Wall Collision', () => {
        test('should detect collision when snake head y position is less than 0', () => {
            // Position snake head at top boundary (y = -1)
            game.snake = [
                { x: 5, y: -1 },  // Head beyond top wall
                { x: 5, y: 0 },
                { x: 5, y: 1 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(true);
        });

        test('should trigger game over when snake moves into top wall', () => {
            // Position snake at top edge, moving up
            game.snake = [
                { x: 5, y: 0 },   // Head at top edge
                { x: 5, y: 1 },
                { x: 5, y: 2 }
            ];
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;
            game.state = GameState.PLAYING;

            // Move snake (head will move to y = -1)
            game.moveSnake();
            const collision = game.checkCollisions();

            expect(collision).toBe(true);
            expect(game.snake[0].y).toBe(-1);
        });

        test('should end game and set state to GAME_OVER on top wall collision', () => {
            game.snake = [
                { x: 5, y: -1 },
                { x: 5, y: 0 },
                { x: 5, y: 1 }
            ];
            game.state = GameState.PLAYING;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            expect(game.state).toBe(GameState.GAME_OVER);
        });

        test('should display game over message when hitting top wall', () => {
            game.snake = [
                { x: 5, y: -1 },
                { x: 5, y: 0 },
                { x: 5, y: 1 }
            ];
            game.state = GameState.PLAYING;
            game.score = 50;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
        });
    });

    /**
     * Test Case 2: Snake head reaches bottom wall
     * Input: Snake head reaches bottom wall (y >= gridHeight)
     * Expected: Game over triggered
     */
    describe('Test Case 2: Bottom Wall Collision', () => {
        test('should detect collision when snake head y position exceeds grid height', () => {
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            // Position snake head beyond bottom wall
            game.snake = [
                { x: 5, y: gridHeight },  // Head beyond bottom wall
                { x: 5, y: gridHeight - 1 },
                { x: 5, y: gridHeight - 2 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(true);
        });

        test('should trigger game over when snake moves into bottom wall', () => {
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            // Position snake at bottom edge, moving down
            game.snake = [
                { x: 5, y: gridHeight - 1 },  // Head at bottom edge
                { x: 5, y: gridHeight - 2 },
                { x: 5, y: gridHeight - 3 }
            ];
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;
            game.state = GameState.PLAYING;

            // Move snake (head will move to y = gridHeight)
            game.moveSnake();
            const collision = game.checkCollisions();

            expect(collision).toBe(true);
            expect(game.snake[0].y).toBe(gridHeight);
        });

        test('should end game and set state to GAME_OVER on bottom wall collision', () => {
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            game.snake = [
                { x: 5, y: gridHeight },
                { x: 5, y: gridHeight - 1 },
                { x: 5, y: gridHeight - 2 }
            ];
            game.state = GameState.PLAYING;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            expect(game.state).toBe(GameState.GAME_OVER);
        });

        test('should display game over message when hitting bottom wall', () => {
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            game.snake = [
                { x: 5, y: gridHeight },
                { x: 5, y: gridHeight - 1 },
                { x: 5, y: gridHeight - 2 }
            ];
            game.state = GameState.PLAYING;
            game.score = 30;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
        });
    });

    /**
     * Test Case 3: Snake head reaches left wall
     * Input: Snake head reaches left wall (x < 0)
     * Expected: Game over triggered
     */
    describe('Test Case 3: Left Wall Collision', () => {
        test('should detect collision when snake head x position is less than 0', () => {
            // Position snake head beyond left wall
            game.snake = [
                { x: -1, y: 5 },  // Head beyond left wall
                { x: 0, y: 5 },
                { x: 1, y: 5 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(true);
        });

        test('should trigger game over when snake moves into left wall', () => {
            // Position snake at left edge, moving left
            game.snake = [
                { x: 0, y: 5 },   // Head at left edge
                { x: 1, y: 5 },
                { x: 2, y: 5 }
            ];
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;
            game.state = GameState.PLAYING;

            // Move snake (head will move to x = -1)
            game.moveSnake();
            const collision = game.checkCollisions();

            expect(collision).toBe(true);
            expect(game.snake[0].x).toBe(-1);
        });

        test('should end game and set state to GAME_OVER on left wall collision', () => {
            game.snake = [
                { x: -1, y: 5 },
                { x: 0, y: 5 },
                { x: 1, y: 5 }
            ];
            game.state = GameState.PLAYING;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            expect(game.state).toBe(GameState.GAME_OVER);
        });

        test('should display game over message when hitting left wall', () => {
            game.snake = [
                { x: -1, y: 5 },
                { x: 0, y: 5 },
                { x: 1, y: 5 }
            ];
            game.state = GameState.PLAYING;
            game.score = 20;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
        });
    });

    /**
     * Test Case 4: Snake head reaches right wall
     * Input: Snake head reaches right wall (x >= gridWidth)
     * Expected: Game over triggered
     */
    describe('Test Case 4: Right Wall Collision', () => {
        test('should detect collision when snake head x position exceeds grid width', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;

            // Position snake head beyond right wall
            game.snake = [
                { x: gridWidth, y: 5 },  // Head beyond right wall
                { x: gridWidth - 1, y: 5 },
                { x: gridWidth - 2, y: 5 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(true);
        });

        test('should trigger game over when snake moves into right wall', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;

            // Position snake at right edge, moving right
            game.snake = [
                { x: gridWidth - 1, y: 5 },  // Head at right edge
                { x: gridWidth - 2, y: 5 },
                { x: gridWidth - 3, y: 5 }
            ];
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;
            game.state = GameState.PLAYING;

            // Move snake (head will move to x = gridWidth)
            game.moveSnake();
            const collision = game.checkCollisions();

            expect(collision).toBe(true);
            expect(game.snake[0].x).toBe(gridWidth);
        });

        test('should end game and set state to GAME_OVER on right wall collision', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;

            game.snake = [
                { x: gridWidth, y: 5 },
                { x: gridWidth - 1, y: 5 },
                { x: gridWidth - 2, y: 5 }
            ];
            game.state = GameState.PLAYING;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            expect(game.state).toBe(GameState.GAME_OVER);
        });

        test('should display game over message when hitting right wall', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;

            game.snake = [
                { x: gridWidth, y: 5 },
                { x: gridWidth - 1, y: 5 },
                { x: gridWidth - 2, y: 5 }
            ];
            game.state = GameState.PLAYING;
            game.score = 40;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
        });
    });

    /**
     * Test Case 5: Snake moves along wall without collision
     * Input: Snake moves along wall without collision
     * Expected: Game continues normally
     */
    describe('Test Case 5: Moving Along Wall Without Collision', () => {
        test('should not detect collision when snake moves along top edge', () => {
            // Position snake at top edge, moving right (parallel to wall)
            game.snake = [
                { x: 5, y: 0 },   // Head at top edge but inside
                { x: 4, y: 0 },
                { x: 3, y: 0 }
            ];
            game.direction = Direction.RIGHT;

            const collision = game.checkCollisions();

            expect(collision).toBe(false);
        });

        test('should not detect collision when snake moves along bottom edge', () => {
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            // Position snake at bottom edge, moving left (parallel to wall)
            game.snake = [
                { x: 5, y: gridHeight - 1 },   // Head at bottom edge but inside
                { x: 6, y: gridHeight - 1 },
                { x: 7, y: gridHeight - 1 }
            ];
            game.direction = Direction.LEFT;

            const collision = game.checkCollisions();

            expect(collision).toBe(false);
        });

        test('should not detect collision when snake moves along left edge', () => {
            // Position snake at left edge, moving down (parallel to wall)
            game.snake = [
                { x: 0, y: 5 },   // Head at left edge but inside
                { x: 0, y: 4 },
                { x: 0, y: 3 }
            ];
            game.direction = Direction.DOWN;

            const collision = game.checkCollisions();

            expect(collision).toBe(false);
        });

        test('should not detect collision when snake moves along right edge', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;

            // Position snake at right edge, moving up (parallel to wall)
            game.snake = [
                { x: gridWidth - 1, y: 5 },   // Head at right edge but inside
                { x: gridWidth - 1, y: 6 },
                { x: gridWidth - 1, y: 7 }
            ];
            game.direction = Direction.UP;

            const collision = game.checkCollisions();

            expect(collision).toBe(false);
        });

        test('should continue game when snake successfully moves along wall', () => {
            // Position snake at top edge, moving right
            game.snake = [
                { x: 5, y: 0 },
                { x: 4, y: 0 },
                { x: 3, y: 0 }
            ];
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;
            game.state = GameState.PLAYING;
            game.food = { x: 15, y: 15 }; // Food somewhere else

            // Move snake
            game.moveSnake();

            // Check no collision
            const collision = game.checkCollisions();

            expect(collision).toBe(false);
            expect(game.snake[0].x).toBe(6);
            expect(game.snake[0].y).toBe(0);
        });

        test('should keep state as PLAYING when moving along wall', () => {
            game.snake = [
                { x: 5, y: 0 },
                { x: 4, y: 0 },
                { x: 3, y: 0 }
            ];
            game.direction = Direction.RIGHT;
            game.state = GameState.PLAYING;

            const collision = game.checkCollisions();

            if (!collision) {
                // Game continues
                expect(game.state).toBe(GameState.PLAYING);
            }
        });

        test('should allow snake to turn away from wall at corner', () => {
            // Position snake at top-left corner, moving right
            game.snake = [
                { x: 0, y: 0 },   // Head at corner
                { x: 0, y: 1 },
                { x: 0, y: 2 }
            ];
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;
            game.state = GameState.PLAYING;
            game.food = { x: 15, y: 15 };

            // No collision at corner
            let collision = game.checkCollisions();
            expect(collision).toBe(false);

            // Move right (away from corner)
            game.moveSnake();
            collision = game.checkCollisions();

            expect(collision).toBe(false);
            expect(game.snake[0].x).toBe(1);
            expect(game.snake[0].y).toBe(0);
        });

        test('should allow movement in center of board without collision', () => {
            // Position snake in center of board
            game.snake = [
                { x: 10, y: 10 },
                { x: 9, y: 10 },
                { x: 8, y: 10 }
            ];
            game.direction = Direction.RIGHT;

            const collision = game.checkCollisions();

            expect(collision).toBe(false);
        });
    });

    // Additional edge case tests
    describe('Edge Cases and Boundary Conditions', () => {
        test('should detect collision at top-left corner', () => {
            game.snake = [
                { x: -1, y: -1 },  // Head beyond top-left corner
                { x: 0, y: 0 },
                { x: 1, y: 0 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(true);
        });

        test('should detect collision at top-right corner', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;

            game.snake = [
                { x: gridWidth, y: -1 },  // Head beyond top-right corner
                { x: gridWidth - 1, y: 0 },
                { x: gridWidth - 2, y: 0 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(true);
        });

        test('should detect collision at bottom-left corner', () => {
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            game.snake = [
                { x: -1, y: gridHeight },  // Head beyond bottom-left corner
                { x: 0, y: gridHeight - 1 },
                { x: 1, y: gridHeight - 1 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(true);
        });

        test('should detect collision at bottom-right corner', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            game.snake = [
                { x: gridWidth, y: gridHeight },  // Head beyond bottom-right corner
                { x: gridWidth - 1, y: gridHeight - 1 },
                { x: gridWidth - 2, y: gridHeight - 1 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(true);
        });

        test('should not detect collision when snake is entirely within boundaries', () => {
            game.snake = [
                { x: 5, y: 5 },
                { x: 4, y: 5 },
                { x: 3, y: 5 }
            ];

            const collision = game.checkCollisions();

            expect(collision).toBe(false);
        });

        test('should save high score when game ends due to wall collision', () => {
            game.snake = [
                { x: -1, y: 5 },
                { x: 0, y: 5 },
                { x: 1, y: 5 }
            ];
            game.state = GameState.PLAYING;
            game.score = 100;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            expect(window.localStorage.setItem).toHaveBeenCalledWith('snakeHighScore', '100');
        });

        test('should verify grid dimensions are correct for collision detection', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            // Grid should be 20x20 based on 400/20
            expect(gridWidth).toBe(20);
            expect(gridHeight).toBe(20);

            // Valid positions should be 0 to 19 (inclusive)
            game.snake = [{ x: 0, y: 0 }, { x: 1, y: 0 }, { x: 2, y: 0 }];
            expect(game.checkCollisions()).toBe(false);

            game.snake = [{ x: 19, y: 19 }, { x: 18, y: 19 }, { x: 17, y: 19 }];
            expect(game.checkCollisions()).toBe(false);

            game.snake = [{ x: 20, y: 19 }, { x: 19, y: 19 }, { x: 18, y: 19 }];
            expect(game.checkCollisions()).toBe(true);

            game.snake = [{ x: 19, y: 20 }, { x: 19, y: 19 }, { x: 19, y: 18 }];
            expect(game.checkCollisions()).toBe(true);
        });
    });
});

// Integration test: Full game flow with wall collision
describe('Integration Test: Game Flow with Wall Collision', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        mockCtx = createMockContext();

        document.body.innerHTML = `
            <div id="game-container">
                <div id="score-board">
                    <span id="score-label">Score: <span id="score">0</span></span>
                    <span id="high-score-label">High Score: <span id="high-score">0</span></span>
                </div>
                <canvas id="game-board"></canvas>
                <div id="game-status">Press any key to start</div>
            </div>
        `;

        const canvas = document.getElementById('game-board');
        canvas.getContext = jest.fn(() => mockCtx);

        Object.defineProperty(window, 'localStorage', {
            value: { getItem: jest.fn(), setItem: jest.fn(), clear: jest.fn() },
            writable: true
        });

        global.requestAnimationFrame = jest.fn(cb => setTimeout(cb, 0));
        global.cancelAnimationFrame = jest.fn();

        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    test('should complete full game cycle: start -> play -> wall collision -> game over', () => {
        // Start game
        expect(game.state).toBe(GameState.READY);

        game.startGame();
        expect(game.state).toBe(GameState.PLAYING);

        // Position snake to hit left wall
        game.snake = [
            { x: 0, y: 10 },
            { x: 1, y: 10 },
            { x: 2, y: 10 }
        ];
        game.direction = Direction.LEFT;
        game.nextDirection = Direction.LEFT;
        game.food = { x: 15, y: 15 };

        // Move snake into wall
        game.moveSnake();
        const collision = game.checkCollisions();

        expect(collision).toBe(true);

        // Trigger game over
        game.gameOver();

        expect(game.state).toBe(GameState.GAME_OVER);
        expect(global.cancelAnimationFrame).toHaveBeenCalled();
    });

    test('should allow game restart after wall collision game over', () => {
        // Start and end game
        game.startGame();
        game.snake = [{ x: -1, y: 10 }, { x: 0, y: 10 }, { x: 1, y: 10 }];
        game.gameOver();

        expect(game.state).toBe(GameState.GAME_OVER);

        // Reset and restart
        game.resetGame();
        game.startGame();

        expect(game.state).toBe(GameState.PLAYING);
        expect(game.score).toBe(0);
        expect(game.snake.length).toBe(3);
    });
});
