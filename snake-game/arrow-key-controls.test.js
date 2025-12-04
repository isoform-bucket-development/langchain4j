/**
 * Snake Game - Test Suite for Arrow Key Controls
 *
 * Scenario: Arrow Key Controls
 * UUID: d2d8c2c0-f103-40d0-b41e-c8efb761b03e
 *
 * Tests:
 * - Test Case 1: Press ArrowUp key - Snake direction changes to 'up'
 * - Test Case 2: Press ArrowDown key - Snake direction changes to 'down'
 * - Test Case 3: Press ArrowLeft key - Snake direction changes to 'left'
 * - Test Case 4: Press ArrowRight key - Snake direction changes to 'right'
 * - Test Case 5: Measure input response time - Direction change processed within 100ms
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

describe('Snake Game - Arrow Key Controls', () => {
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

        // Create new game instance
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Press ArrowUp key
     * Input: Press ArrowUp key
     * Expected: Snake direction changes to 'up'
     */
    describe('Test Case 1: ArrowUp Key Changes Direction to Up', () => {
        test('should change direction to UP when ArrowUp is pressed', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Initial direction is RIGHT, so UP is valid (not opposite)
            game.handleKeyPress({ key: 'ArrowUp' });

            expect(game.nextDirection).toBe(Direction.UP);
            expect(game.nextDirection).toEqual({ x: 0, y: -1 });
        });

        test('should respond to ArrowUp key event from document', () => {
            game.init();
            game.state = GameState.PLAYING;

            const event = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            document.dispatchEvent(event);

            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should not change to UP when currently moving DOWN (opposite direction)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;

            game.handleKeyPress({ key: 'ArrowUp' });

            // Should not change to opposite direction
            expect(game.nextDirection).not.toBe(Direction.UP);
        });

        test('should accept ArrowUp when moving LEFT', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;

            game.handleKeyPress({ key: 'ArrowUp' });

            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should accept ArrowUp when moving RIGHT', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;

            game.handleKeyPress({ key: 'ArrowUp' });

            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('Direction.UP should have correct vector values', () => {
            expect(Direction.UP.x).toBe(0);
            expect(Direction.UP.y).toBe(-1);
        });
    });

    /**
     * Test Case 2: Press ArrowDown key
     * Input: Press ArrowDown key
     * Expected: Snake direction changes to 'down'
     */
    describe('Test Case 2: ArrowDown Key Changes Direction to Down', () => {
        test('should change direction to DOWN when ArrowDown is pressed', () => {
            game.init();
            game.state = GameState.PLAYING;
            // Set initial direction to LEFT so DOWN is valid
            game.direction = Direction.LEFT;

            game.handleKeyPress({ key: 'ArrowDown' });

            expect(game.nextDirection).toBe(Direction.DOWN);
            expect(game.nextDirection).toEqual({ x: 0, y: 1 });
        });

        test('should respond to ArrowDown key event from document', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;

            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });
            document.dispatchEvent(event);

            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('should not change to DOWN when currently moving UP (opposite direction)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;

            game.handleKeyPress({ key: 'ArrowDown' });

            // Should not change to opposite direction
            expect(game.nextDirection).not.toBe(Direction.DOWN);
        });

        test('should accept ArrowDown when moving LEFT', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;

            game.handleKeyPress({ key: 'ArrowDown' });

            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('should accept ArrowDown when moving RIGHT', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;

            game.handleKeyPress({ key: 'ArrowDown' });

            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('Direction.DOWN should have correct vector values', () => {
            expect(Direction.DOWN.x).toBe(0);
            expect(Direction.DOWN.y).toBe(1);
        });
    });

    /**
     * Test Case 3: Press ArrowLeft key
     * Input: Press ArrowLeft key
     * Expected: Snake direction changes to 'left'
     */
    describe('Test Case 3: ArrowLeft Key Changes Direction to Left', () => {
        test('should change direction to LEFT when ArrowLeft is pressed', () => {
            game.init();
            game.state = GameState.PLAYING;
            // Set initial direction to UP so LEFT is valid
            game.direction = Direction.UP;

            game.handleKeyPress({ key: 'ArrowLeft' });

            expect(game.nextDirection).toBe(Direction.LEFT);
            expect(game.nextDirection).toEqual({ x: -1, y: 0 });
        });

        test('should respond to ArrowLeft key event from document', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;

            const event = new KeyboardEvent('keydown', { key: 'ArrowLeft' });
            document.dispatchEvent(event);

            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('should not change to LEFT when currently moving RIGHT (opposite direction)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;

            game.handleKeyPress({ key: 'ArrowLeft' });

            // Should not change to opposite direction - initial nextDirection is RIGHT
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should accept ArrowLeft when moving UP', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;

            game.handleKeyPress({ key: 'ArrowLeft' });

            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('should accept ArrowLeft when moving DOWN', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;

            game.handleKeyPress({ key: 'ArrowLeft' });

            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('Direction.LEFT should have correct vector values', () => {
            expect(Direction.LEFT.x).toBe(-1);
            expect(Direction.LEFT.y).toBe(0);
        });
    });

    /**
     * Test Case 4: Press ArrowRight key
     * Input: Press ArrowRight key
     * Expected: Snake direction changes to 'right'
     */
    describe('Test Case 4: ArrowRight Key Changes Direction to Right', () => {
        test('should change direction to RIGHT when ArrowRight is pressed', () => {
            game.init();
            game.state = GameState.PLAYING;
            // Set initial direction to UP so RIGHT is valid
            game.direction = Direction.UP;

            game.handleKeyPress({ key: 'ArrowRight' });

            expect(game.nextDirection).toBe(Direction.RIGHT);
            expect(game.nextDirection).toEqual({ x: 1, y: 0 });
        });

        test('should respond to ArrowRight key event from document', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;

            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
            document.dispatchEvent(event);

            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should not change to RIGHT when currently moving LEFT (opposite direction)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            game.handleKeyPress({ key: 'ArrowRight' });

            // Should not change to opposite direction
            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('should accept ArrowRight when moving UP', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;

            game.handleKeyPress({ key: 'ArrowRight' });

            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should accept ArrowRight when moving DOWN', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;

            game.handleKeyPress({ key: 'ArrowRight' });

            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('Direction.RIGHT should have correct vector values', () => {
            expect(Direction.RIGHT.x).toBe(1);
            expect(Direction.RIGHT.y).toBe(0);
        });
    });

    /**
     * Test Case 5: Measure input response time
     * Input: Measure input response time
     * Expected: Direction change processed within 100ms
     */
    describe('Test Case 5: Input Response Time Within 100ms', () => {
        test('should process ArrowUp direction change within 100ms', () => {
            game.init();
            game.state = GameState.PLAYING;

            const startTime = performance.now();
            game.handleKeyPress({ key: 'ArrowUp' });
            const endTime = performance.now();

            const responseTime = endTime - startTime;
            expect(responseTime).toBeLessThan(100);
            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should process ArrowDown direction change within 100ms', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;

            const startTime = performance.now();
            game.handleKeyPress({ key: 'ArrowDown' });
            const endTime = performance.now();

            const responseTime = endTime - startTime;
            expect(responseTime).toBeLessThan(100);
            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('should process ArrowLeft direction change within 100ms', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;

            const startTime = performance.now();
            game.handleKeyPress({ key: 'ArrowLeft' });
            const endTime = performance.now();

            const responseTime = endTime - startTime;
            expect(responseTime).toBeLessThan(100);
            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('should process ArrowRight direction change within 100ms', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;

            const startTime = performance.now();
            game.handleKeyPress({ key: 'ArrowRight' });
            const endTime = performance.now();

            const responseTime = endTime - startTime;
            expect(responseTime).toBeLessThan(100);
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should process multiple rapid direction changes within 100ms each', () => {
            game.init();
            game.state = GameState.PLAYING;

            const directions = [
                { key: 'ArrowUp', expected: Direction.UP, setupDir: Direction.RIGHT },
                { key: 'ArrowDown', expected: Direction.DOWN, setupDir: Direction.LEFT },
                { key: 'ArrowLeft', expected: Direction.LEFT, setupDir: Direction.UP },
                { key: 'ArrowRight', expected: Direction.RIGHT, setupDir: Direction.DOWN }
            ];

            directions.forEach(({ key, expected, setupDir }) => {
                game.direction = setupDir;

                const startTime = performance.now();
                game.handleKeyPress({ key });
                const endTime = performance.now();

                const responseTime = endTime - startTime;
                expect(responseTime).toBeLessThan(100);
                expect(game.nextDirection).toBe(expected);
            });
        });

        test('should process keyboard event from document within 100ms', () => {
            game.init();
            game.state = GameState.PLAYING;

            const startTime = performance.now();
            const event = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            document.dispatchEvent(event);
            const endTime = performance.now();

            const responseTime = endTime - startTime;
            expect(responseTime).toBeLessThan(100);
            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should have no perceivable input lag', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Test 10 consecutive inputs
            const responseTimes = [];
            const keys = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];

            for (let i = 0; i < 10; i++) {
                // Set appropriate initial direction
                if (keys[i % 4] === 'ArrowUp' || keys[i % 4] === 'ArrowDown') {
                    game.direction = Direction.LEFT;
                } else {
                    game.direction = Direction.UP;
                }

                const startTime = performance.now();
                game.handleKeyPress({ key: keys[i % 4] });
                const endTime = performance.now();

                responseTimes.push(endTime - startTime);
            }

            // Average response time should be well under 100ms
            const avgResponseTime = responseTimes.reduce((a, b) => a + b, 0) / responseTimes.length;
            expect(avgResponseTime).toBeLessThan(100);

            // No individual response should exceed 100ms
            responseTimes.forEach(time => {
                expect(time).toBeLessThan(100);
            });
        });
    });

    // Additional integration tests for arrow key controls
    describe('Integration Tests: Arrow Key Controls', () => {
        test('should only respond to arrow keys when game is PLAYING', () => {
            game.init();
            // Game starts in READY state
            expect(game.state).toBe(GameState.READY);

            game.handleKeyPress({ key: 'ArrowUp' });

            // Direction should not change in READY state (any key starts the game instead)
            // After pressing key in READY state, game transitions to PLAYING
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should not respond to arrow keys when game is PAUSED', () => {
            game.init();
            game.state = GameState.PAUSED;
            const initialDirection = game.nextDirection;

            game.handleKeyPress({ key: 'ArrowUp' });

            // Direction should not change in PAUSED state
            expect(game.nextDirection).toBe(initialDirection);
        });

        test('should handle arrow keys correctly after game reset', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Change direction
            game.handleKeyPress({ key: 'ArrowUp' });
            expect(game.nextDirection).toBe(Direction.UP);

            // Reset game
            game.resetGame();
            expect(game.direction).toBe(Direction.RIGHT);
            expect(game.nextDirection).toBe(Direction.RIGHT);

            // Start playing again
            game.state = GameState.PLAYING;
            game.handleKeyPress({ key: 'ArrowUp' });
            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should apply direction change on next game update cycle', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Press ArrowUp
            game.handleKeyPress({ key: 'ArrowUp' });

            // nextDirection is updated immediately
            expect(game.nextDirection).toBe(Direction.UP);

            // Current direction hasn't changed yet
            expect(game.direction).toBe(Direction.RIGHT);

            // Simulate update (direction is applied in update loop)
            game.direction = game.nextDirection;
            expect(game.direction).toBe(Direction.UP);
        });

        test('should prevent 180-degree reversals for all directions', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Test UP cannot go DOWN
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;
            game.handleKeyPress({ key: 'ArrowDown' });
            expect(game.nextDirection).not.toBe(Direction.DOWN);

            // Test DOWN cannot go UP
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;
            game.handleKeyPress({ key: 'ArrowUp' });
            expect(game.nextDirection).not.toBe(Direction.UP);

            // Test LEFT cannot go RIGHT
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;
            game.handleKeyPress({ key: 'ArrowRight' });
            expect(game.nextDirection).not.toBe(Direction.RIGHT);

            // Test RIGHT cannot go LEFT
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;
            game.handleKeyPress({ key: 'ArrowLeft' });
            expect(game.nextDirection).not.toBe(Direction.LEFT);
        });

        test('WASD keys should work as alternative controls', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Test W for UP
            game.direction = Direction.RIGHT;
            game.handleKeyPress({ key: 'w' });
            expect(game.nextDirection).toBe(Direction.UP);

            // Test S for DOWN
            game.direction = Direction.LEFT;
            game.handleKeyPress({ key: 's' });
            expect(game.nextDirection).toBe(Direction.DOWN);

            // Test A for LEFT
            game.direction = Direction.UP;
            game.handleKeyPress({ key: 'a' });
            expect(game.nextDirection).toBe(Direction.LEFT);

            // Test D for RIGHT
            game.direction = Direction.UP;
            game.handleKeyPress({ key: 'd' });
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });
    });
});

// E2E-style integration test for arrow key response time
describe('E2E Test: Arrow Key Input Response Time', () => {
    test('full keyboard input pipeline should respond within 100ms', () => {
        // Create mock context
        const mockCtx = createMockContext();

        // Set up DOM
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

        // Mock canvas getContext
        const canvas = document.getElementById('game-board');
        canvas.getContext = jest.fn(() => mockCtx);

        Object.defineProperty(window, 'localStorage', {
            value: { getItem: jest.fn(), setItem: jest.fn(), clear: jest.fn() },
            writable: true
        });

        const game = new SnakeGame();
        game.init();

        // Start the game
        game.state = GameState.PLAYING;

        // Test all four arrow keys
        const arrowKeys = ['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'];
        const expectedDirections = [Direction.UP, Direction.DOWN, Direction.LEFT, Direction.RIGHT];
        const setupDirections = [Direction.RIGHT, Direction.LEFT, Direction.UP, Direction.DOWN];

        for (let i = 0; i < arrowKeys.length; i++) {
            game.direction = setupDirections[i];

            const startTime = performance.now();

            // Dispatch actual keyboard event
            const event = new KeyboardEvent('keydown', { key: arrowKeys[i] });
            document.dispatchEvent(event);

            const endTime = performance.now();
            const responseTime = endTime - startTime;

            // Verify response time is under 100ms
            expect(responseTime).toBeLessThan(100);

            // Verify direction changed correctly
            expect(game.nextDirection).toBe(expectedDirections[i]);

            console.log(`${arrowKeys[i]} response time: ${responseTime.toFixed(2)}ms`);
        }
    });

    test('snake should move in new direction after arrow key press', () => {
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

        Object.defineProperty(window, 'localStorage', {
            value: { getItem: jest.fn(), setItem: jest.fn(), clear: jest.fn() },
            writable: true
        });

        const game = new SnakeGame();
        game.init();
        game.state = GameState.PLAYING;

        // Get initial head position
        const initialHeadX = game.snake[0].x;
        const initialHeadY = game.snake[0].y;

        // Press ArrowUp
        const event = new KeyboardEvent('keydown', { key: 'ArrowUp' });
        document.dispatchEvent(event);

        // Apply direction change (simulating update cycle)
        game.direction = game.nextDirection;

        // Move snake
        game.moveSnake();

        // Verify snake moved up (y decreased)
        expect(game.snake[0].x).toBe(initialHeadX);
        expect(game.snake[0].y).toBe(initialHeadY - 1);
    });
});
