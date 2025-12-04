/**
 * Snake Game - Test Suite for Self Collision Detection
 *
 * Tests for Scenario: Self Collision Detection
 * - Test Case 1: Snake head position equals any body segment position -> Game over triggered
 * - Test Case 2: Snake head adjacent to body (no collision) -> Game continues normally
 * - Test Case 3: Long snake self-collision scenario -> Collision detected accurately regardless of snake length
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

// Helper function to set up DOM and mock environment
const setupTestEnvironment = () => {
    const mockCtx = createMockContext();

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

    const localStorageMock = {
        getItem: jest.fn(),
        setItem: jest.fn(),
        clear: jest.fn()
    };
    Object.defineProperty(window, 'localStorage', {
        value: localStorageMock,
        writable: true
    });

    return { mockCtx, canvas };
};

describe('Snake Game - Self Collision Detection', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        const env = setupTestEnvironment();
        mockCtx = env.mockCtx;
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Snake head position equals any body segment position
     * Input: Snake head position equals any body segment position
     * Expected: Game over triggered
     */
    describe('Test Case 1: Self Collision Detection - Head Equals Body Segment', () => {
        test('should detect collision when head overlaps with body segment', () => {
            // Set up snake where head is at same position as a body segment
            // Snake: head at (5,5), body at [(5,5), (4,5), (3,5)]
            // This simulates head moving to position of second segment
            game.snake = [
                { x: 5, y: 5 },  // head
                { x: 5, y: 5 },  // body segment at same position as head
                { x: 4, y: 5 },
                { x: 3, y: 5 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should detect collision when head equals second body segment', () => {
            // Snake turns back and head hits second segment
            game.snake = [
                { x: 6, y: 5 },  // head
                { x: 5, y: 5 },
                { x: 6, y: 5 },  // body segment at same position as head
                { x: 7, y: 5 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should detect collision when head equals last body segment', () => {
            // Head position equals last body segment
            game.snake = [
                { x: 3, y: 5 },  // head
                { x: 4, y: 5 },
                { x: 5, y: 5 },
                { x: 3, y: 5 }   // tail at same position as head
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should detect collision when head equals middle body segment', () => {
            // Snake with head at middle segment position
            game.snake = [
                { x: 5, y: 6 },  // head
                { x: 5, y: 5 },
                { x: 5, y: 6 },  // middle segment at same position as head
                { x: 5, y: 7 },
                { x: 4, y: 7 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should trigger game over state when self collision detected', () => {
            // Set up collision scenario
            game.snake = [
                { x: 5, y: 5 },
                { x: 5, y: 5 },
                { x: 4, y: 5 }
            ];

            game.state = GameState.PLAYING;

            // Manually call gameOver if collision detected
            if (game.checkCollisions()) {
                game.gameOver();
            }

            expect(game.state).toBe(GameState.GAME_OVER);
        });

        test('should not check head against itself (index 0)', () => {
            // Snake with unique positions - no collision
            game.snake = [
                { x: 5, y: 5 },  // head
                { x: 4, y: 5 },
                { x: 3, y: 5 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should correctly identify collision with different x and y coordinates', () => {
            // Test with various coordinate combinations
            game.snake = [
                { x: 10, y: 10 },  // head
                { x: 9, y: 10 },
                { x: 9, y: 11 },
                { x: 10, y: 11 },
                { x: 10, y: 10 }   // collision with head
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });
    });

    /**
     * Test Case 2: Snake head adjacent to body (no collision)
     * Input: Snake head adjacent to body (no collision)
     * Expected: Game continues normally
     */
    describe('Test Case 2: Adjacent Body - No Collision', () => {
        test('should not detect collision when head is adjacent but not overlapping', () => {
            // Snake moving right, head adjacent to body
            game.snake = [
                { x: 5, y: 5 },  // head
                { x: 4, y: 5 },  // adjacent to head (left)
                { x: 3, y: 5 },
                { x: 2, y: 5 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should allow head to be horizontally adjacent to body segment', () => {
            // Head at (5,5), body segment at (4,5) - adjacent horizontally
            game.snake = [
                { x: 5, y: 5 },
                { x: 4, y: 5 },
                { x: 4, y: 6 },
                { x: 4, y: 7 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should allow head to be vertically adjacent to body segment', () => {
            // Head at (5,5), body moving down
            game.snake = [
                { x: 5, y: 5 },
                { x: 5, y: 6 },  // adjacent below
                { x: 5, y: 7 },
                { x: 5, y: 8 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should allow head to be diagonally adjacent (not collision)', () => {
            // Diagonal adjacency should not trigger collision
            game.snake = [
                { x: 5, y: 5 },  // head
                { x: 4, y: 5 },
                { x: 4, y: 6 },
                { x: 4, y: 7 },
                { x: 4, y: 4 }   // diagonally adjacent to head (4,4)
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should continue game when snake is in normal movement pattern', () => {
            // Normal snake configuration with no collision
            game.snake = [
                { x: 10, y: 10 },
                { x: 9, y: 10 },
                { x: 8, y: 10 },
                { x: 7, y: 10 }
            ];

            game.state = GameState.PLAYING;
            const collision = game.checkCollisions();

            expect(collision).toBe(false);
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should not trigger collision for L-shaped snake configuration', () => {
            // L-shaped snake
            game.snake = [
                { x: 7, y: 5 },  // head
                { x: 6, y: 5 },
                { x: 5, y: 5 },
                { x: 5, y: 6 },
                { x: 5, y: 7 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should not trigger collision for U-shaped snake configuration', () => {
            // U-shaped snake
            game.snake = [
                { x: 7, y: 7 },  // head
                { x: 7, y: 6 },
                { x: 7, y: 5 },
                { x: 6, y: 5 },
                { x: 5, y: 5 },
                { x: 5, y: 6 },
                { x: 5, y: 7 }   // close to head but not same position
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should allow snake to curve around itself without collision', () => {
            // Spiral-like pattern without collision
            game.snake = [
                { x: 6, y: 6 },  // head
                { x: 5, y: 6 },
                { x: 5, y: 5 },
                { x: 6, y: 5 },
                { x: 7, y: 5 },
                { x: 7, y: 6 },
                { x: 7, y: 7 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });
    });

    /**
     * Test Case 3: Long snake self-collision scenario
     * Input: Long snake self-collision scenario
     * Expected: Collision detected accurately regardless of snake length
     */
    describe('Test Case 3: Long Snake Self-Collision (Integration)', () => {
        test('should detect collision in snake with 10 segments', () => {
            // Long snake with collision at segment 8
            game.snake = [
                { x: 5, y: 5 },   // head
                { x: 4, y: 5 },
                { x: 3, y: 5 },
                { x: 3, y: 6 },
                { x: 3, y: 7 },
                { x: 4, y: 7 },
                { x: 5, y: 7 },
                { x: 5, y: 6 },
                { x: 5, y: 5 },   // collision with head
                { x: 6, y: 5 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should detect collision in snake with 20 segments', () => {
            // Very long snake with collision at last segment
            game.snake = [];

            // Create a long snake with head at (10, 10)
            game.snake.push({ x: 10, y: 10 });  // head

            // Add 18 more segments in various positions
            for (let i = 1; i < 19; i++) {
                game.snake.push({ x: 10 - i, y: 10 });
            }

            // Add collision segment at same position as head
            game.snake.push({ x: 10, y: 10 });

            expect(game.snake.length).toBe(20);
            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should not detect collision in long snake without overlap', () => {
            // Very long snake with no collision
            game.snake = [];

            // Create a long straight snake
            for (let i = 0; i < 20; i++) {
                game.snake.push({ x: 15 - i, y: 10 });
            }

            expect(game.snake.length).toBe(20);
            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should detect collision in snake with 50 segments', () => {
            // Simulate a very long snake that loops back
            game.snake = [];

            // Head at (5, 5)
            game.snake.push({ x: 5, y: 5 });

            // Create body segments in a spiral pattern
            let x = 4, y = 5;
            for (let i = 1; i < 49; i++) {
                game.snake.push({ x, y });
                x = (x - 1 + 20) % 20; // Move left, wrap around
            }

            // Add final segment at head position to create collision
            game.snake.push({ x: 5, y: 5 });

            expect(game.snake.length).toBe(50);
            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should detect collision regardless of which body segment collides', () => {
            // Test collision detection at various segment positions
            const segmentPositions = [1, 5, 10, 15, 19]; // Test indices

            segmentPositions.forEach(collisionIndex => {
                game.snake = [];

                // Head at (10, 10)
                game.snake.push({ x: 10, y: 10 });

                // Create 20 segments
                for (let i = 1; i < 20; i++) {
                    if (i === collisionIndex) {
                        // Create collision at this index
                        game.snake.push({ x: 10, y: 10 });
                    } else {
                        game.snake.push({ x: 10 - i, y: 10 });
                    }
                }

                const collision = game.checkCollisions();
                expect(collision).toBe(true);
            });
        });

        test('should handle maximum realistic snake length (400 segments for 20x20 grid)', () => {
            // Create a snake that covers significant portion of the grid
            game.snake = [];

            // Head at (10, 10)
            game.snake.push({ x: 10, y: 10 });

            // Fill snake with unique positions
            const gridSize = 20;
            let count = 1;

            outer: for (let y = 0; y < gridSize; y++) {
                for (let x = 0; x < gridSize; x++) {
                    if (count >= 400) break outer;
                    if (x === 10 && y === 10) continue; // Skip head position

                    game.snake.push({ x, y });
                    count++;
                }
            }

            // No collision expected
            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should detect collision immediately when long snake turns into itself', () => {
            // Simulate snake turning into its own body after several moves
            game.snake = [
                { x: 8, y: 5 },   // head
                { x: 7, y: 5 },
                { x: 6, y: 5 },
                { x: 5, y: 5 },
                { x: 5, y: 6 },
                { x: 5, y: 7 },
                { x: 6, y: 7 },
                { x: 7, y: 7 },
                { x: 8, y: 7 },
                { x: 8, y: 6 },
                { x: 8, y: 5 }    // collision with head
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should accurately track game over state after long snake collision', () => {
            // Set up long snake with collision
            game.snake = [];
            game.snake.push({ x: 5, y: 5 }); // head

            for (let i = 1; i < 15; i++) {
                game.snake.push({ x: 5 - i + 20, y: 5 }); // body
            }
            game.snake.push({ x: 5, y: 5 }); // collision segment

            game.state = GameState.PLAYING;

            if (game.checkCollisions()) {
                game.gameOver();
            }

            expect(game.state).toBe(GameState.GAME_OVER);
        });
    });

    // Additional edge case tests
    describe('Edge Cases for Self Collision', () => {
        test('should not detect collision for minimum length snake (3 segments)', () => {
            game.snake = [
                { x: 5, y: 5 },
                { x: 4, y: 5 },
                { x: 3, y: 5 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });

        test('should detect collision in 4-segment snake (minimum for self-collision)', () => {
            // Minimum snake length that can self-collide
            game.snake = [
                { x: 4, y: 5 },  // head
                { x: 5, y: 5 },
                { x: 5, y: 6 },
                { x: 4, y: 5 }   // collision with head
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should detect collision at grid boundary', () => {
            // Snake near edge of grid with collision
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            game.snake = [
                { x: gridWidth - 1, y: gridHeight - 1 },  // head at corner
                { x: gridWidth - 2, y: gridHeight - 1 },
                { x: gridWidth - 2, y: gridHeight - 2 },
                { x: gridWidth - 1, y: gridHeight - 2 },
                { x: gridWidth - 1, y: gridHeight - 1 }   // collision with head
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should detect collision at origin (0,0)', () => {
            game.snake = [
                { x: 0, y: 0 },  // head at origin
                { x: 1, y: 0 },
                { x: 1, y: 1 },
                { x: 0, y: 1 },
                { x: 0, y: 0 }   // collision with head
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(true);
        });

        test('should handle snake position with zero coordinates', () => {
            game.snake = [
                { x: 0, y: 5 },
                { x: 1, y: 5 },
                { x: 2, y: 5 }
            ];

            const collision = game.checkCollisions();
            expect(collision).toBe(false);
        });
    });
});

// Integration tests for complete self-collision gameplay scenario
describe('Integration: Self Collision Gameplay Flow', () => {
    let game;

    beforeEach(() => {
        setupTestEnvironment();
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    test('should grow snake to 4+ segments and detect self-collision (full scenario)', () => {
        // Step 1: Start with initial snake (3 segments)
        expect(game.snake.length).toBe(3);

        // Step 2: Grow snake by consuming food (simulate)
        game.snake.unshift({ x: game.snake[0].x + 1, y: game.snake[0].y });
        expect(game.snake.length).toBe(4);

        // At this point snake can self-collide

        // Step 3: Simulate collision by setting head position equal to body segment
        const originalHead = { ...game.snake[0] };
        game.snake[game.snake.length - 1] = { ...originalHead };

        // Step 4: Verify game over
        game.state = GameState.PLAYING;
        const collision = game.checkCollisions();

        expect(collision).toBe(true);

        if (collision) {
            game.gameOver();
        }

        expect(game.state).toBe(GameState.GAME_OVER);
    });

    test('should save high score when game ends due to self-collision', () => {
        game.score = 100;
        game.highScore = 50;
        game.state = GameState.PLAYING;

        // Set up collision
        game.snake = [
            { x: 5, y: 5 },
            { x: 5, y: 5 },
            { x: 4, y: 5 }
        ];

        if (game.checkCollisions()) {
            game.gameOver();
        }

        expect(game.highScore).toBe(100);
        expect(window.localStorage.setItem).toHaveBeenCalledWith('snakeHighScore', '100');
    });

    test('should display game over message after self-collision', () => {
        game.score = 50;
        game.state = GameState.PLAYING;

        game.snake = [
            { x: 5, y: 5 },
            { x: 5, y: 5 },
            { x: 4, y: 5 }
        ];

        if (game.checkCollisions()) {
            game.gameOver();
        }

        const statusElement = document.getElementById('game-status');
        expect(statusElement.textContent).toContain('Game Over');
        expect(statusElement.textContent).toContain('50');
    });
});
