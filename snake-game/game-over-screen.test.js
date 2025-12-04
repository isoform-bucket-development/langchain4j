/**
 * Snake Game - Test Suite for Game Over Screen Display
 *
 * Tests for Scenario: Game Over Screen Display
 * - Test Case 1: Game over screen/message is displayed when game over is triggered
 * - Test Case 2: Final score matches last playing score on game over
 * - Test Case 3: Screen includes 'Game Over' text and score
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

describe('Snake Game - Game Over Screen Display', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        const setup = setupDOM();
        mockCtx = setup.mockCtx;
        game = new SnakeGame();
        // Mock requestAnimationFrame and cancelAnimationFrame
        jest.spyOn(window, 'requestAnimationFrame').mockImplementation(cb => {
            return setTimeout(cb, 16);
        });
        jest.spyOn(window, 'cancelAnimationFrame').mockImplementation(id => {
            clearTimeout(id);
        });
    });

    afterEach(() => {
        jest.clearAllMocks();
        jest.restoreAllMocks();
        if (game && game.gameLoop) {
            cancelAnimationFrame(game.gameLoop);
        }
    });

    /**
     * Test Case 1: Game over screen/message is displayed (E2E)
     * Input: Game over triggered
     * Expected: Game over screen/message is displayed
     */
    describe('Test Case 1: Game Over Screen Display (E2E)', () => {
        test('should display game over message when gameOver() is called', () => {
            game.init();
            game.startGame();

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
        });

        test('should change game state to GAME_OVER when game ends', () => {
            game.init();
            game.startGame();

            game.gameOver();

            expect(game.state).toBe(GameState.GAME_OVER);
            expect(game.getState()).toBe(GameState.GAME_OVER);
        });

        test('should display game over message on wall collision', () => {
            game.init();
            game.startGame();

            // Move snake head out of bounds (wall collision)
            game.snake[0] = { x: -1, y: 0 };
            const hasCollision = game.checkCollisions();

            expect(hasCollision).toBe(true);

            // Trigger game over as the game loop would
            if (hasCollision) {
                game.gameOver();
            }

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
            expect(game.state).toBe(GameState.GAME_OVER);
        });

        test('should display game over message on self collision', () => {
            game.init();
            game.startGame();

            // Set up snake in a self-collision state
            // Snake head at position colliding with body
            game.snake = [
                { x: 5, y: 5 },
                { x: 5, y: 4 },
                { x: 5, y: 3 },
                { x: 5, y: 5 }  // Body segment at same position as head
            ];

            const hasCollision = game.checkCollisions();
            expect(hasCollision).toBe(true);

            if (hasCollision) {
                game.gameOver();
            }

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
        });

        test('game status element should be visible and accessible', () => {
            game.init();

            const statusElement = document.getElementById('game-status');
            expect(statusElement).not.toBeNull();
            expect(statusElement).toBeTruthy();
        });

        test('should stop game loop on game over', () => {
            game.init();
            game.startGame();
            const gameLoopId = game.gameLoop;

            game.gameOver();

            expect(cancelAnimationFrame).toHaveBeenCalled();
            expect(game.state).toBe(GameState.GAME_OVER);
        });

        test('game over message should provide restart instruction', () => {
            game.init();
            game.startGame();

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent.toLowerCase()).toContain('restart');
        });
    });

    /**
     * Test Case 2: Final score matches last playing score (Integration)
     * Input: Check final score on game over
     * Expected: Final score matches last playing score
     */
    describe('Test Case 2: Final Score Accuracy (Integration)', () => {
        test('should display correct score (0) on game over with no food consumed', () => {
            game.init();
            game.startGame();
            expect(game.score).toBe(0);

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Score: 0');
        });

        test('should display correct score (10) after eating one food item', () => {
            game.init();
            game.startGame();

            // Eat one food
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            expect(game.score).toBe(10);

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Score: 10');
        });

        test('should display correct score (50) after eating five food items', () => {
            game.init();
            game.startGame();

            // Eat five food items
            for (let i = 0; i < 5; i++) {
                game.food = { x: game.snake[0].x, y: game.snake[0].y };
                game.checkFood();
            }
            expect(game.score).toBe(50);

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Score: 50');
        });

        test('should display correct score (100) after eating ten food items', () => {
            game.init();
            game.startGame();

            // Eat ten food items
            for (let i = 0; i < 10; i++) {
                game.food = { x: game.snake[0].x, y: game.snake[0].y };
                game.checkFood();
            }
            expect(game.score).toBe(100);

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Score: 100');
        });

        test('score in game over message should match internal score property', () => {
            game.init();
            game.startGame();

            // Random number of food consumptions
            const foodCount = 7;
            for (let i = 0; i < foodCount; i++) {
                game.food = { x: game.snake[0].x, y: game.snake[0].y };
                game.checkFood();
            }

            const scoreBeforeGameOver = game.score;
            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain(`Score: ${scoreBeforeGameOver}`);
        });

        test('score display element should retain last score on game over', () => {
            game.init();
            game.startGame();

            // Consume food to get score
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();

            const finalScore = game.score;
            expect(finalScore).toBe(30);

            game.gameOver();

            // Score display should still show 30
            const scoreElement = document.getElementById('score');
            expect(scoreElement.textContent).toBe('30');
        });

        test('high score should be saved on game over if current score is higher', () => {
            game.init();
            game.highScore = 20;
            game.startGame();

            // Get a higher score
            for (let i = 0; i < 5; i++) {
                game.food = { x: game.snake[0].x, y: game.snake[0].y };
                game.checkFood();
            }
            expect(game.score).toBe(50);

            game.gameOver();

            expect(game.highScore).toBe(50);
        });

        test('high score should not change if current score is lower', () => {
            game.init();
            game.highScore = 100;
            game.startGame();

            // Get a lower score
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            expect(game.score).toBe(10);

            game.gameOver();

            expect(game.highScore).toBe(100);
        });
    });

    /**
     * Test Case 3: Screen includes 'Game Over' text and score (E2E)
     * Input: Game over screen content
     * Expected: Screen includes 'Game Over' text and score
     */
    describe('Test Case 3: Game Over Screen Content (E2E)', () => {
        test('game over message should contain "Game Over" text', () => {
            game.init();
            game.startGame();

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
        });

        test('game over message should contain score value', () => {
            game.init();
            game.startGame();
            game.score = 40;

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('40');
        });

        test('game over message format should be "Game Over! Score: X"', () => {
            game.init();
            game.startGame();
            game.score = 80;

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toMatch(/Game Over.*Score.*80/i);
        });

        test('game over message should be displayed in game-status element', () => {
            game.init();
            game.startGame();

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            expect(statusElement).not.toBeNull();
            expect(statusElement.textContent.length).toBeGreaterThan(0);
        });

        test('game over should render the final game state', () => {
            game.init();
            game.startGame();

            // Mock render to verify it's called
            const renderSpy = jest.spyOn(game, 'render');

            game.gameOver();

            expect(renderSpy).toHaveBeenCalled();
        });

        test('complete game over screen verification', () => {
            game.init();
            game.startGame();

            // Play game - eat some food
            for (let i = 0; i < 3; i++) {
                game.food = { x: game.snake[0].x, y: game.snake[0].y };
                game.checkFood();
            }
            const expectedScore = game.score; // 30

            // Trigger game over
            game.gameOver();

            // Verify all components
            const statusElement = document.getElementById('game-status');
            const scoreElement = document.getElementById('score');

            // 1. Game Over text present
            expect(statusElement.textContent).toContain('Game Over');

            // 2. Score in game over message
            expect(statusElement.textContent).toContain(String(expectedScore));

            // 3. Score display still shows final score
            expect(scoreElement.textContent).toBe(String(expectedScore));

            // 4. Game state is GAME_OVER
            expect(game.state).toBe(GameState.GAME_OVER);

            // 5. Restart instructions present
            expect(statusElement.textContent.toLowerCase()).toContain('restart');
        });

        test('game over screen should show both current score and restart hint', () => {
            game.init();
            game.startGame();
            game.score = 60;

            game.gameOver();

            const statusElement = document.getElementById('game-status');
            const messageText = statusElement.textContent;

            // Should have Game Over
            expect(messageText).toContain('Game Over');
            // Should have score
            expect(messageText).toContain('60');
            // Should indicate how to restart
            expect(messageText.toLowerCase()).toMatch(/press.*key|restart/i);
        });
    });
});

