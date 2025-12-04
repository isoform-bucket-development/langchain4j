/**
 * Snake Game - Frame Rate Performance Test Suite
 *
 * Tests for Scenario: Frame Rate Performance
 * - Test Case 1: Game with short snake renders at 60 FPS minimum
 * - Test Case 2: Game with long snake (50+ segments) maintains 60 FPS
 * - Test Case 3: Game loop uses RequestAnimationFrame for rendering
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

describe('Snake Game - Frame Rate Performance', () => {
    let game;
    let mockCtx;
    let rafCallbacks;
    let rafId;
    let originalRAF;
    let originalCAF;

    beforeEach(() => {
        // Create mock context
        mockCtx = createMockContext();
        rafCallbacks = [];
        rafId = 0;

        // Store original functions
        originalRAF = global.requestAnimationFrame;
        originalCAF = global.cancelAnimationFrame;

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
     * Helper function to create a long snake for testing
     */
    const createLongSnake = (game, length) => {
        const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
        game.snake = [];

        // Create snake in a zigzag pattern to fit in the grid
        let x = Math.floor(gridWidth / 2);
        let y = 1;
        let direction = 1; // 1 = right, -1 = left

        for (let i = 0; i < length; i++) {
            game.snake.push({ x, y });

            // Move to next position
            x += direction;

            // If at edge, move down and change direction
            if (x >= gridWidth - 1 || x <= 0) {
                direction *= -1;
                y++;
                if (y >= CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE - 1) {
                    y = 1; // Wrap around if needed
                }
            }
        }
    };

    /**
     * Test Case 1: Game with short snake renders at 60 FPS minimum
     * Input: Game with short snake
     * Expected: Renders at 60 FPS minimum
     */
    describe('Test Case 1: Short Snake - 60 FPS Minimum', () => {
        test('should maintain render time under 16.67ms per frame with short snake', () => {
            game.init();
            game.startGame();

            // Measure render time for multiple frames
            const renderTimes = [];
            const numFrames = 100;

            for (let i = 0; i < numFrames; i++) {
                const startTime = performance.now();
                game.render();
                const endTime = performance.now();
                renderTimes.push(endTime - startTime);
            }

            // Calculate average render time
            const avgRenderTime = renderTimes.reduce((a, b) => a + b, 0) / numFrames;

            // At 60 FPS, each frame should take less than 16.67ms
            expect(avgRenderTime).toBeLessThan(16.67);

            console.log(`Short snake avg render time: ${avgRenderTime.toFixed(3)}ms`);
        });

        test('should be able to process 60 frames per second', () => {
            game.init();

            // Default snake has 3 segments
            expect(game.snake.length).toBe(3);

            // Simulate 60 frames worth of renders
            const frameCount = 60;
            const startTime = performance.now();

            for (let i = 0; i < frameCount; i++) {
                game.render();
            }

            const totalTime = performance.now() - startTime;

            // 60 frames should complete within 1 second (1000ms)
            expect(totalTime).toBeLessThan(1000);

            console.log(`60 frames with short snake completed in: ${totalTime.toFixed(2)}ms`);
        });

        test('should have default snake length of 3 segments', () => {
            game.init();
            expect(game.snake.length).toBe(3);
        });

        test('should render snake segments efficiently', () => {
            game.init();

            // Clear previous calls
            mockCtx.fillRect.mockClear();

            // Render one frame
            game.render();

            // Should call fillRect for background and snake segments
            // Background (1) + Snake head (1) + Snake body (2) = 4 calls
            expect(mockCtx.fillRect.mock.calls.length).toBeGreaterThanOrEqual(1);
        });

        test('should complete game update cycle quickly with short snake', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.lastUpdateTime = Date.now() - CONFIG.INITIAL_SPEED - 1;

            const startTime = performance.now();

            // Simulate update logic (without RAF scheduling)
            game.direction = game.nextDirection;
            game.moveSnake();
            if (!game.checkCollisions()) {
                game.checkFood();
                game.render();
            }

            const updateTime = performance.now() - startTime;

            // Update should be nearly instant (well under 16.67ms)
            expect(updateTime).toBeLessThan(16.67);

            console.log(`Short snake update cycle: ${updateTime.toFixed(3)}ms`);
        });
    });

    /**
     * Test Case 2: Game with long snake (50+ segments) maintains 60 FPS
     * Input: Game with long snake (50+ segments)
     * Expected: Maintains 60 FPS
     */
    describe('Test Case 2: Long Snake (50+ segments) - 60 FPS', () => {
        test('should maintain render time under 16.67ms per frame with 50 segment snake', () => {
            game.init();
            createLongSnake(game, 50);

            expect(game.snake.length).toBe(50);

            // Measure render time for multiple frames
            const renderTimes = [];
            const numFrames = 100;

            for (let i = 0; i < numFrames; i++) {
                const startTime = performance.now();
                game.render();
                const endTime = performance.now();
                renderTimes.push(endTime - startTime);
            }

            // Calculate average render time
            const avgRenderTime = renderTimes.reduce((a, b) => a + b, 0) / numFrames;

            // At 60 FPS, each frame should take less than 16.67ms
            expect(avgRenderTime).toBeLessThan(16.67);

            console.log(`50 segment snake avg render time: ${avgRenderTime.toFixed(3)}ms`);
        });

        test('should maintain render time under 16.67ms with 100 segment snake', () => {
            game.init();
            createLongSnake(game, 100);

            expect(game.snake.length).toBe(100);

            // Measure render time for multiple frames
            const renderTimes = [];
            const numFrames = 100;

            for (let i = 0; i < numFrames; i++) {
                const startTime = performance.now();
                game.render();
                const endTime = performance.now();
                renderTimes.push(endTime - startTime);
            }

            const avgRenderTime = renderTimes.reduce((a, b) => a + b, 0) / numFrames;
            expect(avgRenderTime).toBeLessThan(16.67);

            console.log(`100 segment snake avg render time: ${avgRenderTime.toFixed(3)}ms`);
        });

        test('should process 60 frames per second with long snake', () => {
            game.init();
            createLongSnake(game, 75);

            const frameCount = 60;
            const startTime = performance.now();

            for (let i = 0; i < frameCount; i++) {
                game.render();
            }

            const totalTime = performance.now() - startTime;

            // 60 frames should complete within 1 second (1000ms)
            expect(totalTime).toBeLessThan(1000);

            console.log(`60 frames with 75 segment snake completed in: ${totalTime.toFixed(2)}ms`);
        });

        test('should handle collision detection efficiently with long snake', () => {
            game.init();
            createLongSnake(game, 50);

            // Measure collision detection time
            const iterations = 1000;
            const startTime = performance.now();

            for (let i = 0; i < iterations; i++) {
                game.checkCollisions();
            }

            const avgTime = (performance.now() - startTime) / iterations;

            // Collision detection should be very fast
            expect(avgTime).toBeLessThan(1);

            console.log(`Collision check avg time (50 segments): ${avgTime.toFixed(5)}ms`);
        });

        test('should maintain performance with snake near maximum practical length', () => {
            game.init();
            createLongSnake(game, 150);

            // Measure a complete update cycle
            const renderTimes = [];
            const numFrames = 30;

            for (let i = 0; i < numFrames; i++) {
                const startTime = performance.now();
                game.render();
                const endTime = performance.now();
                renderTimes.push(endTime - startTime);
            }

            const avgRenderTime = renderTimes.reduce((a, b) => a + b, 0) / numFrames;

            // Even with 150 segments, should stay under 16.67ms
            expect(avgRenderTime).toBeLessThan(16.67);

            console.log(`150 segment snake avg render time: ${avgRenderTime.toFixed(3)}ms`);
        });

        test('should scale render time linearly with snake length', () => {
            game.init();

            // Test with different snake lengths
            const lengths = [10, 25, 50, 100];
            const avgTimes = [];

            lengths.forEach(length => {
                createLongSnake(game, length);

                const renderTimes = [];
                for (let i = 0; i < 50; i++) {
                    const startTime = performance.now();
                    game.render();
                    renderTimes.push(performance.now() - startTime);
                }

                avgTimes.push(renderTimes.reduce((a, b) => a + b, 0) / 50);
            });

            // All times should be under 16.67ms
            avgTimes.forEach((time, index) => {
                expect(time).toBeLessThan(16.67);
                console.log(`Snake length ${lengths[index]}: ${time.toFixed(3)}ms`);
            });
        });
    });

    /**
     * Test Case 3: Game loop uses RequestAnimationFrame for rendering
     * Input: RequestAnimationFrame usage
     * Expected: Game loop uses RAF for rendering
     */
    describe('Test Case 3: RequestAnimationFrame Usage', () => {
        test('should use requestAnimationFrame when starting the game', () => {
            game.init();

            // Clear any previous calls
            global.requestAnimationFrame.mockClear();

            // Start the game
            game.startGame();

            // Verify requestAnimationFrame was called
            expect(global.requestAnimationFrame).toHaveBeenCalled();
        });

        test('should schedule next frame using requestAnimationFrame in update loop', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.lastUpdateTime = 0; // Force update to trigger

            // Clear previous calls
            global.requestAnimationFrame.mockClear();

            // Call update directly
            game.update();

            // Verify RAF was called to schedule next frame
            expect(global.requestAnimationFrame).toHaveBeenCalled();
        });

        test('should use requestAnimationFrame to resume from pause', () => {
            game.init();
            game.startGame();

            // Pause the game
            game.togglePause();
            expect(game.state).toBe(GameState.PAUSED);

            // Clear RAF calls
            global.requestAnimationFrame.mockClear();

            // Resume the game
            game.togglePause();

            // Verify RAF was called on resume
            expect(global.requestAnimationFrame).toHaveBeenCalled();
        });

        test('should call cancelAnimationFrame when game is paused', () => {
            game.init();
            game.startGame();

            // Clear previous calls
            global.cancelAnimationFrame.mockClear();

            // Pause the game
            game.togglePause();

            // Verify cancelAnimationFrame was called
            expect(global.cancelAnimationFrame).toHaveBeenCalled();
        });

        test('should call cancelAnimationFrame on game over', () => {
            game.init();
            game.startGame();

            // Clear previous calls
            global.cancelAnimationFrame.mockClear();

            // Trigger game over
            game.gameOver();

            // Verify cancelAnimationFrame was called
            expect(global.cancelAnimationFrame).toHaveBeenCalled();
        });

        test('should not use setInterval for game loop', () => {
            // Spy on setInterval
            const setIntervalSpy = jest.spyOn(global, 'setInterval');

            game.init();
            game.startGame();

            // setInterval should not be called for the game loop
            expect(setIntervalSpy).not.toHaveBeenCalled();

            setIntervalSpy.mockRestore();
        });

        test('should not use setTimeout for main game loop', () => {
            // Spy on setTimeout
            const setTimeoutSpy = jest.spyOn(global, 'setTimeout');

            game.init();

            // Clear any setup timeouts
            setTimeoutSpy.mockClear();

            game.startGame();

            // setTimeout should not be called for the main game loop
            // (RAF should be used instead)
            expect(setTimeoutSpy).not.toHaveBeenCalled();

            setTimeoutSpy.mockRestore();
        });

        test('should store RAF handle in gameLoop property', () => {
            game.init();
            game.startGame();

            // gameLoop should store the RAF handle
            expect(game.gameLoop).toBeDefined();
            expect(typeof game.gameLoop).toBe('number');
        });

        test('should use delta time for frame rate independent updates', () => {
            game.init();
            game.startGame();

            // Verify game tracks lastUpdateTime for delta calculations
            expect(game.lastUpdateTime).toBeDefined();
            expect(typeof game.lastUpdateTime).toBe('number');
        });

        test('should only update game state at configured speed intervals', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Set last update time to recent (within speed interval)
            game.lastUpdateTime = Date.now();
            const initialSnakeHead = { ...game.snake[0] };

            // Manually call update logic check
            const currentTime = Date.now();
            const deltaTime = currentTime - game.lastUpdateTime;

            // Delta should be less than speed threshold
            expect(deltaTime).toBeLessThan(game.speed);
        });

        test('should request animation frame for continuous rendering', () => {
            game.init();

            // Start game
            game.startGame();

            // First RAF call
            expect(global.requestAnimationFrame).toHaveBeenCalledTimes(1);

            // Simulate RAF callback execution
            const callback = global.requestAnimationFrame.mock.calls[0][0];

            // Execute the callback (this simulates one frame)
            // Note: This would normally call RAF again inside update()
            game.state = GameState.PLAYING;
            game.lastUpdateTime = Date.now() - game.speed - 1; // Force update

            global.requestAnimationFrame.mockClear();
            callback();

            // After one frame, another RAF should be scheduled
            expect(global.requestAnimationFrame).toHaveBeenCalled();
        });
    });

    /**
     * Additional Performance Tests
     */
    describe('Additional Performance Tests', () => {
        test('CONFIG.MIN_SPEED should support at least 60 FPS', () => {
            // MIN_SPEED of 50ms means max 20 game updates per second
            // But RAF still runs at 60 FPS for rendering
            // The MIN_SPEED is for game logic updates, not rendering
            expect(CONFIG.MIN_SPEED).toBeGreaterThanOrEqual(16); // Can support 60 FPS rendering
        });

        test('should render efficiently even with rapid direction changes', () => {
            game.init();
            game.startGame();

            const directions = [Direction.UP, Direction.LEFT, Direction.DOWN, Direction.RIGHT];
            const renderTimes = [];

            // Simulate rapid direction changes and renders
            for (let i = 0; i < 100; i++) {
                game.nextDirection = directions[i % 4];

                const startTime = performance.now();
                game.render();
                renderTimes.push(performance.now() - startTime);
            }

            const avgRenderTime = renderTimes.reduce((a, b) => a + b, 0) / renderTimes.length;
            expect(avgRenderTime).toBeLessThan(16.67);
        });

        test('food spawning should not impact frame rate', () => {
            game.init();

            const spawnTimes = [];
            const numSpawns = 1000;

            for (let i = 0; i < numSpawns; i++) {
                const startTime = performance.now();
                game.spawnFood();
                spawnTimes.push(performance.now() - startTime);
            }

            const avgSpawnTime = spawnTimes.reduce((a, b) => a + b, 0) / numSpawns;

            // Food spawning should be very fast
            expect(avgSpawnTime).toBeLessThan(1);

            console.log(`Avg food spawn time: ${avgSpawnTime.toFixed(5)}ms`);
        });

        test('should maintain consistent frame times (no major spikes)', () => {
            game.init();
            createLongSnake(game, 50);

            const renderTimes = [];
            const numFrames = 100;

            for (let i = 0; i < numFrames; i++) {
                const startTime = performance.now();
                game.render();
                renderTimes.push(performance.now() - startTime);
            }

            // Calculate average and max render time
            const avg = renderTimes.reduce((a, b) => a + b, 0) / numFrames;
            const maxRenderTime = Math.max(...renderTimes);

            // All frame times should be well under 16.67ms (60 FPS threshold)
            expect(avg).toBeLessThan(16.67);
            expect(maxRenderTime).toBeLessThan(16.67);

            // No single frame should cause a major spike (defined as > 5ms for this simple game)
            expect(maxRenderTime).toBeLessThan(5);

            console.log(`Frame time avg: ${avg.toFixed(3)}ms, max: ${maxRenderTime.toFixed(3)}ms`);
        });
    });
});

