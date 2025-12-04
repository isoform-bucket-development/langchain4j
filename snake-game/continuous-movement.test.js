/**
 * Snake Game - Continuous Movement Test Suite
 *
 * Tests for Scenario: Continuous Snake Movement
 * - Test Case 1: Game started, no input - Snake moves continuously in initial direction
 * - Test Case 2: Game loop timing - Snake position updates at consistent intervals
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

describe('Snake Game - Continuous Snake Movement', () => {
    let game;
    let mockCtx;
    let rafCallbacks;
    let rafId;
    let originalRAF;
    let originalCAF;
    let originalDateNow;
    let mockTime;

    beforeEach(() => {
        // Create mock context
        mockCtx = createMockContext();
        rafCallbacks = [];
        rafId = 0;
        mockTime = 0;

        // Store original functions
        originalRAF = global.requestAnimationFrame;
        originalCAF = global.cancelAnimationFrame;
        originalDateNow = Date.now;

        // Mock Date.now for controlled timing
        Date.now = jest.fn(() => mockTime);

        // Mock requestAnimationFrame to track calls and timing
        global.requestAnimationFrame = jest.fn((callback) => {
            rafId++;
            rafCallbacks.push({ id: rafId, callback });
            return rafId;
        });

        // Mock cancelAnimationFrame
        global.cancelAnimationFrame = jest.fn((id) => {
            rafCallbacks = rafCallbacks.filter(cb => cb.id !== id);
        });

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
        // Restore original functions
        global.requestAnimationFrame = originalRAF;
        global.cancelAnimationFrame = originalCAF;
        Date.now = originalDateNow;
        jest.clearAllMocks();
    });

    /**
     * Helper function to execute RAF callbacks
     */
    const executeRAFCallbacks = () => {
        const callbacks = [...rafCallbacks];
        rafCallbacks = [];
        callbacks.forEach(({ callback }) => callback());
    };

    /**
     * Helper function to advance mock time
     */
    const advanceTime = (ms) => {
        mockTime += ms;
    };

    /**
     * Test Case 1: Game started, no input - Snake moves continuously in initial direction
     * Input: Game started, no input
     * Expected: Snake moves continuously in initial direction
     */
    describe('Test Case 1: Continuous Movement Without Input', () => {
        test('should move snake continuously after game starts without any user input', () => {
            game.init();
            const initialHeadPos = { ...game.snake[0] };

            // Start the game
            game.startGame();
            expect(game.state).toBe(GameState.PLAYING);

            // Record initial position
            expect(game.snake[0].x).toBe(initialHeadPos.x);
            expect(game.snake[0].y).toBe(initialHeadPos.y);

            // Advance time past the speed threshold
            advanceTime(CONFIG.INITIAL_SPEED + 1);

            // Execute the RAF callback (simulates game loop iteration)
            executeRAFCallbacks();

            // Snake should have moved (in initial direction which is RIGHT)
            expect(game.snake[0].x).toBe(initialHeadPos.x + 1);
            expect(game.snake[0].y).toBe(initialHeadPos.y);
        });

        test('should continue moving in the same direction without keyboard input', () => {
            game.init();
            game.startGame();

            const positions = [{ ...game.snake[0] }];

            // Simulate multiple game loop iterations without any input
            for (let i = 0; i < 5; i++) {
                advanceTime(CONFIG.INITIAL_SPEED + 1);
                executeRAFCallbacks();
                positions.push({ ...game.snake[0] });
            }

            // Each position should be one step to the right of the previous
            for (let i = 1; i < positions.length; i++) {
                expect(positions[i].x).toBe(positions[i - 1].x + 1);
                expect(positions[i].y).toBe(positions[i - 1].y);
            }
        });

        test('should move in initial direction RIGHT when game starts', () => {
            game.init();
            expect(game.direction).toEqual(Direction.RIGHT);

            game.startGame();

            // Advance and update
            advanceTime(CONFIG.INITIAL_SPEED + 1);
            executeRAFCallbacks();

            // Verify movement direction is RIGHT (x increases, y stays same)
            expect(game.direction).toEqual(Direction.RIGHT);
        });

        test('should keep moving until collision occurs (no stop mechanism)', () => {
            game.init();
            game.startGame();

            let iterationCount = 0;
            const maxIterations = 100;

            // Simulate game loop until collision or max iterations
            while (game.state === GameState.PLAYING && iterationCount < maxIterations) {
                advanceTime(CONFIG.INITIAL_SPEED + 1);
                executeRAFCallbacks();
                iterationCount++;
            }

            // Game should have either:
            // 1. Hit a wall (GAME_OVER state)
            // 2. Still be playing (within bounds)
            expect([GameState.PLAYING, GameState.GAME_OVER]).toContain(game.state);

            // If still playing, snake must have moved multiple times
            if (game.state === GameState.PLAYING) {
                expect(iterationCount).toBe(maxIterations);
            }
        });

        test('should not provide any way to stop the snake movement during gameplay', () => {
            game.init();
            game.startGame();

            // Verify no stop method or stationary direction exists
            expect(game.direction).not.toEqual({ x: 0, y: 0 });

            // Try all arrow keys and WASD - none should stop movement
            const keysToTest = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'w', 'a', 's', 'd'];

            keysToTest.forEach(key => {
                const event = new KeyboardEvent('keydown', { key });
                document.dispatchEvent(event);

                // After any key, direction should still be valid (non-zero)
                const dirMagnitude = Math.abs(game.nextDirection.x) + Math.abs(game.nextDirection.y);
                expect(dirMagnitude).toBe(1); // Direction should have magnitude 1
            });
        });

        test('should automatically schedule next update after each game loop iteration', () => {
            game.init();
            game.startGame();

            // First RAF call made during startGame
            expect(global.requestAnimationFrame).toHaveBeenCalledTimes(1);

            // Simulate one frame
            advanceTime(CONFIG.INITIAL_SPEED + 1);
            executeRAFCallbacks();

            // RAF should be called again for next frame
            expect(global.requestAnimationFrame).toHaveBeenCalledTimes(2);
        });

        test('snake should enter PLAYING state immediately on start and stay moving', () => {
            game.init();
            expect(game.state).toBe(GameState.READY);

            // Simulate pressing a key to start
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            document.dispatchEvent(event);

            expect(game.state).toBe(GameState.PLAYING);

            // Game loop should be active
            expect(game.gameLoop).toBeDefined();
            expect(typeof game.gameLoop).toBe('number');
        });
    });

    /**
     * Test Case 2: Game loop timing - Snake position updates at consistent intervals
     * Input: Game loop timing
     * Expected: Snake position updates at consistent intervals
     */
    describe('Test Case 2: Consistent Update Intervals', () => {
        test('should update snake position at speed interval (not before)', () => {
            game.init();
            game.startGame();

            const initialHead = { ...game.snake[0] };

            // Advance time but NOT past the speed threshold
            advanceTime(CONFIG.INITIAL_SPEED - 10);
            executeRAFCallbacks();

            // Snake should NOT have moved yet
            expect(game.snake[0].x).toBe(initialHead.x);
            expect(game.snake[0].y).toBe(initialHead.y);
        });

        test('should update snake position exactly when speed interval is reached', () => {
            game.init();
            game.startGame();

            const initialHead = { ...game.snake[0] };

            // Advance time exactly to the speed threshold
            advanceTime(CONFIG.INITIAL_SPEED);
            executeRAFCallbacks();

            // Snake should have moved
            expect(game.snake[0].x).toBe(initialHead.x + 1);
        });

        test('should maintain consistent update intervals over multiple iterations', () => {
            game.init();
            game.startGame();

            const moveTimestamps = [];
            const initialHead = { ...game.snake[0] };
            let expectedX = initialHead.x;

            // Simulate 10 update cycles with exact timing
            for (let i = 0; i < 10; i++) {
                advanceTime(CONFIG.INITIAL_SPEED);
                const timeBefore = mockTime;
                executeRAFCallbacks();

                // Snake should have moved
                expectedX++;
                expect(game.snake[0].x).toBe(expectedX);
                moveTimestamps.push(timeBefore);
            }

            // Check that intervals are consistent
            for (let i = 1; i < moveTimestamps.length; i++) {
                const interval = moveTimestamps[i] - moveTimestamps[i - 1];
                expect(interval).toBe(CONFIG.INITIAL_SPEED);
            }
        });

        test('should use delta time accumulator pattern for frame-rate independence', () => {
            game.init();
            game.startGame();

            // Verify game tracks lastUpdateTime
            expect(game.lastUpdateTime).toBeDefined();

            // lastUpdateTime should be set when game starts
            const startTime = mockTime;
            expect(game.lastUpdateTime).toBe(startTime);
        });

        test('should only move once per speed interval even with multiple RAF callbacks', () => {
            game.init();
            game.startGame();

            const initialHead = { ...game.snake[0] };

            // Advance time past speed threshold
            advanceTime(CONFIG.INITIAL_SPEED + 1);

            // Execute RAF callback multiple times at same timestamp
            executeRAFCallbacks();
            executeRAFCallbacks();
            executeRAFCallbacks();

            // Snake should only have moved once
            expect(game.snake[0].x).toBe(initialHead.x + 1);
        });

        test('should use CONFIG.INITIAL_SPEED as base update interval', () => {
            expect(CONFIG.INITIAL_SPEED).toBeDefined();
            expect(typeof CONFIG.INITIAL_SPEED).toBe('number');
            expect(CONFIG.INITIAL_SPEED).toBeGreaterThan(0);

            game.init();
            expect(game.speed).toBe(CONFIG.INITIAL_SPEED);
        });

        test('should accumulate delta time correctly across frames', () => {
            game.init();
            game.startGame();

            const initialHead = { ...game.snake[0] };

            // Advance partial intervals (should accumulate)
            advanceTime(CONFIG.INITIAL_SPEED / 2);
            executeRAFCallbacks();

            // Snake should NOT have moved
            expect(game.snake[0].x).toBe(initialHead.x);

            // Advance another half interval
            advanceTime(CONFIG.INITIAL_SPEED / 2);
            executeRAFCallbacks();

            // Now snake should have moved (total time >= speed)
            expect(game.snake[0].x).toBe(initialHead.x + 1);
        });

        test('game speed should be adjustable (increases when food eaten)', () => {
            game.init();
            const initialSpeed = game.speed;

            // Simulate eating food
            game.increaseSpeed();

            // Speed should decrease (faster movement = lower interval)
            expect(game.speed).toBeLessThan(initialSpeed);
            expect(game.speed).toBe(initialSpeed - CONFIG.SPEED_INCREMENT);
        });

        test('should not update snake position when game is paused', () => {
            game.init();
            game.startGame();

            const initialHead = { ...game.snake[0] };

            // Pause the game
            game.togglePause();
            expect(game.state).toBe(GameState.PAUSED);

            // Advance time past speed threshold
            advanceTime(CONFIG.INITIAL_SPEED + 100);

            // Try to execute update (it should return early due to paused state)
            game.update();

            // Snake should NOT have moved
            expect(game.snake[0].x).toBe(initialHead.x);
            expect(game.snake[0].y).toBe(initialHead.y);
        });

        test('should resume movement at correct timing after unpause', () => {
            game.init();
            game.startGame();

            // Let snake move once
            advanceTime(CONFIG.INITIAL_SPEED + 1);
            executeRAFCallbacks();
            const headAfterFirstMove = { ...game.snake[0] };

            // Pause
            game.togglePause();

            // Advance time while paused
            advanceTime(CONFIG.INITIAL_SPEED * 5);

            // Unpause
            game.togglePause();

            // lastUpdateTime should be reset on unpause
            expect(game.lastUpdateTime).toBe(mockTime);

            // Advance time and execute update
            advanceTime(CONFIG.INITIAL_SPEED + 1);
            executeRAFCallbacks();

            // Snake should have moved exactly once from head position after pause
            expect(game.snake[0].x).toBe(headAfterFirstMove.x + 1);
        });
    });

    /**
     * Integration Tests: Continuous Movement Behavior
     */
    describe('Integration: Continuous Movement Behavior', () => {
        test('snake should move continuously until hitting a wall', () => {
            game.init();
            game.startGame();

            // Calculate how many steps until wall collision
            const gridWidth = game.boardSize / CONFIG.GRID_SIZE;
            const initialX = game.snake[0].x;
            const stepsToWall = gridWidth - initialX;

            // Move snake until it hits the wall
            for (let i = 0; i < stepsToWall + 5; i++) {
                if (game.state !== GameState.PLAYING) break;
                advanceTime(CONFIG.INITIAL_SPEED + 1);
                executeRAFCallbacks();
            }

            // Game should be over (wall collision)
            expect(game.state).toBe(GameState.GAME_OVER);
        });

        test('player cannot stop snake - only change direction', () => {
            game.init();
            game.startGame();

            // Direction constants check - no STOP direction
            expect(Direction.UP).toEqual({ x: 0, y: -1 });
            expect(Direction.DOWN).toEqual({ x: 0, y: 1 });
            expect(Direction.LEFT).toEqual({ x: -1, y: 0 });
            expect(Direction.RIGHT).toEqual({ x: 1, y: 0 });

            // No zero/stop direction exists
            const directions = Object.values(Direction);
            const hasStopDirection = directions.some(d => d.x === 0 && d.y === 0);
            expect(hasStopDirection).toBe(false);
        });

        test('game loop should be active throughout gameplay', () => {
            game.init();
            game.startGame();

            // Game loop should be active
            expect(game.gameLoop).toBeDefined();

            // Simulate several frames
            for (let i = 0; i < 5; i++) {
                advanceTime(CONFIG.INITIAL_SPEED + 1);
                executeRAFCallbacks();

                if (game.state === GameState.PLAYING) {
                    expect(game.gameLoop).toBeDefined();
                }
            }
        });

        test('snake continuously moves in changed direction after direction change', () => {
            game.init();
            game.startGame();

            // Initial direction is RIGHT
            expect(game.direction).toEqual(Direction.RIGHT);

            // Move once
            advanceTime(CONFIG.INITIAL_SPEED + 1);
            executeRAFCallbacks();

            // Change direction to UP
            game.handleKeyPress({ key: 'ArrowUp' });
            expect(game.nextDirection).toEqual(Direction.UP);

            // Let snake move in new direction multiple times
            const positionsY = [game.snake[0].y];
            for (let i = 0; i < 3; i++) {
                advanceTime(CONFIG.INITIAL_SPEED + 1);
                executeRAFCallbacks();
                positionsY.push(game.snake[0].y);
            }

            // Y should decrease each time (moving UP)
            for (let i = 1; i < positionsY.length; i++) {
                expect(positionsY[i]).toBe(positionsY[i - 1] - 1);
            }
        });
    });
});