// Additional Integration Tests - Full Game Flow to Game Over
describe('Game Over Screen Integration Tests', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        const setup = setupDOM();
        mockCtx = setup.mockCtx;
        game = new SnakeGame();
        jest.spyOn(window, 'requestAnimationFrame').mockImplementation(cb => setTimeout(cb, 16));
        jest.spyOn(window, 'cancelAnimationFrame').mockImplementation(id => clearTimeout(id));
    });

    afterEach(() => {
        jest.clearAllMocks();
        jest.restoreAllMocks();
    });

    test('full game flow: start -> play -> wall collision -> game over screen', () => {
        game.init();

        // Start game
        game.startGame();
        expect(game.state).toBe(GameState.PLAYING);

        // Eat some food
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();
        expect(game.score).toBe(10);

        // Simulate wall collision (head goes out of bounds)
        game.snake[0] = { x: -1, y: 5 };
        const collision = game.checkCollisions();
        expect(collision).toBe(true);

        // Game over
        game.gameOver();

        // Verify game over screen
        expect(game.state).toBe(GameState.GAME_OVER);
        const statusElement = document.getElementById('game-status');
        expect(statusElement.textContent).toContain('Game Over');
        expect(statusElement.textContent).toContain('10');
    });

    test('full game flow: start -> play -> self collision -> game over screen', () => {
        game.init();
        game.startGame();

        // Accumulate score
        for (let i = 0; i < 5; i++) {
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
        }
        expect(game.score).toBe(50);

        // Simulate self collision (snake bites itself)
        // Create a snake that collides with itself
        game.snake = [
            { x: 10, y: 10 },  // head
            { x: 9, y: 10 },
            { x: 9, y: 9 },
            { x: 10, y: 9 },
            { x: 10, y: 10 }   // tail at same position as head
        ];

        const collision = game.checkCollisions();
        expect(collision).toBe(true);

        game.gameOver();

        // Verify
        expect(game.state).toBe(GameState.GAME_OVER);
        const statusElement = document.getElementById('game-status');
        expect(statusElement.textContent).toContain('Game Over');
        expect(statusElement.textContent).toContain('50');
    });

    test('game over screen allows restart with any key press', () => {
        game.init();
        game.startGame();
        game.score = 30;
        game.gameOver();

        expect(game.state).toBe(GameState.GAME_OVER);

        // Simulate key press to restart
        const keyEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        game.handleKeyPress(keyEvent);

        // Should have reset and started new game
        expect(game.state).toBe(GameState.PLAYING);
        expect(game.score).toBe(0);
    });

    test('game over does not occur during READY state', () => {
        game.init();
        expect(game.state).toBe(GameState.READY);

        // Calling checkCollisions shouldn't cause issues
        const collision = game.checkCollisions();

        // Game should still be in READY state
        expect(game.state).toBe(GameState.READY);
    });

    test('multiple game over and restart cycles maintain correct score display', () => {
        game.init();

        // First game
        game.startGame();
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();
        expect(game.score).toBe(10);
        game.gameOver();

        let statusElement = document.getElementById('game-status');
        expect(statusElement.textContent).toContain('10');

        // Restart
        game.resetGame();
        game.startGame();
        expect(game.score).toBe(0);

        // Second game - more food
        for (let i = 0; i < 4; i++) {
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
        }
        expect(game.score).toBe(40);
        game.gameOver();

        statusElement = document.getElementById('game-status');
        expect(statusElement.textContent).toContain('40');
    });
});

