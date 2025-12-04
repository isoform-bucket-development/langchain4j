/**
 * Snake Game - Test Suite for Keyboard-Only Operation
 *
 * Scenario: Keyboard-Only Operation
 * UUID: 9ab82532-33ce-49ae-94d4-1212f3523776
 *
 * Tests:
 * - Test Case 1: E2E - Start game without mouse (game starts via keyboard input)
 * - Test Case 2: E2E - Complete game session without mouse (full game cycle possible with keyboard only)
 */

const { SnakeGame, CONFIG, GameState, Direction } = require('./game.js');

// Mock canvas context
const createMockContext = () => ({
    fillStyle: '',
    strokeStyle: '',
    lineWidth: 0,
    globalAlpha: 1,
    fillRect: jest.fn(),
    strokeRect: jest.fn(),
    beginPath: jest.fn(),
    arc: jest.fn(),
    fill: jest.fn(),
    stroke: jest.fn(),
    clearRect: jest.fn(),
    save: jest.fn(),
    restore: jest.fn()
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

describe('Snake Game - Keyboard-Only Operation', () => {
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
        if (game && game.gameLoop) {
            cancelAnimationFrame(game.gameLoop);
        }
    });

    /**
     * Test Case 1: Start game without mouse
     * Input: Start game without mouse
     * Expected: Game starts via keyboard input
     *
     * This test verifies:
     * - Game can be initialized and waiting for keyboard input
     * - Any keyboard key starts the game
     * - No mouse interaction is required to start
     */
    describe('Test Case 1: Start Game Without Mouse', () => {
        test('should display "Press any key to start" message initially', () => {
            game.init();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Press any key to start');
            expect(game.state).toBe(GameState.READY);
        });

        test('should start game when Enter key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when Space key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: ' ' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when ArrowUp key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when ArrowDown key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when ArrowLeft key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'ArrowLeft' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when ArrowRight key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when W key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'w' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when A key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'a' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when S key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 's' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when D key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'd' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game when any letter key is pressed', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'x' });
            game.handleKeyPress(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should start game via document keydown event listener', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            document.dispatchEvent(event);

            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should initialize game board and snake without mouse interaction', () => {
            game.init();

            // Verify game is properly initialized
            expect(game.canvas).not.toBeNull();
            expect(game.ctx).not.toBeNull();
            expect(game.snake.length).toBe(3);
            expect(game.food).not.toBeNull();
            expect(game.initialized).toBe(true);
        });

        test('should not require any mouse click event to start', () => {
            game.init();

            // Verify we can start purely with keyboard
            expect(game.state).toBe(GameState.READY);

            // No mouse events dispatched, only keyboard
            const keyEvent = new KeyboardEvent('keydown', { key: 'Enter' });
            game.handleKeyPress(keyEvent);

            expect(game.state).toBe(GameState.PLAYING);
        });
    });

    /**
     * Test Case 2: Complete game session without mouse
     * Input: Complete game session without mouse
     * Expected: Full game cycle possible with keyboard only
     *
     * This test verifies:
     * - Game can be started with keyboard
     * - Snake can be controlled with arrow keys or WASD
     * - Game can be paused and resumed with keyboard
     * - Game can be restarted after game over with keyboard
     */
    describe('Test Case 2: Complete Game Session Without Mouse', () => {
        test('should complete full game cycle: start -> play -> game over -> restart', () => {
            game.init();

            // Step 1: Start game with keyboard
            expect(game.state).toBe(GameState.READY);
            game.handleKeyPress({ key: 'Enter' });
            expect(game.state).toBe(GameState.PLAYING);

            // Step 2: Control snake with arrow keys
            game.handleKeyPress({ key: 'ArrowUp' });
            expect(game.nextDirection).toBe(Direction.UP);

            game.direction = Direction.UP;
            game.handleKeyPress({ key: 'ArrowLeft' });
            expect(game.nextDirection).toBe(Direction.LEFT);

            // Step 3: Simulate game over
            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            // Step 4: Restart with keyboard
            game.handleKeyPress({ key: 'Enter' });
            expect(game.state).toBe(GameState.PLAYING);
            expect(game.score).toBe(0);
            expect(game.snake.length).toBe(3);
        });

        test('should control snake using arrow keys during gameplay', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game
            expect(game.state).toBe(GameState.PLAYING);

            // Initial direction is RIGHT
            expect(game.direction).toBe(Direction.RIGHT);

            // Change to UP
            game.handleKeyPress({ key: 'ArrowUp' });
            expect(game.nextDirection).toBe(Direction.UP);

            // Apply direction change
            game.direction = game.nextDirection;

            // Change to LEFT (perpendicular to UP)
            game.handleKeyPress({ key: 'ArrowLeft' });
            expect(game.nextDirection).toBe(Direction.LEFT);

            // Apply direction change
            game.direction = game.nextDirection;

            // Change to DOWN (perpendicular to LEFT)
            game.handleKeyPress({ key: 'ArrowDown' });
            expect(game.nextDirection).toBe(Direction.DOWN);

            // Apply direction change
            game.direction = game.nextDirection;

            // Change to RIGHT (perpendicular to DOWN)
            game.handleKeyPress({ key: 'ArrowRight' });
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should control snake using WASD keys during gameplay', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game
            expect(game.state).toBe(GameState.PLAYING);

            // Initial direction is RIGHT
            expect(game.direction).toBe(Direction.RIGHT);

            // Change to UP with W
            game.handleKeyPress({ key: 'w' });
            expect(game.nextDirection).toBe(Direction.UP);

            // Apply direction change
            game.direction = game.nextDirection;

            // Change to LEFT with A (perpendicular to UP)
            game.handleKeyPress({ key: 'a' });
            expect(game.nextDirection).toBe(Direction.LEFT);

            // Apply direction change
            game.direction = game.nextDirection;

            // Change to DOWN with S (perpendicular to LEFT)
            game.handleKeyPress({ key: 's' });
            expect(game.nextDirection).toBe(Direction.DOWN);

            // Apply direction change
            game.direction = game.nextDirection;

            // Change to RIGHT with D (perpendicular to DOWN)
            game.handleKeyPress({ key: 'd' });
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should pause and resume game using Space key', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game
            expect(game.state).toBe(GameState.PLAYING);

            // Pause game with Space
            game.handleKeyPress({ key: ' ' });
            expect(game.state).toBe(GameState.PAUSED);

            // Resume game with Space
            game.handleKeyPress({ key: ' ' });
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should restart game after game over using any key', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game

            // Simulate game over
            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            // Restart with Enter key
            game.handleKeyPress({ key: 'Enter' });
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should restart game after game over using arrow keys', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game

            // Simulate game over
            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            // Restart with arrow key
            game.handleKeyPress({ key: 'ArrowRight' });
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should restart game after game over using WASD keys', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game

            // Simulate game over
            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            // Restart with WASD key
            game.handleKeyPress({ key: 'w' });
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should accumulate score during keyboard-only gameplay', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game

            // Position food at snake head location to simulate eating
            game.food = { x: game.snake[0].x + 1, y: game.snake[0].y };

            // Move snake to eat food
            game.direction = Direction.RIGHT;
            game.moveSnake();
            game.checkFood();

            expect(game.score).toBe(10);
        });

        test('should handle multiple game sessions with keyboard only', () => {
            game.init();

            // First game session
            game.handleKeyPress({ key: 'Enter' });
            expect(game.state).toBe(GameState.PLAYING);
            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            // Second game session
            game.handleKeyPress({ key: 'Enter' });
            expect(game.state).toBe(GameState.PLAYING);
            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            // Third game session
            game.handleKeyPress({ key: 'Enter' });
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should support rapid direction changes with keyboard', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game

            const startTime = performance.now();

            // Rapid direction changes
            game.direction = Direction.RIGHT;
            game.handleKeyPress({ key: 'ArrowUp' });
            game.direction = Direction.UP;
            game.handleKeyPress({ key: 'ArrowLeft' });
            game.direction = Direction.LEFT;
            game.handleKeyPress({ key: 'ArrowDown' });
            game.direction = Direction.DOWN;
            game.handleKeyPress({ key: 'ArrowRight' });

            const endTime = performance.now();

            // All direction changes should be processed within 100ms total
            expect(endTime - startTime).toBeLessThan(100);
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should prevent opposite direction changes (keyboard safety)', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' }); // Start game

            // Try to reverse direction (should be blocked)
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;
            game.handleKeyPress({ key: 'ArrowLeft' }); // Try opposite
            expect(game.nextDirection).toBe(Direction.RIGHT); // Should not change

            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;
            game.handleKeyPress({ key: 'ArrowDown' }); // Try opposite
            expect(game.nextDirection).toBe(Direction.UP); // Should not change
        });

        test('should maintain game state integrity during keyboard-only operation', () => {
            game.init();

            // Start game
            game.handleKeyPress({ key: 'Enter' });
            expect(game.state).toBe(GameState.PLAYING);
            expect(game.score).toBe(0);
            expect(game.snake.length).toBe(3);

            // Play for a bit
            game.handleKeyPress({ key: 'ArrowUp' });
            game.direction = game.nextDirection;
            game.moveSnake();

            // Pause
            game.handleKeyPress({ key: ' ' });
            expect(game.state).toBe(GameState.PAUSED);

            // Resume
            game.handleKeyPress({ key: ' ' });
            expect(game.state).toBe(GameState.PLAYING);

            // Game over
            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            // Restart
            game.handleKeyPress({ key: 'Enter' });
            expect(game.state).toBe(GameState.PLAYING);
            expect(game.score).toBe(0);
            expect(game.snake.length).toBe(3);
        });
    });

    /**
     * Integration tests for keyboard-only operation
     */
    describe('Integration: Keyboard-Only Full Workflow', () => {
        test('E2E: complete game workflow using only keyboard events dispatched to document', () => {
            game.init();

            // Start game via document event
            const startEvent = new KeyboardEvent('keydown', { key: 'Enter' });
            document.dispatchEvent(startEvent);
            expect(game.state).toBe(GameState.PLAYING);

            // Control snake via document events
            const upEvent = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            document.dispatchEvent(upEvent);
            expect(game.nextDirection).toBe(Direction.UP);

            // Pause via document event
            const pauseEvent = new KeyboardEvent('keydown', { key: ' ' });
            document.dispatchEvent(pauseEvent);
            expect(game.state).toBe(GameState.PAUSED);

            // Resume via document event
            const resumeEvent = new KeyboardEvent('keydown', { key: ' ' });
            document.dispatchEvent(resumeEvent);
            expect(game.state).toBe(GameState.PLAYING);

            // Simulate game over
            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            // Restart via document event
            const restartEvent = new KeyboardEvent('keydown', { key: 'r' });
            document.dispatchEvent(restartEvent);
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('E2E: verify no mouse events are required for any game action', () => {
            game.init();

            // Verify initial state (no mouse needed)
            expect(game.state).toBe(GameState.READY);
            expect(game.initialized).toBe(true);

            // All game transitions via keyboard only
            game.handleKeyPress({ key: 'Enter' }); // Start
            expect(game.state).toBe(GameState.PLAYING);

            game.handleKeyPress({ key: 'ArrowUp' }); // Control
            expect(game.nextDirection).toBe(Direction.UP);

            game.handleKeyPress({ key: ' ' }); // Pause
            expect(game.state).toBe(GameState.PAUSED);

            game.handleKeyPress({ key: ' ' }); // Resume
            expect(game.state).toBe(GameState.PLAYING);

            game.gameOver();
            expect(game.state).toBe(GameState.GAME_OVER);

            game.handleKeyPress({ key: 'Enter' }); // Restart
            expect(game.state).toBe(GameState.PLAYING);

            // All operations completed without any mouse interaction
        });

        test('should handle keyboard controls setup during initialization', () => {
            const setupControlsSpy = jest.spyOn(SnakeGame.prototype, 'setupControls');

            const newGame = new SnakeGame();
            newGame.init();

            expect(setupControlsSpy).toHaveBeenCalled();
            setupControlsSpy.mockRestore();
        });

        test('keyboard controls should respond within 100ms (performance)', () => {
            game.init();
            game.handleKeyPress({ key: 'Enter' });

            const responseTimes = [];
            const keys = ['ArrowUp', 'ArrowLeft', 'ArrowDown', 'ArrowRight'];
            const setupDirs = [Direction.RIGHT, Direction.UP, Direction.LEFT, Direction.DOWN];

            for (let i = 0; i < keys.length; i++) {
                game.direction = setupDirs[i];

                const start = performance.now();
                game.handleKeyPress({ key: keys[i] });
                const end = performance.now();

                responseTimes.push(end - start);
            }

            // All response times should be under 100ms
            responseTimes.forEach(time => {
                expect(time).toBeLessThan(100);
            });

            // Average should be well under 100ms
            const avg = responseTimes.reduce((a, b) => a + b, 0) / responseTimes.length;
            expect(avg).toBeLessThan(100);
        });
    });
});

