/**
 * Snake Game - Test Suite for Prevent Reverse Direction
 *
 * Scenario: Prevent Reverse Direction
 * UUID: 3a5c5793-d425-4ac4-a6f0-f09281305a21
 *
 * Description: Verify that the snake cannot reverse directly into itself (180-degree turn)
 *
 * Steps:
 * 1. Start game with snake moving right (Snake has length > 1)
 * 2. Press Left arrow (Attempt to reverse direction by pressing opposite direction)
 * 3. Verify direction unchanged (Confirm snake continues moving right, ignoring invalid input)
 *
 * Test Cases:
 * - Test Case 1: Moving right, press Left - Snake continues moving right (reverse blocked)
 * - Test Case 2: Moving left, press Right - Snake continues moving left (reverse blocked)
 * - Test Case 3: Moving up, press Down - Snake continues moving up (reverse blocked)
 * - Test Case 4: Moving down, press Up - Snake continues moving down (reverse blocked)
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

describe('Snake Game - Prevent Reverse Direction', () => {
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
     * Test Case 1: Moving right, press Left
     * Input: Snake is moving right, then Left arrow is pressed
     * Expected: Snake continues moving right (reverse blocked)
     */
    describe('Test Case 1: Moving Right, Press Left - Reverse Blocked', () => {
        test('should block reverse when moving RIGHT and pressing ArrowLeft', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Attempt to reverse direction by pressing Left
            game.handleKeyPress({ key: 'ArrowLeft' });

            // Snake should continue moving RIGHT, reverse blocked
            expect(game.nextDirection).toBe(Direction.RIGHT);
            expect(game.nextDirection).not.toBe(Direction.LEFT);
        });

        test('should block reverse when moving RIGHT and dispatching ArrowLeft event', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Dispatch keyboard event
            const event = new KeyboardEvent('keydown', { key: 'ArrowLeft' });
            document.dispatchEvent(event);

            // Snake should continue moving RIGHT
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should block reverse when moving RIGHT and pressing "a" key (WASD)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Attempt to reverse using WASD control
            game.handleKeyPress({ key: 'a' });

            // Snake should continue moving RIGHT
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should block reverse when moving RIGHT and pressing "A" key (uppercase)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Attempt to reverse using uppercase WASD control
            game.handleKeyPress({ key: 'A' });

            // Snake should continue moving RIGHT
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('snake should have length > 1 when reverse is blocked', () => {
            game.init();

            // Verify snake has length > 1 (prerequisite for scenario)
            expect(game.snake.length).toBeGreaterThan(1);

            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 'ArrowLeft' });

            // Reverse should be blocked
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });
    });

    /**
     * Test Case 2: Moving left, press Right
     * Input: Snake is moving left, then Right arrow is pressed
     * Expected: Snake continues moving left (reverse blocked)
     */
    describe('Test Case 2: Moving Left, Press Right - Reverse Blocked', () => {
        test('should block reverse when moving LEFT and pressing ArrowRight', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            // Attempt to reverse direction by pressing Right
            game.handleKeyPress({ key: 'ArrowRight' });

            // Snake should continue moving LEFT, reverse blocked
            expect(game.nextDirection).toBe(Direction.LEFT);
            expect(game.nextDirection).not.toBe(Direction.RIGHT);
        });

        test('should block reverse when moving LEFT and dispatching ArrowRight event', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            // Dispatch keyboard event
            const event = new KeyboardEvent('keydown', { key: 'ArrowRight' });
            document.dispatchEvent(event);

            // Snake should continue moving LEFT
            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('should block reverse when moving LEFT and pressing "d" key (WASD)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            // Attempt to reverse using WASD control
            game.handleKeyPress({ key: 'd' });

            // Snake should continue moving LEFT
            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('should block reverse when moving LEFT and pressing "D" key (uppercase)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            // Attempt to reverse using uppercase WASD control
            game.handleKeyPress({ key: 'D' });

            // Snake should continue moving LEFT
            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('snake should have length > 1 when reverse is blocked (LEFT)', () => {
            game.init();

            // Verify snake has length > 1
            expect(game.snake.length).toBeGreaterThan(1);

            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            game.handleKeyPress({ key: 'ArrowRight' });

            // Reverse should be blocked
            expect(game.nextDirection).toBe(Direction.LEFT);
        });
    });

    /**
     * Test Case 3: Moving up, press Down
     * Input: Snake is moving up, then Down arrow is pressed
     * Expected: Snake continues moving up (reverse blocked)
     */
    describe('Test Case 3: Moving Up, Press Down - Reverse Blocked', () => {
        test('should block reverse when moving UP and pressing ArrowDown', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            // Attempt to reverse direction by pressing Down
            game.handleKeyPress({ key: 'ArrowDown' });

            // Snake should continue moving UP, reverse blocked
            expect(game.nextDirection).toBe(Direction.UP);
            expect(game.nextDirection).not.toBe(Direction.DOWN);
        });

        test('should block reverse when moving UP and dispatching ArrowDown event', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            // Dispatch keyboard event
            const event = new KeyboardEvent('keydown', { key: 'ArrowDown' });
            document.dispatchEvent(event);

            // Snake should continue moving UP
            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should block reverse when moving UP and pressing "s" key (WASD)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            // Attempt to reverse using WASD control
            game.handleKeyPress({ key: 's' });

            // Snake should continue moving UP
            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should block reverse when moving UP and pressing "S" key (uppercase)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            // Attempt to reverse using uppercase WASD control
            game.handleKeyPress({ key: 'S' });

            // Snake should continue moving UP
            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('snake should have length > 1 when reverse is blocked (UP)', () => {
            game.init();

            // Verify snake has length > 1
            expect(game.snake.length).toBeGreaterThan(1);

            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'ArrowDown' });

            // Reverse should be blocked
            expect(game.nextDirection).toBe(Direction.UP);
        });
    });

    /**
     * Test Case 4: Moving down, press Up
     * Input: Snake is moving down, then Up arrow is pressed
     * Expected: Snake continues moving down (reverse blocked)
     */
    describe('Test Case 4: Moving Down, Press Up - Reverse Blocked', () => {
        test('should block reverse when moving DOWN and pressing ArrowUp', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;

            // Attempt to reverse direction by pressing Up
            game.handleKeyPress({ key: 'ArrowUp' });

            // Snake should continue moving DOWN, reverse blocked
            expect(game.nextDirection).toBe(Direction.DOWN);
            expect(game.nextDirection).not.toBe(Direction.UP);
        });

        test('should block reverse when moving DOWN and dispatching ArrowUp event', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;

            // Dispatch keyboard event
            const event = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            document.dispatchEvent(event);

            // Snake should continue moving DOWN
            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('should block reverse when moving DOWN and pressing "w" key (WASD)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;

            // Attempt to reverse using WASD control
            game.handleKeyPress({ key: 'w' });

            // Snake should continue moving DOWN
            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('should block reverse when moving DOWN and pressing "W" key (uppercase)', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;

            // Attempt to reverse using uppercase WASD control
            game.handleKeyPress({ key: 'W' });

            // Snake should continue moving DOWN
            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('snake should have length > 1 when reverse is blocked (DOWN)', () => {
            game.init();

            // Verify snake has length > 1
            expect(game.snake.length).toBeGreaterThan(1);

            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;

            game.handleKeyPress({ key: 'ArrowUp' });

            // Reverse should be blocked
            expect(game.nextDirection).toBe(Direction.DOWN);
        });
    });

    // Additional integration tests for reverse direction prevention
    describe('Integration Tests: Reverse Direction Prevention', () => {
        test('should allow perpendicular direction changes after blocking reverse', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Try to reverse (should be blocked)
            game.handleKeyPress({ key: 'ArrowLeft' });
            expect(game.nextDirection).toBe(Direction.RIGHT);

            // Try perpendicular direction (should be allowed)
            game.handleKeyPress({ key: 'ArrowUp' });
            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should block multiple consecutive reverse attempts', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Multiple reverse attempts
            game.handleKeyPress({ key: 'ArrowLeft' });
            game.handleKeyPress({ key: 'ArrowLeft' });
            game.handleKeyPress({ key: 'a' });
            game.handleKeyPress({ key: 'A' });

            // All attempts should be blocked
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('snake continues moving in original direction after reverse blocked', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            const initialHeadX = game.snake[0].x;
            const initialHeadY = game.snake[0].y;

            // Attempt to reverse
            game.handleKeyPress({ key: 'ArrowLeft' });

            // Apply direction (simulating game loop)
            game.direction = game.nextDirection;

            // Move snake
            game.moveSnake();

            // Snake should have moved RIGHT (x increased), not LEFT
            expect(game.snake[0].x).toBe(initialHeadX + 1);
            expect(game.snake[0].y).toBe(initialHeadY);
        });

        test('reverse blocking works correctly through full game cycle', () => {
            game.init();
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            const initialHeadX = game.snake[0].x;
            const initialHeadY = game.snake[0].y;

            // Attempt reverse (DOWN when moving UP)
            game.handleKeyPress({ key: 'ArrowDown' });

            // Simulate update cycle - direction is applied
            game.direction = game.nextDirection;

            // Move snake
            game.moveSnake();

            // Snake should have moved UP (y decreased), not DOWN
            expect(game.snake[0].x).toBe(initialHeadX);
            expect(game.snake[0].y).toBe(initialHeadY - 1);
        });
    });
});