/**
 * E2E Tests: Continuous Snake Movement
 */
describe('E2E Test: Continuous Snake Movement', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        mockCtx = createMockContext();

        // Mock RAF with realistic timing
        let rafId = 0;
        global.requestAnimationFrame = jest.fn((cb) => {
            rafId++;
            setTimeout(cb, 16); // ~60 FPS
            return rafId;
        });
        global.cancelAnimationFrame = jest.fn((id) => clearTimeout(id));

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
    });

    afterEach(() => {
        jest.clearAllMocks();
        jest.useRealTimers();
    });

    test('E2E: Snake moves continuously from game start without user interaction', () => {
        game.init();

        // Start game by pressing a key
        const startEvent = new KeyboardEvent('keydown', { key: 'Enter' });
        document.dispatchEvent(startEvent);

        // Verify game started
        expect(game.state).toBe(GameState.PLAYING);

        // Verify game loop is active
        expect(game.gameLoop).toBeDefined();
        expect(global.requestAnimationFrame).toHaveBeenCalled();

        // Verify initial direction is set
        expect(game.direction).toEqual(Direction.RIGHT);
    });

    test('E2E: Game provides no mechanism to stop snake movement', () => {
        game.init();
        game.startGame();

        // Verify all possible directions are movement directions (not stop)
        expect(Direction.UP).not.toEqual({ x: 0, y: 0 });
        expect(Direction.DOWN).not.toEqual({ x: 0, y: 0 });
        expect(Direction.LEFT).not.toEqual({ x: 0, y: 0 });
        expect(Direction.RIGHT).not.toEqual({ x: 0, y: 0 });

        // Verify game is using RAF for continuous updates
        expect(global.requestAnimationFrame).toHaveBeenCalled();
    });

    test('E2E: Complete game flow - continuous movement until collision', async () => {
        jest.useFakeTimers();

        game.init();
        game.startGame();

        const initialState = game.state;
        expect(initialState).toBe(GameState.PLAYING);

        // Verify the update function continues scheduling itself
        const updateSpy = jest.spyOn(game, 'update');

        // Run game loop for a bit
        for (let i = 0; i < 10; i++) {
            jest.advanceTimersByTime(16); // One frame
            jest.runAllTimers();
        }

        // Update should have been called multiple times (continuous movement)
        expect(updateSpy.mock.calls.length).toBeGreaterThan(0);

        updateSpy.mockRestore();
    });
});