// E2E Tests - Complete Scenario Validation
describe('E2E: Complete Game Over Screen Display Scenario', () => {
    test('Scenario Step 1-3: Trigger game over -> Verify screen -> Verify final score', () => {
        setupDOM();
        const game = new SnakeGame();
        jest.spyOn(window, 'requestAnimationFrame').mockImplementation(cb => setTimeout(cb, 16));
        jest.spyOn(window, 'cancelAnimationFrame').mockImplementation(id => clearTimeout(id));

        // Initialize and start
        game.init();
        game.startGame();

        // Play - accumulate score
        const foodEaten = 6;
        for (let i = 0; i < foodEaten; i++) {
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
        }
        const expectedFinalScore = foodEaten * 10; // 60
        expect(game.score).toBe(expectedFinalScore);

        // Step 1: Trigger game over (wall collision)
        game.snake[0] = { x: CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE, y: 5 }; // Out of bounds
        expect(game.checkCollisions()).toBe(true);
        game.gameOver();

        // Step 2: Verify game over screen/overlay is displayed
        const statusElement = document.getElementById('game-status');
        expect(game.state).toBe(GameState.GAME_OVER);
        expect(statusElement.textContent).toContain('Game Over');

        // Step 3: Verify final score display
        expect(statusElement.textContent).toContain(String(expectedFinalScore));

        // Additional verification: Score matches the last in-game score
        expect(game.score).toBe(expectedFinalScore);
        expect(document.getElementById('score').textContent).toBe(String(expectedFinalScore));

        jest.restoreAllMocks();
    });
});