// E2E-style integration test for reverse direction prevention
describe('E2E Test: Reverse Direction Prevention', () => {
    test('full scenario: start moving right, press left, verify direction unchanged', () => {
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

        // Step 1: Verify snake has length > 1
        expect(game.snake.length).toBeGreaterThan(1);

        // Step 1: Start game with snake moving right
        game.state = GameState.PLAYING;
        expect(game.direction).toEqual(Direction.RIGHT);

        // Step 2: Press Left arrow (attempt to reverse)
        const event = new KeyboardEvent('keydown', { key: 'ArrowLeft' });
        document.dispatchEvent(event);

        // Step 3: Verify direction unchanged - snake continues moving right
        expect(game.nextDirection).toBe(Direction.RIGHT);
        expect(game.nextDirection).not.toBe(Direction.LEFT);

        console.log('E2E Test passed: Reverse direction correctly blocked');
    });

    test('all four reverse direction scenarios in sequence', () => {
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

        // Test all 4 reverse scenarios
        const scenarios = [
            { current: Direction.RIGHT, reverseKey: 'ArrowLeft', expected: Direction.RIGHT },
            { current: Direction.LEFT, reverseKey: 'ArrowRight', expected: Direction.LEFT },
            { current: Direction.UP, reverseKey: 'ArrowDown', expected: Direction.UP },
            { current: Direction.DOWN, reverseKey: 'ArrowUp', expected: Direction.DOWN }
        ];

        scenarios.forEach(({ current, reverseKey, expected }) => {
            game.direction = current;
            game.nextDirection = current;

            const event = new KeyboardEvent('keydown', { key: reverseKey });
            document.dispatchEvent(event);

            expect(game.nextDirection).toBe(expected);
        });

        console.log('E2E Test passed: All 4 reverse direction scenarios correctly blocked');
    });
});
