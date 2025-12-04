/**
 * Snake Game - Test Suite for Game State Management
 *
 * Tests for Scenario: Game State Management
 * - Test Case 1: Initial page load - Game state is 'Ready'
 * - Test Case 2: Start key pressed - Game state transitions to 'Playing'
 * - Test Case 3: Pause key pressed during play - Game state transitions to 'Paused'
 * - Test Case 4: Collision detected - Game state transitions to 'Game Over'
 * - Test Case 5: Invalid state transitions are blocked
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

describe('Snake Game - Game State Management', () => {
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
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Initial page load
     * Input: Initial page load
     * Expected: Game state is 'Ready'
     */
    describe('Test Case 1: Initial Page Load - Game State is Ready', () => {
        test('should have game state as READY before initialization', () => {
            // Before init, state should already be READY (from constructor)
            expect(game.getState()).toBe(GameState.READY);
        });

        test('should have game state as READY after initialization', () => {
            game.init();
            expect(game.getState()).toBe(GameState.READY);
        });

        test('GameState.READY should be defined as "ready"', () => {
            expect(GameState.READY).toBe('ready');
        });

        test('should display "Press any key to start" prompt in READY state', () => {
            game.init();
            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Press any key to start');
        });

        test('snake should be initialized but not moving in READY state', () => {
            game.init();
            expect(game.snake).toBeDefined();
            expect(game.snake.length).toBe(3);
        });

        test('food should be spawned in READY state', () => {
            game.init();
            expect(game.food).not.toBeNull();
            expect(game.food.x).toBeDefined();
            expect(game.food.y).toBeDefined();
        });

        test('score should be zero in READY state', () => {
            game.init();
            expect(game.score).toBe(0);
        });

        test('game loop should not be running in READY state', () => {
            game.init();
            // game.update() should return early if state is not PLAYING
            const moveSnakeSpy = jest.spyOn(game, 'moveSnake');
            game.update();
            expect(moveSnakeSpy).not.toHaveBeenCalled();
        });
    });

    /**
     * Test Case 2: Start key pressed
     * Input: Start key pressed
     * Expected: Game state transitions to 'Playing'
     */
    describe('Test Case 2: Start Key Pressed - Game State Transitions to Playing', () => {
        beforeEach(() => {
            game.init();
        });

        test('should transition from READY to PLAYING when startGame is called', () => {
            expect(game.getState()).toBe(GameState.READY);
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('should transition from READY to PLAYING when any key is pressed', () => {
            expect(game.getState()).toBe(GameState.READY);

            const event = { key: 'Enter' };
            game.handleKeyPress(event);

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('should transition from READY to PLAYING when arrow key is pressed', () => {
            expect(game.getState()).toBe(GameState.READY);

            const event = { key: 'ArrowUp' };
            game.handleKeyPress(event);

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('should transition from READY to PLAYING when space key is pressed', () => {
            expect(game.getState()).toBe(GameState.READY);

            const event = { key: ' ' };
            game.handleKeyPress(event);

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('GameState.PLAYING should be defined as "playing"', () => {
            expect(GameState.PLAYING).toBe('playing');
        });

        test('should start animation frame loop when transitioning to PLAYING', () => {
            mockRequestAnimationFrame.mockClear();
            game.startGame();
            expect(mockRequestAnimationFrame).toHaveBeenCalled();
        });

        test('should clear status message when transitioning to PLAYING', () => {
            game.startGame();
            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toBe('');
        });

        test('should set lastUpdateTime when starting game', () => {
            const timeBefore = Date.now();
            game.startGame();
            expect(game.lastUpdateTime).toBeGreaterThanOrEqual(timeBefore);
        });
    });

    /**
     * Test Case 3: Pause key pressed during play
     * Input: Pause key pressed during play
     * Expected: Game state transitions to 'Paused'
     */
    describe('Test Case 3: Pause Key Pressed During Play - Game State Transitions to Paused', () => {
        beforeEach(() => {
            game.init();
            game.startGame();
        });

        test('should transition from PLAYING to PAUSED when togglePause is called', () => {
            expect(game.getState()).toBe(GameState.PLAYING);
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);
        });

        test('should transition from PLAYING to PAUSED when space key is pressed', () => {
            expect(game.getState()).toBe(GameState.PLAYING);

            const event = { key: ' ' };
            game.handleKeyPress(event);

            expect(game.getState()).toBe(GameState.PAUSED);
        });

        test('GameState.PAUSED should be defined as "paused"', () => {
            expect(GameState.PAUSED).toBe('paused');
        });

        test('should cancel animation frame when transitioning to PAUSED', () => {
            const gameLoopId = game.gameLoop;
            game.togglePause();
            expect(mockCancelAnimationFrame).toHaveBeenCalledWith(gameLoopId);
        });

        test('should display pause message when transitioning to PAUSED', () => {
            game.togglePause();
            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Paused');
        });

        test('should transition from PAUSED back to PLAYING when space key is pressed', () => {
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            const event = { key: ' ' };
            game.handleKeyPress(event);

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('game state should remain preserved during pause', () => {
            const snakeBefore = JSON.parse(JSON.stringify(game.snake));
            const foodBefore = JSON.parse(JSON.stringify(game.food));
            const scoreBefore = game.score;

            game.togglePause();

            expect(game.snake).toEqual(snakeBefore);
            expect(game.food).toEqual(foodBefore);
            expect(game.score).toBe(scoreBefore);
        });
    });

    /**
     * Test Case 4: Collision detected
     * Input: Collision detected
     * Expected: Game state transitions to 'Game Over'
     */
    describe('Test Case 4: Collision Detected - Game State Transitions to Game Over', () => {
        beforeEach(() => {
            game.init();
            game.startGame();
        });

        test('should transition from PLAYING to GAME_OVER when gameOver is called', () => {
            expect(game.getState()).toBe(GameState.PLAYING);
            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);
        });

        test('GameState.GAME_OVER should be defined as "game_over"', () => {
            expect(GameState.GAME_OVER).toBe('game_over');
        });

        test('should transition to GAME_OVER on wall collision (left boundary)', () => {
            game.snake[0] = { x: -1, y: 10 };
            const collisionDetected = game.checkCollisions();
            expect(collisionDetected).toBe(true);
        });

        test('should transition to GAME_OVER on wall collision (right boundary)', () => {
            const gridWidth = game.boardSize / CONFIG.GRID_SIZE;
            game.snake[0] = { x: gridWidth, y: 10 };
            const collisionDetected = game.checkCollisions();
            expect(collisionDetected).toBe(true);
        });

        test('should transition to GAME_OVER on wall collision (top boundary)', () => {
            game.snake[0] = { x: 10, y: -1 };
            const collisionDetected = game.checkCollisions();
            expect(collisionDetected).toBe(true);
        });

        test('should transition to GAME_OVER on wall collision (bottom boundary)', () => {
            const gridHeight = game.boardSize / CONFIG.GRID_SIZE;
            game.snake[0] = { x: 10, y: gridHeight };
            const collisionDetected = game.checkCollisions();
            expect(collisionDetected).toBe(true);
        });

        test('should transition to GAME_OVER on self collision', () => {
            // Set up snake that collides with itself
            game.snake = [
                { x: 5, y: 5 },  // head
                { x: 6, y: 5 },
                { x: 6, y: 6 },
                { x: 5, y: 6 },
                { x: 5, y: 5 }   // body segment at same position as head
            ];
            const collisionDetected = game.checkCollisions();
            expect(collisionDetected).toBe(true);
        });

        test('should cancel animation frame when game over', () => {
            const gameLoopId = game.gameLoop;
            game.gameOver();
            expect(mockCancelAnimationFrame).toHaveBeenCalledWith(gameLoopId);
        });

        test('should display game over message', () => {
            game.gameOver();
            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Game Over');
        });

        test('should save high score when game over', () => {
            game.score = 100;
            game.highScore = 50;
            game.gameOver();
            expect(game.highScore).toBe(100);
        });

        test('should allow restart from GAME_OVER state on any key press', () => {
            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);

            const event = { key: 'Enter' };
            game.handleKeyPress(event);

            expect(game.getState()).toBe(GameState.PLAYING);
        });
    });

    /**
     * Test Case 5: Invalid state transitions
     * Input: Invalid state transitions
     * Expected: Invalid transitions are blocked
     */
    describe('Test Case 5: Invalid State Transitions Are Blocked', () => {
        beforeEach(() => {
            game.init();
        });

        test('should not allow pause from READY state', () => {
            expect(game.getState()).toBe(GameState.READY);
            game.togglePause();
            expect(game.getState()).toBe(GameState.READY);
        });

        test('should not allow pause from GAME_OVER state', () => {
            game.startGame();
            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);

            game.togglePause();

            expect(game.getState()).toBe(GameState.GAME_OVER);
        });

        test('should not process direction changes in READY state', () => {
            expect(game.getState()).toBe(GameState.READY);
            const initialDirection = game.nextDirection;

            // Any key in READY state starts the game, doesn't change direction
            const event = { key: 'ArrowUp' };
            game.handleKeyPress(event);

            // Game should have started
            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('should not process direction changes in PAUSED state', () => {
            game.startGame();
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            const initialDirection = game.nextDirection;
            const event = { key: 'ArrowUp' };
            game.handleKeyPress(event);

            // Direction should not change while paused (for non-space keys)
            expect(game.nextDirection).toBe(initialDirection);
        });

        test('should not process direction changes in GAME_OVER state', () => {
            game.startGame();
            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);

            // Arrow keys in GAME_OVER state restart the game
            const event = { key: 'ArrowUp' };
            game.handleKeyPress(event);

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('update should not execute game logic in READY state', () => {
            expect(game.getState()).toBe(GameState.READY);

            const moveSnakeSpy = jest.spyOn(game, 'moveSnake');
            game.update();

            expect(moveSnakeSpy).not.toHaveBeenCalled();
        });

        test('update should not execute game logic in PAUSED state', () => {
            game.startGame();
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            const moveSnakeSpy = jest.spyOn(game, 'moveSnake');
            game.update();

            expect(moveSnakeSpy).not.toHaveBeenCalled();
        });

        test('update should not execute game logic in GAME_OVER state', () => {
            game.startGame();
            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);

            const moveSnakeSpy = jest.spyOn(game, 'moveSnake');
            game.update();

            expect(moveSnakeSpy).not.toHaveBeenCalled();
        });

        test('should only allow space key to resume from PAUSED state', () => {
            game.startGame();
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            // Non-space key should not change state
            const arrowEvent = { key: 'ArrowUp' };
            game.handleKeyPress(arrowEvent);
            expect(game.getState()).toBe(GameState.PAUSED);

            // Space key should resume
            const spaceEvent = { key: ' ' };
            game.handleKeyPress(spaceEvent);
            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('all four game states should be defined', () => {
            expect(GameState.READY).toBeDefined();
            expect(GameState.PLAYING).toBeDefined();
            expect(GameState.PAUSED).toBeDefined();
            expect(GameState.GAME_OVER).toBeDefined();
        });

        test('game states should have expected string values', () => {
            expect(GameState.READY).toBe('ready');
            expect(GameState.PLAYING).toBe('playing');
            expect(GameState.PAUSED).toBe('paused');
            expect(GameState.GAME_OVER).toBe('game_over');
        });
    });

    /**
     * Integration Tests: Complete State Transition Flow
     */
    describe('Integration Tests: Complete State Transition Flow', () => {
        beforeEach(() => {
            game.init();
        });

        test('complete state flow: READY -> PLAYING -> PAUSED -> PLAYING -> GAME_OVER -> PLAYING', () => {
            // Start in READY
            expect(game.getState()).toBe(GameState.READY);

            // Transition to PLAYING
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);

            // Transition to PAUSED
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            // Transition back to PLAYING
            game.togglePause();
            expect(game.getState()).toBe(GameState.PLAYING);

            // Transition to GAME_OVER
            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);

            // Restart to PLAYING
            game.resetGame();
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('state flow: READY -> PLAYING -> GAME_OVER (direct path)', () => {
            expect(game.getState()).toBe(GameState.READY);

            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);

            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);
        });

        test('repeated pause/resume cycle should work correctly', () => {
            game.startGame();

            for (let i = 0; i < 5; i++) {
                game.togglePause();
                expect(game.getState()).toBe(GameState.PAUSED);

                game.togglePause();
                expect(game.getState()).toBe(GameState.PLAYING);
            }
        });

        test('resetGame should return state to READY', () => {
            game.startGame();
            game.gameOver();

            game.resetGame();

            expect(game.getState()).toBe(GameState.READY);
            expect(game.score).toBe(0);
        });
    });
});

/**
 * E2E Test: Complete Game State Management Flow
 */
describe('E2E Test: Game State Management Flow', () => {
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

    test('full user journey: load -> start -> pause -> resume -> game over -> restart', () => {
        // 1. Initial load - READY state
        expect(game.getState()).toBe(GameState.READY);
        expect(document.getElementById('game-status').textContent).toContain('Press any key to start');

        // 2. Press key to start - transitions to PLAYING
        const startEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        document.dispatchEvent(startEvent);
        expect(game.getState()).toBe(GameState.PLAYING);
        expect(document.getElementById('game-status').textContent).toBe('');

        // 3. Press space to pause - transitions to PAUSED
        const pauseEvent = new KeyboardEvent('keydown', { key: ' ' });
        document.dispatchEvent(pauseEvent);
        expect(game.getState()).toBe(GameState.PAUSED);
        expect(document.getElementById('game-status').textContent).toContain('Paused');

        // 4. Press space to resume - transitions back to PLAYING
        const resumeEvent = new KeyboardEvent('keydown', { key: ' ' });
        document.dispatchEvent(resumeEvent);
        expect(game.getState()).toBe(GameState.PLAYING);

        // 5. Trigger game over (simulate collision)
        game.gameOver();
        expect(game.getState()).toBe(GameState.GAME_OVER);
        expect(document.getElementById('game-status').textContent).toContain('Game Over');

        // 6. Press key to restart
        const restartEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        document.dispatchEvent(restartEvent);
        expect(game.getState()).toBe(GameState.PLAYING);
    });

    test('collision detection triggers game over state', () => {
        game.startGame();
        expect(game.getState()).toBe(GameState.PLAYING);

        // Move snake to wall (simulate collision by setting position outside bounds)
        game.snake[0] = { x: -1, y: 10 };

        // Check collision should return true
        const hasCollision = game.checkCollisions();
        expect(hasCollision).toBe(true);

        // If collision detected, game should end
        if (hasCollision) {
            game.gameOver();
        }

        expect(game.getState()).toBe(GameState.GAME_OVER);
    });

    test('self collision triggers game over state', () => {
        game.startGame();

        // Set up snake that collides with itself
        game.snake = [
            { x: 5, y: 5 },
            { x: 6, y: 5 },
            { x: 6, y: 6 },
            { x: 5, y: 6 },
            { x: 5, y: 5 }
        ];

        const hasCollision = game.checkCollisions();
        expect(hasCollision).toBe(true);

        if (hasCollision) {
            game.gameOver();
        }

        expect(game.getState()).toBe(GameState.GAME_OVER);
    });
});
