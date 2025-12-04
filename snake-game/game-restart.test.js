/**
 * Snake Game - Test Suite for Game Restart Mechanism
 *
 * Tests for Scenario: Game Restart Mechanism
 * - Test Case 1: E2E test - Press restart key/button after game over
 * - Test Case 2: Unit test - Score resets to 0
 * - Test Case 3: Unit test - Snake resets to initial length and position
 * - Test Case 4: Unit test - New food item generated
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

// Helper function to set up DOM environment
const setupDOM = () => {
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
};

// Helper function to mock canvas and localStorage
const setupMocks = (mockCtx) => {
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
};

// Helper to simulate game over state
const simulateGameOver = (game, score = 50) => {
    game.score = score;
    game.state = GameState.GAME_OVER;
    // Modify snake to simulate gameplay
    game.snake = [
        { x: 15, y: 10 },
        { x: 14, y: 10 },
        { x: 13, y: 10 },
        { x: 12, y: 10 },
        { x: 11, y: 10 }
    ];
};

describe('Snake Game - Game Restart Mechanism', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        jest.useFakeTimers();
        mockCtx = createMockContext();
        setupDOM();
        setupMocks(mockCtx);
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
        jest.useRealTimers();
    });

    /**
     * Test Case 1: E2E Test - Press restart key/button after game over
     * Input: Press restart key/button after game over
     * Expected: Game restarts with fresh state
     */
    describe('Test Case 1: E2E - Game Restart After Game Over', () => {
        test('should restart game when pressing any key after game over', () => {
            game.init();
            game.startGame();

            // Simulate game over
            simulateGameOver(game, 100);

            // Verify game is in game over state
            expect(game.state).toBe(GameState.GAME_OVER);

            // Press any key to restart
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            game.handleKeyPress(event);

            // Verify game restarted
            expect(game.state).toBe(GameState.PLAYING);
            expect(game.score).toBe(0);
        });

        test('should restart game when pressing arrow key after game over', () => {
            game.init();
            game.startGame();
            simulateGameOver(game);

            // Press arrow key to restart
            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should restart game when pressing space bar after game over', () => {
            game.init();
            game.startGame();
            simulateGameOver(game);

            const event = new KeyboardEvent('keydown', { key: ' ' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should display game over message before restart', () => {
            game.init();
            game.startGame();

            // Trigger game over through gameOver method
            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
            expect(statusElement.textContent).toContain('restart');
        });

        test('should have fresh game state after restart from game over', () => {
            game.init();
            game.startGame();

            // Simulate played game with accumulated score
            game.score = 150;
            game.speed = CONFIG.MIN_SPEED; // Speed increased due to score
            game.direction = Direction.UP;
            game.snake = [
                { x: 10, y: 5 },
                { x: 10, y: 6 },
                { x: 10, y: 7 },
                { x: 10, y: 8 },
                { x: 10, y: 9 },
                { x: 10, y: 10 }
            ];

            // Game over
            game.state = GameState.GAME_OVER;

            // Restart
            const event = new KeyboardEvent('keydown', { key: 'r' });
            game.handleKeyPress(event);

            // Verify fresh state
            expect(game.score).toBe(0);
            expect(game.speed).toBe(CONFIG.INITIAL_SPEED);
            expect(game.direction).toEqual(Direction.RIGHT);
            expect(game.snake.length).toBe(3);
        });

        test('should call resetGame followed by startGame when restarting', () => {
            game.init();

            // Spy on methods
            const resetSpy = jest.spyOn(game, 'resetGame');
            const startSpy = jest.spyOn(game, 'startGame');

            // Set game over state
            game.state = GameState.GAME_OVER;

            // Trigger restart
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            game.handleKeyPress(event);

            expect(resetSpy).toHaveBeenCalled();
            expect(startSpy).toHaveBeenCalled();
        });

        test('should preserve high score after restart', () => {
            game.init();
            game.highScore = 200;

            game.startGame();
            simulateGameOver(game, 50);

            // Restart
            game.resetGame();
            game.startGame();

            expect(game.highScore).toBe(200);
        });

        test('should update high score before restart if new high score achieved', () => {
            game.init();
            game.highScore = 100;

            game.score = 150;
            game.gameOver();

            expect(game.highScore).toBe(150);

            // Restart
            game.resetGame();
            game.startGame();

            // High score should be preserved
            expect(game.highScore).toBe(150);
        });
    });

    /**
     * Test Case 2: Unit Test - Score resets to 0
     * Input: Check score after restart
     * Expected: Score resets to 0
     */
    describe('Test Case 2: Score Resets to 0', () => {
        test('should reset score to 0 after calling resetGame', () => {
            game.init();
            game.score = 100;

            game.resetGame();

            expect(game.score).toBe(0);
        });

        test('should reset score to 0 regardless of previous score value', () => {
            game.init();

            // Test with various scores
            const testScores = [10, 50, 100, 500, 1000, 9999];

            testScores.forEach(score => {
                game.score = score;
                game.resetGame();
                expect(game.score).toBe(0);
            });
        });

        test('should update score display to 0 after reset', () => {
            game.init();
            game.score = 250;
            game.updateScoreDisplay();

            expect(document.getElementById('score').textContent).toBe('250');

            game.resetGame();

            expect(document.getElementById('score').textContent).toBe('0');
        });

        test('should reset score but preserve high score', () => {
            game.init();
            game.highScore = 300;
            game.score = 150;

            game.resetGame();

            expect(game.score).toBe(0);
            expect(game.highScore).toBe(300);
        });

        test('should reset score from maximum possible value', () => {
            game.init();
            game.score = Number.MAX_SAFE_INTEGER;

            game.resetGame();

            expect(game.score).toBe(0);
        });

        test('score should be 0 when game restarts from GAME_OVER state', () => {
            game.init();
            game.startGame();
            simulateGameOver(game, 500);

            // Restart
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            game.handleKeyPress(event);

            expect(game.score).toBe(0);
        });

        test('score display element should show 0 after full restart flow', () => {
            game.init();
            game.startGame();

            // Accumulate score
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            expect(game.score).toBe(10);

            // Game over and restart
            game.state = GameState.GAME_OVER;
            game.resetGame();
            game.startGame();

            const scoreDisplay = document.getElementById('score');
            expect(scoreDisplay.textContent).toBe('0');
        });
    });

    /**
     * Test Case 3: Unit Test - Snake resets to initial length and position
     * Input: Check snake after restart
     * Expected: Snake resets to initial length and position
     */
    describe('Test Case 3: Snake Resets to Initial Length and Position', () => {
        test('should reset snake to initial 3 segments', () => {
            game.init();

            // Grow the snake
            game.snake = [
                { x: 15, y: 10 },
                { x: 14, y: 10 },
                { x: 13, y: 10 },
                { x: 12, y: 10 },
                { x: 11, y: 10 },
                { x: 10, y: 10 },
                { x: 9, y: 10 }
            ];

            game.resetGame();

            expect(game.snake.length).toBe(3);
        });

        test('should reset snake head to center of board', () => {
            game.init();

            const expectedX = Math.floor(CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE / 2);
            const expectedY = Math.floor(CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE / 2);

            // Move snake to different position
            game.snake = [
                { x: 5, y: 5 },
                { x: 4, y: 5 },
                { x: 3, y: 5 }
            ];

            game.resetGame();

            expect(game.snake[0].x).toBe(expectedX);
            expect(game.snake[0].y).toBe(expectedY);
        });

        test('should reset snake to horizontal orientation', () => {
            game.init();

            // Position snake vertically
            game.snake = [
                { x: 10, y: 5 },
                { x: 10, y: 6 },
                { x: 10, y: 7 }
            ];

            game.resetGame();

            // After reset, snake should be horizontal (all same Y)
            const headY = game.snake[0].y;
            game.snake.forEach(segment => {
                expect(segment.y).toBe(headY);
            });
        });

        test('should reset snake segments to be consecutive', () => {
            game.init();
            game.resetGame();

            // Verify segments are consecutive (each segment's x is 1 less than previous)
            for (let i = 1; i < game.snake.length; i++) {
                expect(game.snake[i].x).toBe(game.snake[i - 1].x - 1);
                expect(game.snake[i].y).toBe(game.snake[i - 1].y);
            }
        });

        test('should reset direction to RIGHT', () => {
            game.init();
            game.direction = Direction.UP;
            game.nextDirection = Direction.LEFT;

            game.resetGame();

            expect(game.direction).toEqual(Direction.RIGHT);
            expect(game.nextDirection).toEqual(Direction.RIGHT);
        });

        test('should reset snake even when it filled most of the board', () => {
            game.init();

            // Create a very long snake
            const longSnake = [];
            for (let i = 0; i < 100; i++) {
                longSnake.push({ x: i % 20, y: Math.floor(i / 20) });
            }
            game.snake = longSnake;

            game.resetGame();

            expect(game.snake.length).toBe(3);
        });

        test('snake position should match initial values after restart from game over', () => {
            game.init();
            const initialSnake = JSON.parse(JSON.stringify(game.snake));

            // Play game and get game over
            game.startGame();
            simulateGameOver(game);

            // Restart
            game.resetGame();
            game.startGame();

            expect(game.snake).toEqual(initialSnake);
        });

        test('should reset snake speed to initial value', () => {
            game.init();
            game.speed = CONFIG.MIN_SPEED;

            game.resetGame();

            expect(game.speed).toBe(CONFIG.INITIAL_SPEED);
        });
    });

    /**
     * Test Case 4: Unit Test - New food item generated
     * Input: Check food after restart
     * Expected: New food item generated
     */
    describe('Test Case 4: New Food Item Generated', () => {
        test('should generate new food after reset', () => {
            game.init();
            const initialFood = { ...game.food };

            // Change food position
            game.food = { x: 0, y: 0 };

            game.resetGame();

            // Food should exist
            expect(game.food).not.toBeNull();
            expect(game.food).toBeDefined();
        });

        test('should generate food at valid position within grid', () => {
            game.init();
            game.resetGame();

            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            expect(game.food.x).toBeGreaterThanOrEqual(0);
            expect(game.food.x).toBeLessThan(gridWidth);
            expect(game.food.y).toBeGreaterThanOrEqual(0);
            expect(game.food.y).toBeLessThan(gridHeight);
        });

        test('should not spawn food on snake position after reset', () => {
            game.init();
            game.resetGame();

            const isOnSnake = game.snake.some(
                segment => segment.x === game.food.x && segment.y === game.food.y
            );

            expect(isOnSnake).toBe(false);
        });

        test('food should have x and y properties after reset', () => {
            game.init();
            game.food = null;

            game.resetGame();

            expect(game.food).toHaveProperty('x');
            expect(game.food).toHaveProperty('y');
            expect(typeof game.food.x).toBe('number');
            expect(typeof game.food.y).toBe('number');
        });

        test('food position should be regenerated on each reset', () => {
            game.init();

            // Multiple resets should call spawnFood each time
            const spawnFoodSpy = jest.spyOn(game, 'spawnFood');

            game.resetGame();
            game.resetGame();
            game.resetGame();

            // spawnFood is called during resetGame
            expect(spawnFoodSpy).toHaveBeenCalledTimes(3);
        });

        test('foodEaten flag should be reset to false', () => {
            game.init();
            game.foodEaten = true;

            game.resetGame();

            expect(game.foodEaten).toBe(false);
        });

        test('food should be at different position than during previous game over', () => {
            // This test runs multiple times to statistically verify food is regenerated
            game.init();

            let foodChangedCount = 0;
            const iterations = 10;

            for (let i = 0; i < iterations; i++) {
                const previousFood = { ...game.food };
                game.resetGame();

                // Check if food position changed
                if (game.food.x !== previousFood.x || game.food.y !== previousFood.y) {
                    foodChangedCount++;
                }
            }

            // Food should change position at least some of the time
            // (It's possible to randomly get same position, but unlikely every time)
            expect(foodChangedCount).toBeGreaterThan(0);
        });

        test('food coordinates should be integers', () => {
            game.init();
            game.resetGame();

            expect(Number.isInteger(game.food.x)).toBe(true);
            expect(Number.isInteger(game.food.y)).toBe(true);
        });
    });

    /**
     * Additional comprehensive tests for game restart mechanism
     */
    describe('Additional Game Restart Tests', () => {
        test('game state should transition from GAME_OVER to READY then PLAYING on restart', () => {
            game.init();
            game.startGame();
            simulateGameOver(game);

            expect(game.state).toBe(GameState.GAME_OVER);

            // resetGame sets state to READY
            game.resetGame();
            expect(game.state).toBe(GameState.READY);

            // startGame sets state to PLAYING
            game.startGame();
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should be able to play multiple games without issues', () => {
            game.init();

            // Play 5 games
            for (let i = 0; i < 5; i++) {
                game.startGame();
                expect(game.state).toBe(GameState.PLAYING);

                // Simulate gameplay
                game.score = 50 * (i + 1);
                game.snake.push({ x: 5, y: 5 });

                // Game over
                game.state = GameState.GAME_OVER;

                // Restart
                game.resetGame();
                expect(game.score).toBe(0);
                expect(game.snake.length).toBe(3);
                expect(game.state).toBe(GameState.READY);
            }
        });

        test('should reset game loop timing on restart', () => {
            game.init();
            game.startGame();

            const oldUpdateTime = game.lastUpdateTime;

            // Advance time
            jest.advanceTimersByTime(1000);

            // Game over and restart
            game.state = GameState.GAME_OVER;
            game.resetGame();
            game.startGame();

            // lastUpdateTime should be updated to current time
            expect(game.lastUpdateTime).toBeGreaterThan(oldUpdateTime);
        });

        test('UI should be properly updated on restart', () => {
            game.init();
            game.startGame();

            game.score = 100;
            game.updateScoreDisplay();
            game.updateStatus('Playing...');

            simulateGameOver(game);

            game.resetGame();
            game.startGame();

            expect(document.getElementById('score').textContent).toBe('0');
        });

        test('should handle restart immediately after game starts', () => {
            game.init();
            game.startGame();

            // Immediately reset
            game.resetGame();

            expect(game.score).toBe(0);
            expect(game.snake.length).toBe(3);
            expect(game.state).toBe(GameState.READY);
        });
    });
});