/**
 * E2E Test Suite: Keyboard-Only Game Session
 */
describe('E2E: Full Keyboard-Only Game Session', () => {
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

    test('complete keyboard-only game session from start to restart', () => {
        // Initialize
        game.init();
        expect(game.state).toBe(GameState.READY);

        // Phase 1: Start with keyboard
        const startEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        document.dispatchEvent(startEvent);
        expect(game.state).toBe(GameState.PLAYING);

        // Phase 2: Play using keyboard controls
        // Move up
        const upEvent = new KeyboardEvent('keydown', { key: 'ArrowUp' });
        document.dispatchEvent(upEvent);
        expect(game.nextDirection).toBe(Direction.UP);

        // Apply and move left
        game.direction = game.nextDirection;
        const leftEvent = new KeyboardEvent('keydown', { key: 'ArrowLeft' });
        document.dispatchEvent(leftEvent);
        expect(game.nextDirection).toBe(Direction.LEFT);

        // Test WASD
        game.direction = game.nextDirection;
        const sEvent = new KeyboardEvent('keydown', { key: 's' });
        document.dispatchEvent(sEvent);
        expect(game.nextDirection).toBe(Direction.DOWN);

        // Phase 3: Pause and resume with keyboard
        const pauseEvent = new KeyboardEvent('keydown', { key: ' ' });
        document.dispatchEvent(pauseEvent);
        expect(game.state).toBe(GameState.PAUSED);

        const resumeEvent = new KeyboardEvent('keydown', { key: ' ' });
        document.dispatchEvent(resumeEvent);
        expect(game.state).toBe(GameState.PLAYING);

        // Phase 4: Game over and restart with keyboard
        game.gameOver();
        expect(game.state).toBe(GameState.GAME_OVER);

        const restartEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        document.dispatchEvent(restartEvent);
        expect(game.state).toBe(GameState.PLAYING);
        expect(game.score).toBe(0);
        expect(game.snake.length).toBe(3);
    });

    test('verify all game states are reachable via keyboard', () => {
        game.init();

        // READY state (initial)
        expect(game.state).toBe(GameState.READY);

        // READY -> PLAYING (keyboard)
        game.handleKeyPress({ key: 'Enter' });
        expect(game.state).toBe(GameState.PLAYING);

        // PLAYING -> PAUSED (keyboard)
        game.handleKeyPress({ key: ' ' });
        expect(game.state).toBe(GameState.PAUSED);

        // PAUSED -> PLAYING (keyboard)
        game.handleKeyPress({ key: ' ' });
        expect(game.state).toBe(GameState.PLAYING);

        // PLAYING -> GAME_OVER (via game logic, triggered by collision)
        game.gameOver();
        expect(game.state).toBe(GameState.GAME_OVER);

        // GAME_OVER -> PLAYING (keyboard)
        game.handleKeyPress({ key: 'Enter' });
        expect(game.state).toBe(GameState.PLAYING);
    });

    test('no click handlers required for game operation', () => {
        game.init();

        // Verify that game can complete full lifecycle without any click
        // Start
        game.handleKeyPress({ key: 'Enter' });
        expect(game.state).toBe(GameState.PLAYING);

        // Play
        game.handleKeyPress({ key: 'ArrowUp' });
        game.handleKeyPress({ key: ' ' }); // Pause
        game.handleKeyPress({ key: ' ' }); // Resume

        // End
        game.gameOver();

        // Restart
        game.handleKeyPress({ key: 'Enter' });

        // Full game cycle completed - no mouse/click events used
        expect(game.state).toBe(GameState.PLAYING);
    });
});
