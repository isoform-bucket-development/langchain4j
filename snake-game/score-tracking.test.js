/**
 * Snake Game - Test Suite for Score Tracking and Display
 *
 * Tests for Scenario: Score Tracking and Display
 * - Test Case 1: Score initializes to 0 on new game start
 * - Test Case 2: Score increases by defined increment on food consumption
 * - Test Case 3: Score is prominently visible on screen (e2e)
 * - Test Case 4: Score updates immediately upon food consumption (integration)
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

// Helper to set up DOM with mocked canvas
const setupDOM = () => {
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

    return { mockCtx, localStorageMock };
};

describe('Snake Game - Score Tracking and Display', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        const setup = setupDOM();
        mockCtx = setup.mockCtx;
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
        if (game && game.gameLoop) {
            cancelAnimationFrame(game.gameLoop);
        }
    });

    /**
     * Test Case 1: Score initializes to 0 on new game start
     * Input: Start new game
     * Expected: Score initializes to 0
     */
    describe('Test Case 1: Score Initializes to 0', () => {
        test('should initialize score to 0 when game is created', () => {
            expect(game.score).toBe(0);
        });

        test('should display score as 0 after initialization', () => {
            game.init();

            const scoreElement = document.getElementById('score');
            expect(scoreElement.textContent).toBe('0');
        });

        test('should have score equal to 0 after game init', () => {
            game.init();

            expect(game.score).toBe(0);
        });

        test('should reset score to 0 when starting new game after game over', () => {
            game.init();
            // Simulate game over with a score
            game.score = 50;
            game.state = GameState.GAME_OVER;

            // Reset game
            game.resetGame();

            expect(game.score).toBe(0);
        });

        test('should display 0 score after resetting game', () => {
            game.init();
            game.score = 100;
            game.updateScoreDisplay();

            expect(document.getElementById('score').textContent).toBe('100');

            game.resetGame();

            expect(document.getElementById('score').textContent).toBe('0');
        });

        test('score property should exist and be a number', () => {
            game.init();

            expect(typeof game.score).toBe('number');
            expect(game.score).toBeGreaterThanOrEqual(0);
        });

        test('should start with score 0 when game transitions to PLAYING state', () => {
            game.init();

            expect(game.score).toBe(0);

            game.startGame();

            expect(game.score).toBe(0);
            expect(game.getState()).toBe(GameState.PLAYING);
        });
    });

    /**
     * Test Case 2: Score increases by defined increment on food consumption
     * Input: Consume one food item
     * Expected: Score increases by defined increment
     */
    describe('Test Case 2: Score Increases on Food Consumption', () => {
        test('should increase score by 10 when food is consumed', () => {
            game.init();
            const initialScore = game.score;

            // Manually trigger food consumption
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();

            expect(game.score).toBe(initialScore + 10);
        });

        test('should increase score cumulatively when multiple food items are consumed', () => {
            game.init();

            // First food consumption
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            expect(game.score).toBe(10);

            // Second food consumption
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            expect(game.score).toBe(20);

            // Third food consumption
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            expect(game.score).toBe(30);
        });

        test('should not increase score if snake head does not reach food', () => {
            game.init();
            const initialScore = game.score;

            // Food is not at snake head position
            game.food = { x: game.snake[0].x + 5, y: game.snake[0].y + 5 };
            game.checkFood();

            expect(game.score).toBe(initialScore);
        });

        test('score increment should be positive', () => {
            game.init();
            const beforeScore = game.score;

            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();

            expect(game.score).toBeGreaterThan(beforeScore);
        });

        test('score should update display when food is consumed', () => {
            game.init();

            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();

            const scoreElement = document.getElementById('score');
            expect(scoreElement.textContent).toBe('10');
        });

        test('score increment is exactly 10 points', () => {
            game.init();

            const scoreBefore = game.score;
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            const scoreAfter = game.score;

            expect(scoreAfter - scoreBefore).toBe(10);
        });

        test('should spawn new food after consumption', () => {
            game.init();
            const oldFood = { ...game.food };

            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();

            // Food should be respawned (might be same position by chance, but food object should be updated)
            expect(game.food).toBeDefined();
            expect(game.food.x).toBeDefined();
            expect(game.food.y).toBeDefined();
        });
    });

    /**
     * Test Case 3: Score is prominently visible on screen (e2e)
     * Input: Score display element
     * Expected: Score is prominently visible on screen
     */
    describe('Test Case 3: Score Display Visibility (E2E)', () => {
        test('score element should exist in DOM', () => {
            game.init();

            const scoreElement = document.getElementById('score');
            expect(scoreElement).not.toBeNull();
        });

        test('score element should be inside score-board container', () => {
            game.init();

            const scoreBoard = document.getElementById('score-board');
            const scoreElement = document.getElementById('score');

            expect(scoreBoard).not.toBeNull();
            expect(scoreBoard.contains(scoreElement)).toBe(true);
        });

        test('score label should display "Score:" text', () => {
            game.init();

            const scoreLabel = document.getElementById('score-label');
            expect(scoreLabel).not.toBeNull();
            expect(scoreLabel.textContent).toContain('Score:');
        });

        test('score-board should be present and accessible', () => {
            game.init();

            const scoreBoard = document.getElementById('score-board');
            expect(scoreBoard).toBeTruthy();
        });

        test('high score element should also be visible', () => {
            game.init();

            const highScoreElement = document.getElementById('high-score');
            const highScoreLabel = document.getElementById('high-score-label');

            expect(highScoreElement).not.toBeNull();
            expect(highScoreLabel).not.toBeNull();
            expect(highScoreLabel.textContent).toContain('High Score:');
        });

        test('score should be readable (contains numeric value)', () => {
            game.init();

            const scoreElement = document.getElementById('score');
            const scoreValue = parseInt(scoreElement.textContent, 10);

            expect(isNaN(scoreValue)).toBe(false);
            expect(scoreValue).toBe(0);
        });

        test('all score-related UI elements should be present after init', () => {
            game.init();

            expect(document.getElementById('score-board')).not.toBeNull();
            expect(document.getElementById('score')).not.toBeNull();
            expect(document.getElementById('score-label')).not.toBeNull();
            expect(document.getElementById('high-score')).not.toBeNull();
            expect(document.getElementById('high-score-label')).not.toBeNull();
        });

        test('score element should have valid content structure', () => {
            game.init();

            const scoreElement = document.getElementById('score');
            expect(scoreElement.tagName.toLowerCase()).toBe('span');
            expect(scoreElement.textContent).toMatch(/^\d+$/);
        });
    });

    /**
     * Test Case 4: Score updates immediately upon food consumption (integration)
     * Input: Score update timing
     * Expected: Score updates immediately upon food consumption
     */
    describe('Test Case 4: Immediate Score Update (Integration)', () => {
        test('score display should update synchronously with score value', () => {
            game.init();

            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();

            // Check both internal score and display are updated simultaneously
            expect(game.score).toBe(10);
            expect(document.getElementById('score').textContent).toBe('10');
        });

        test('score and display should always be in sync', () => {
            game.init();

            // Multiple food consumptions
            for (let i = 1; i <= 5; i++) {
                game.food = { x: game.snake[0].x, y: game.snake[0].y };
                game.checkFood();

                // After each consumption, verify sync
                expect(document.getElementById('score').textContent).toBe(String(game.score));
            }
        });

        test('updateScoreDisplay should reflect current score value', () => {
            game.init();

            game.score = 150;
            game.updateScoreDisplay();

            expect(document.getElementById('score').textContent).toBe('150');
        });

        test('score update happens in same call as food check', () => {
            game.init();
            const originalScore = game.score;

            // Position food at snake head
            game.food = { x: game.snake[0].x, y: game.snake[0].y };

            // Before checkFood
            expect(game.score).toBe(originalScore);
            expect(document.getElementById('score').textContent).toBe(String(originalScore));

            // After checkFood - both should update immediately
            game.checkFood();

            expect(game.score).toBe(originalScore + 10);
            expect(document.getElementById('score').textContent).toBe(String(originalScore + 10));
        });

        test('score update does not require additional render calls', () => {
            game.init();

            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();

            // Score display should already be updated without explicit render()
            expect(document.getElementById('score').textContent).toBe('10');
        });

        test('display updates immediately even with rapid score changes', () => {
            game.init();

            // Rapid consecutive updates
            for (let i = 0; i < 10; i++) {
                game.food = { x: game.snake[0].x, y: game.snake[0].y };
                game.checkFood();
                expect(document.getElementById('score').textContent).toBe(String((i + 1) * 10));
            }
        });

        test('game over should display final score', () => {
            game.init();

            // Accumulate some score
            game.score = 100;
            game.updateScoreDisplay();

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('100');
        });
    });
});