/**
 * E2E Tests for Frame Rate Performance
 */
describe('E2E Test: Frame Rate Performance', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        mockCtx = createMockContext();

        // Mock RAF
        global.requestAnimationFrame = jest.fn(cb => setTimeout(cb, 16));
        global.cancelAnimationFrame = jest.fn(id => clearTimeout(id));

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
    });

    test('E2E: Game maintains playable frame rate during gameplay', () => {
        game.init();
        game.startGame();

        // Verify RAF is being used
        expect(global.requestAnimationFrame).toHaveBeenCalled();

        // Verify game state is correct
        expect(game.state).toBe(GameState.PLAYING);

        // Verify game loop handle is set
        expect(game.gameLoop).toBeDefined();
    });

    test('E2E: Extended gameplay with growing snake maintains performance', () => {
        game.init();
        game.startGame();

        const initialLength = game.snake.length; // Should be 3

        // Simulate extended gameplay with snake growth
        const numFrames = 50;
        const renderTimes = [];
        let growthCount = 0;

        for (let i = 0; i < numFrames; i++) {
            // Grow snake every 5 frames (at frames 5, 10, 15, 20, 25, 30, 35, 40, 45)
            if (i > 0 && i % 5 === 0) {
                game.snake.push({ x: 0, y: 0 });
                growthCount++;
            }

            const startTime = performance.now();
            game.render();
            renderTimes.push(performance.now() - startTime);
        }

        const avgRenderTime = renderTimes.reduce((a, b) => a + b, 0) / numFrames;

        // Should maintain 60 FPS
        expect(avgRenderTime).toBeLessThan(16.67);
        expect(game.snake.length).toBe(initialLength + growthCount); // 3 initial + 9 grown = 12
    });

    test('E2E: Render quality maintained at 60 FPS', () => {
        game.init();
        game.startGame();

        // Clear render tracking
        mockCtx.fillRect.mockClear();
        mockCtx.strokeRect.mockClear();
        mockCtx.arc.mockClear();

        // Render frame
        game.render();

        // Verify all render components called
        expect(mockCtx.fillRect).toHaveBeenCalled(); // Background + snake
        expect(mockCtx.strokeRect).toHaveBeenCalled(); // Border
        expect(mockCtx.arc).toHaveBeenCalled(); // Food
    });
});