/**
 * E2E Integration Test: Full Game Restart Flow
 */
describe('E2E Integration Test: Full Game Restart Flow', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        mockCtx = createMockContext();
        setupDOM();
        setupMocks(mockCtx);
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    test('complete game restart flow from game over', () => {
        // Initialize game
        game.init();
        expect(game.state).toBe(GameState.READY);

        // Start game
        const startEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        game.handleKeyPress(startEvent);
        expect(game.state).toBe(GameState.PLAYING);

        // Simulate gameplay - accumulate score
        game.score = 100;
        game.snake = [
            { x: 15, y: 10 },
            { x: 14, y: 10 },
            { x: 13, y: 10 },
            { x: 12, y: 10 },
            { x: 11, y: 10 }
        ];

        // Trigger game over
        game.gameOver();
        expect(game.state).toBe(GameState.GAME_OVER);

        // Verify game over message
        const statusElement = document.getElementById('game-status');
        expect(statusElement.textContent).toContain('Game Over');

        // Restart game by pressing a key
        const restartEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        game.handleKeyPress(restartEvent);

        // Verify fresh game state
        expect(game.state).toBe(GameState.PLAYING);
        expect(game.score).toBe(0);
        expect(game.snake.length).toBe(3);
        expect(game.direction).toEqual(Direction.RIGHT);
        expect(game.speed).toBe(CONFIG.INITIAL_SPEED);
        expect(game.food).not.toBeNull();

        // Verify snake is at initial position
        const expectedX = Math.floor(CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE / 2);
        const expectedY = Math.floor(CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE / 2);
        expect(game.snake[0].x).toBe(expectedX);
        expect(game.snake[0].y).toBe(expectedY);
    });

    test('restart should work with keyboard key press event dispatched to document', () => {
        game.init();

        // Start game
        const startEvent = new KeyboardEvent('keydown', { key: ' ' });
        document.dispatchEvent(startEvent);
        expect(game.state).toBe(GameState.PLAYING);

        // Game over
        game.state = GameState.GAME_OVER;
        game.score = 50;

        // Restart via document event
        const restartEvent = new KeyboardEvent('keydown', { key: 'r' });
        document.dispatchEvent(restartEvent);

        expect(game.state).toBe(GameState.PLAYING);
        expect(game.score).toBe(0);
    });
});
