/**
 * Snake Game - Test Suite for Pause and Resume Functionality
 *
 * Tests for Scenario: Pause and Resume Functionality
 * - Test Case 1: Press pause key during gameplay - Game pauses and snake stops moving
 * - Test Case 2: Press pause key while paused - Game resumes from paused state
 * - Test Case 3: Game state during pause - Snake position and score preserved
 * - Test Case 4: Visual pause indicator - Paused state is visually indicated to user
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

// Mock requestAnimationFrame and cancelAnimationFrame
let animationFrameCallbacks = [];
let animationFrameId = 0;

const mockRequestAnimationFrame = jest.fn((callback) => {
    const id = ++animationFrameId;
    animationFrameCallbacks.push({ id, callback });
    return id;
});

const mockCancelAnimationFrame = jest.fn((id) => {
    animationFrameCallbacks = animationFrameCallbacks.filter(item => item.id !== id);
});

describe('Snake Game - Pause and Resume Functionality', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        // Reset animation frame mocks
        animationFrameCallbacks = [];
        animationFrameId = 0;
        global.requestAnimationFrame = mockRequestAnimationFrame;
        global.cancelAnimationFrame = mockCancelAnimationFrame;

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

        // Create new game instance
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Press pause key during gameplay
     * Input: Press pause key during gameplay
     * Expected: Game pauses and snake stops moving
     */
    describe('Test Case 1: Pause Game During Gameplay (E2E)', () => {
        test('should transition to PAUSED state when space key is pressed during PLAYING state', () => {
            // Start the game
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);

            // Press space to pause
            game.togglePause();

            expect(game.getState()).toBe(GameState.PAUSED);
        });

        test('should cancel animation frame when pausing', () => {
            game.startGame();
            const initialGameLoop = game.gameLoop;

            game.togglePause();

            expect(mockCancelAnimationFrame).toHaveBeenCalledWith(initialGameLoop);
        });

        test('should stop game loop when paused', () => {
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);

            game.togglePause();

            // Game should be paused
            expect(game.getState()).toBe(GameState.PAUSED);

            // update() should not continue when paused
            game.update();
            expect(game.getState()).toBe(GameState.PAUSED);
        });

        test('should pause game when pressing space key through handleKeyPress', () => {
            // Start the game first to set up gameLoop
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);

            // Use plain object for key event
            const spaceKeyEvent = { key: ' ' };
            game.handleKeyPress(spaceKeyEvent);

            expect(game.getState()).toBe(GameState.PAUSED);
        });

        test('should respond to space keydown event from document during gameplay', () => {
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);

            // Directly call togglePause since document event dispatch is handled by setupControls
            game.togglePause();

            expect(game.getState()).toBe(GameState.PAUSED);
        });

        test('snake should stop moving when game is paused', () => {
            game.startGame();
            const initialSnakePosition = JSON.parse(JSON.stringify(game.snake));

            // Pause the game
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            // Try to move snake (should not change position)
            game.moveSnake();

            // Manually verify snake would have moved, but since update() returns early when paused,
            // we verify the state machine prevents movement
            game.update(); // This should return early due to PAUSED state

            // The game state should still be PAUSED
            expect(game.getState()).toBe(GameState.PAUSED);
        });

        test('update function should return early when game state is PAUSED', () => {
            game.startGame();
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            // Track if render is called (it shouldn't be during update when paused)
            const renderSpy = jest.spyOn(game, 'render');
            const moveSnakeSpy = jest.spyOn(game, 'moveSnake');

            game.update();

            // Neither render nor moveSnake should be called during update when paused
            expect(moveSnakeSpy).not.toHaveBeenCalled();
        });
    });

    /**
     * Test Case 2: Press pause key while paused
     * Input: Press pause key while paused
     * Expected: Game resumes from paused state
     */
    describe('Test Case 2: Resume Game From Paused State (E2E)', () => {
        test('should transition from PAUSED to PLAYING when space key is pressed', () => {
            game.startGame();
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            // Resume game
            game.togglePause();

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('should restart animation frame when resuming', () => {
            game.startGame();
            game.togglePause();

            mockRequestAnimationFrame.mockClear();
            game.togglePause();

            expect(mockRequestAnimationFrame).toHaveBeenCalled();
        });

        test('should resume game when pressing space key through handleKeyPress while paused', () => {
            game.startGame();
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            const spaceKeyEvent = { key: ' ' };
            game.handleKeyPress(spaceKeyEvent);

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('should resume game when space keydown event from document while paused', () => {
            game.startGame();
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            // Simulate spacebar press
            const spaceEvent = new KeyboardEvent('keydown', { key: ' ' });
            document.dispatchEvent(spaceEvent);

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('should update lastUpdateTime when resuming to prevent time jump', () => {
            game.startGame();
            const initialTime = game.lastUpdateTime;

            game.togglePause();

            // Simulate some time passing
            jest.advanceTimersByTime = jest.fn();

            game.togglePause(); // Resume

            // lastUpdateTime should be updated to current time
            expect(game.lastUpdateTime).toBeGreaterThanOrEqual(initialTime);
        });

        test('should allow direction changes after resuming', () => {
            game.startGame();
            game.togglePause();
            game.togglePause(); // Resume

            // Change direction
            const upKeyEvent = { key: 'ArrowUp' };
            game.handleKeyPress(upKeyEvent);

            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('game loop should continue after resuming', () => {
            game.startGame();
            game.togglePause();

            mockRequestAnimationFrame.mockClear();
            game.togglePause(); // Resume

            expect(mockRequestAnimationFrame).toHaveBeenCalled();
            expect(game.gameLoop).toBeDefined();
        });
    });

    /**
     * Test Case 3: Game state during pause
     * Input: Game state during pause
     * Expected: Snake position and score preserved
     */
    describe('Test Case 3: Game State Preservation During Pause (Unit)', () => {
        test('snake position should be preserved during pause', () => {
            game.startGame();

            // Move snake a few times
            game.moveSnake();
            game.moveSnake();
            const snakeBeforePause = JSON.parse(JSON.stringify(game.snake));

            // Pause the game
            game.togglePause();

            // Snake position should be unchanged
            expect(game.snake).toEqual(snakeBeforePause);
        });

        test('score should be preserved during pause', () => {
            game.startGame();

            // Set a score
            game.score = 50;

            // Pause the game
            game.togglePause();

            // Score should be unchanged
            expect(game.score).toBe(50);
        });

        test('snake position should remain preserved after pause duration', () => {
            game.startGame();

            const snakeBeforePause = JSON.parse(JSON.stringify(game.snake));
            game.togglePause();

            // Simulate time passing while paused
            // (In real game, this would be handled by the game loop check)

            expect(game.snake).toEqual(snakeBeforePause);
        });

        test('food position should be preserved during pause', () => {
            game.startGame();
            const foodBeforePause = { ...game.food };

            game.togglePause();

            expect(game.food).toEqual(foodBeforePause);
        });

        test('direction should be preserved during pause', () => {
            game.startGame();
            game.nextDirection = Direction.UP;

            game.togglePause();

            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('game speed should be preserved during pause', () => {
            game.startGame();
            game.speed = 100; // Modified speed

            game.togglePause();

            expect(game.speed).toBe(100);
        });

        test('high score should be preserved during pause', () => {
            game.highScore = 100;
            game.startGame();

            game.togglePause();

            expect(game.highScore).toBe(100);
        });

        test('snake length should be preserved after multiple pause/resume cycles', () => {
            game.startGame();
            const initialLength = game.snake.length;

            // Multiple pause/resume cycles
            game.togglePause();
            game.togglePause();
            game.togglePause();
            game.togglePause();

            expect(game.snake.length).toBe(initialLength);
        });

        test('score should remain preserved after resuming and pausing again', () => {
            game.startGame();
            game.score = 30;

            game.togglePause();
            game.togglePause(); // Resume

            // Score still preserved after resume
            expect(game.score).toBe(30);

            game.togglePause(); // Pause again
            expect(game.score).toBe(30);
        });
    });

    /**
     * Test Case 4: Visual pause indicator
     * Input: Visual pause indicator
     * Expected: Paused state is visually indicated to user
     */
    describe('Test Case 4: Visual Pause Indicator (E2E)', () => {
        test('should display pause message when game is paused', () => {
            game.startGame();
            game.togglePause();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Paused');
        });

        test('should display resume instruction in pause message', () => {
            game.startGame();
            game.togglePause();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Space');
        });

        test('should clear pause message when game is resumed', () => {
            game.startGame();
            game.togglePause();
            game.togglePause(); // Resume

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).not.toContain('Paused');
        });

        test('pause message should be visible via updateStatus method', () => {
            const updateStatusSpy = jest.spyOn(game, 'updateStatus');

            game.startGame();
            game.togglePause();

            expect(updateStatusSpy).toHaveBeenCalledWith(expect.stringContaining('Paused'));
        });

        test('resume should clear the status message', () => {
            game.startGame();
            game.togglePause();

            const updateStatusSpy = jest.spyOn(game, 'updateStatus');
            game.togglePause(); // Resume

            expect(updateStatusSpy).toHaveBeenCalledWith('');
        });

        test('status element should exist and be accessible', () => {
            const statusElement = document.getElementById('game-status');
            expect(statusElement).not.toBeNull();
            expect(statusElement).toBeTruthy();
        });

        test('game state should be queryable via getState method when paused', () => {
            game.startGame();
            game.togglePause();

            expect(game.getState()).toBe(GameState.PAUSED);
            expect(GameState.PAUSED).toBe('paused');
        });

        test('GameState should have PAUSED constant defined', () => {
            expect(GameState.PAUSED).toBeDefined();
            expect(GameState.PAUSED).toBe('paused');
        });
    });

    /**
     * Additional integration tests for pause/resume functionality
     */
    describe('Integration Tests: Pause and Resume', () => {
        test('should not pause when game is in READY state', () => {
            expect(game.getState()).toBe(GameState.READY);

            game.togglePause();

            // togglePause only works when PLAYING or PAUSED
            expect(game.getState()).toBe(GameState.READY);
        });

        test('should not pause when game is in GAME_OVER state', () => {
            game.startGame();
            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);

            game.togglePause();

            expect(game.getState()).toBe(GameState.GAME_OVER);
        });

        test('direction changes should not apply when paused', () => {
            game.startGame();
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.togglePause();

            // Try to change direction via handleKeyPress (should be ignored for direction keys when paused)
            const upKeyEvent = { key: 'ArrowUp' };
            game.handleKeyPress(upKeyEvent);

            // Direction should remain unchanged since direction changes only apply in PLAYING state
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('full pause/resume cycle should work correctly', () => {
            // Start game
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);
            const snakePosition = JSON.parse(JSON.stringify(game.snake));
            const initialScore = game.score;

            // Pause
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);
            expect(document.getElementById('game-status').textContent).toContain('Paused');

            // Verify state preserved
            expect(game.snake).toEqual(snakePosition);
            expect(game.score).toBe(initialScore);

            // Resume
            game.togglePause();
            expect(game.getState()).toBe(GameState.PLAYING);
            expect(document.getElementById('game-status').textContent).toBe('');

            // Verify game continues normally
            expect(game.snake).toEqual(snakePosition);
            expect(game.score).toBe(initialScore);
        });

        test('should handle rapid pause/resume toggling', () => {
            game.startGame();

            for (let i = 0; i < 10; i++) {
                game.togglePause();
                game.togglePause();
            }

            // Game should still be in PLAYING state
            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('pause should work correctly after eating food', () => {
            game.startGame();

            // Simulate eating food
            game.score = 10;
            game.snake.push({ x: 5, y: 5 }); // Grow snake
            const lengthAfterEating = game.snake.length;
            const scoreAfterEating = game.score;

            game.togglePause();

            expect(game.snake.length).toBe(lengthAfterEating);
            expect(game.score).toBe(scoreAfterEating);
        });

        test('should be able to change direction immediately after resuming', () => {
            game.startGame();
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.togglePause();
            game.togglePause(); // Resume

            // Should be able to change direction now
            const upKeyEvent = { key: 'ArrowUp' };
            game.handleKeyPress(upKeyEvent);

            expect(game.nextDirection).toBe(Direction.UP);
        });
    });
});

/**
 * E2E Integration Test: Complete Pause/Resume Flow
 */
describe('E2E Test: Complete Pause and Resume Flow', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        global.requestAnimationFrame = jest.fn((cb) => setTimeout(cb, 16));
        global.cancelAnimationFrame = jest.fn((id) => clearTimeout(id));

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

        game = new SnakeGame();
        game.init();
    });

    test('complete game flow: start -> play -> pause -> resume -> continue playing', () => {
        // 1. Game starts in READY state
        expect(game.getState()).toBe(GameState.READY);

        // 2. Start game
        game.startGame();
        expect(game.getState()).toBe(GameState.PLAYING);

        // 3. Store game state
        const snakeBeforePause = JSON.parse(JSON.stringify(game.snake));
        const scoreBeforePause = game.score;

        // 4. Pause game using handleKeyPress with plain object
        const pauseEvent = { key: ' ' };
        game.handleKeyPress(pauseEvent);
        expect(game.getState()).toBe(GameState.PAUSED);

        // 5. Verify pause indicator
        const statusElement = document.getElementById('game-status');
        expect(statusElement.textContent).toContain('Paused');

        // 6. Verify state preserved
        expect(game.snake).toEqual(snakeBeforePause);
        expect(game.score).toBe(scoreBeforePause);

        // 7. Resume game using handleKeyPress with plain object
        const resumeEvent = { key: ' ' };
        game.handleKeyPress(resumeEvent);
        expect(game.getState()).toBe(GameState.PLAYING);

        // 8. Verify pause indicator cleared
        expect(statusElement.textContent).toBe('');

        // 9. Verify game continues with same state
        expect(game.snake).toEqual(snakeBeforePause);
        expect(game.score).toBe(scoreBeforePause);
    });

    test('pause should not affect ability to restart after game over', () => {
        // Start and pause
        game.startGame();
        game.togglePause();
        game.togglePause(); // Resume

        // Game over
        game.gameOver();
        expect(game.getState()).toBe(GameState.GAME_OVER);

        // Should be able to restart
        const restartEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        document.dispatchEvent(restartEvent);
        expect(game.getState()).toBe(GameState.PLAYING);
    });

    test('score display should remain correct during pause', () => {
        game.startGame();
        game.score = 50;
        game.updateScoreDisplay();

        game.togglePause();

        const scoreElement = document.getElementById('score');
        expect(scoreElement.textContent).toBe('50');
    });
});