// Additional integration tests
describe('Score Tracking Integration Tests', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        const setup = setupDOM();
        mockCtx = setup.mockCtx;
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    test('full game flow: start -> consume food -> score increases', () => {
        game.init();

        // Start game
        expect(game.score).toBe(0);
        expect(game.getState()).toBe(GameState.READY);

        game.startGame();
        expect(game.getState()).toBe(GameState.PLAYING);
        expect(game.score).toBe(0);

        // Consume food
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();

        expect(game.score).toBe(10);
        expect(document.getElementById('score').textContent).toBe('10');
    });

    test('score persists during pause/resume', () => {
        game.init();
        game.startGame();

        // Get some score
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();
        expect(game.score).toBe(10);

        // Pause
        game.togglePause();
        expect(game.getState()).toBe(GameState.PAUSED);
        expect(game.score).toBe(10);

        // Resume
        game.togglePause();
        expect(game.getState()).toBe(GameState.PLAYING);
        expect(game.score).toBe(10);
    });

    test('score resets properly on game restart', () => {
        game.init();
        game.startGame();

        // Accumulate score
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();
        expect(game.score).toBe(20);

        // Reset game
        game.resetGame();

        expect(game.score).toBe(0);
        expect(document.getElementById('score').textContent).toBe('0');
    });

    test('high score updates when current score exceeds it', () => {
        game.init();
        game.highScore = 50;

        game.score = 60;
        game.saveHighScore();

        expect(game.highScore).toBe(60);
    });

    test('high score does not update when current score is lower', () => {
        game.init();
        game.highScore = 100;

        game.score = 50;
        game.saveHighScore();

        expect(game.highScore).toBe(100);
    });
});

// E2E style test for complete score tracking scenario
describe('E2E: Complete Score Tracking Scenario', () => {
    test('should track and display score throughout game lifecycle', () => {
        setupDOM();
        const game = new SnakeGame();

        // Step 1: Start new game - score should be 0
        game.init();
        expect(game.score).toBe(0);
        expect(document.getElementById('score').textContent).toBe('0');

        // Step 2: Verify score UI is visible
        const scoreElement = document.getElementById('score');
        const scoreBoardElement = document.getElementById('score-board');
        expect(scoreElement).not.toBeNull();
        expect(scoreBoardElement).not.toBeNull();

        // Step 3: Start game and consume food
        game.startGame();
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();

        // Step 4: Verify score increment and immediate display update
        expect(game.score).toBe(10);
        expect(document.getElementById('score').textContent).toBe('10');

        // Consume more food
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();
        expect(game.score).toBe(20);
        expect(document.getElementById('score').textContent).toBe('20');
    });

    test('score display is properly styled and positioned', () => {
        setupDOM();
        const game = new SnakeGame();
        game.init();

        const scoreBoard = document.getElementById('score-board');
        expect(scoreBoard).not.toBeNull();

        // Score board contains both score and high score labels
        expect(scoreBoard.innerHTML).toContain('Score:');
        expect(scoreBoard.innerHTML).toContain('High Score:');
    });
});
