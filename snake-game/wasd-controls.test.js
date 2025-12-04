/**
 * Snake Game - Test Suite for WASD Alternative Controls
 *
 * Scenario: WASD Alternative Controls
 * UUID: 651eda3b-22cb-4d7c-b3a8-ce70db10b3a3
 *
 * Tests:
 * - Test Case 1: Press W key - Snake direction changes to 'up'
 * - Test Case 2: Press S key - Snake direction changes to 'down'
 * - Test Case 3: Press A key - Snake direction changes to 'left'
 * - Test Case 4: Press D key - Snake direction changes to 'right'
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

describe('Snake Game - WASD Alternative Controls', () => {
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
        game.init();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Press W key
     * Input: Press W key
     * Expected: Snake direction changes to 'up'
     */
    describe('Test Case 1: W key for Up direction', () => {
        test('should change direction to UP when W key is pressed (lowercase)', () => {
            // Set game to playing state
            game.state = GameState.PLAYING;
            // Make sure snake is moving horizontally (not down) so UP is valid
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Press 'w' key
            game.handleKeyPress({ key: 'w' });

            expect(game.nextDirection).toBe(Direction.UP);
            expect(game.nextDirection).toEqual({ x: 0, y: -1 });
        });

        test('should change direction to UP when W key is pressed (uppercase)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Press 'W' key
            game.handleKeyPress({ key: 'W' });

            expect(game.nextDirection).toBe(Direction.UP);
            expect(game.nextDirection).toEqual({ x: 0, y: -1 });
        });

        test('should not change to UP when snake is moving DOWN (prevent reverse)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;

            // Try to press 'w' key
            game.handleKeyPress({ key: 'w' });

            // Direction should remain DOWN (cannot reverse)
            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('should allow W key when moving LEFT', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            game.handleKeyPress({ key: 'w' });

            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('W key should behave same as ArrowUp', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 'w' });
            const wDirection = { ...game.nextDirection };

            // Reset
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 'ArrowUp' });
            const arrowDirection = { ...game.nextDirection };

            expect(wDirection).toEqual(arrowDirection);
        });
    });

    /**
     * Test Case 2: Press S key
     * Input: Press S key
     * Expected: Snake direction changes to 'down'
     */
    describe('Test Case 2: S key for Down direction', () => {
        test('should change direction to DOWN when S key is pressed (lowercase)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 's' });

            expect(game.nextDirection).toBe(Direction.DOWN);
            expect(game.nextDirection).toEqual({ x: 0, y: 1 });
        });

        test('should change direction to DOWN when S key is pressed (uppercase)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 'S' });

            expect(game.nextDirection).toBe(Direction.DOWN);
            expect(game.nextDirection).toEqual({ x: 0, y: 1 });
        });

        test('should not change to DOWN when snake is moving UP (prevent reverse)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 's' });

            // Direction should remain UP (cannot reverse)
            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should allow S key when moving LEFT', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            game.handleKeyPress({ key: 's' });

            expect(game.nextDirection).toBe(Direction.DOWN);
        });

        test('S key should behave same as ArrowDown', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 's' });
            const sDirection = { ...game.nextDirection };

            // Reset
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 'ArrowDown' });
            const arrowDirection = { ...game.nextDirection };

            expect(sDirection).toEqual(arrowDirection);
        });
    });

    /**
     * Test Case 3: Press A key
     * Input: Press A key
     * Expected: Snake direction changes to 'left'
     */
    describe('Test Case 3: A key for Left direction', () => {
        test('should change direction to LEFT when A key is pressed (lowercase)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'a' });

            expect(game.nextDirection).toBe(Direction.LEFT);
            expect(game.nextDirection).toEqual({ x: -1, y: 0 });
        });

        test('should change direction to LEFT when A key is pressed (uppercase)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'A' });

            expect(game.nextDirection).toBe(Direction.LEFT);
            expect(game.nextDirection).toEqual({ x: -1, y: 0 });
        });

        test('should not change to LEFT when snake is moving RIGHT (prevent reverse)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 'a' });

            // Direction should remain RIGHT (cannot reverse)
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should allow A key when moving DOWN', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;

            game.handleKeyPress({ key: 'a' });

            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('A key should behave same as ArrowLeft', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'a' });
            const aDirection = { ...game.nextDirection };

            // Reset
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'ArrowLeft' });
            const arrowDirection = { ...game.nextDirection };

            expect(aDirection).toEqual(arrowDirection);
        });
    });

    /**
     * Test Case 4: Press D key
     * Input: Press D key
     * Expected: Snake direction changes to 'right'
     */
    describe('Test Case 4: D key for Right direction', () => {
        test('should change direction to RIGHT when D key is pressed (lowercase)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'd' });

            expect(game.nextDirection).toBe(Direction.RIGHT);
            expect(game.nextDirection).toEqual({ x: 1, y: 0 });
        });

        test('should change direction to RIGHT when D key is pressed (uppercase)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'D' });

            expect(game.nextDirection).toBe(Direction.RIGHT);
            expect(game.nextDirection).toEqual({ x: 1, y: 0 });
        });

        test('should not change to RIGHT when snake is moving LEFT (prevent reverse)', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.LEFT;
            game.nextDirection = Direction.LEFT;

            game.handleKeyPress({ key: 'd' });

            // Direction should remain LEFT (cannot reverse)
            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('should allow D key when moving DOWN', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.DOWN;
            game.nextDirection = Direction.DOWN;

            game.handleKeyPress({ key: 'd' });

            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('D key should behave same as ArrowRight', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.UP;
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'd' });
            const dDirection = { ...game.nextDirection };

            // Reset
            game.nextDirection = Direction.UP;

            game.handleKeyPress({ key: 'ArrowRight' });
            const arrowDirection = { ...game.nextDirection };

            expect(dDirection).toEqual(arrowDirection);
        });
    });

    /**
     * Integration tests for WASD controls
     */
    describe('Integration: WASD Controls', () => {
        test('should only respond to WASD keys when game is in PLAYING state', () => {
            // Game starts in READY state
            expect(game.state).toBe(GameState.READY);

            const initialDirection = { ...game.nextDirection };

            // Press WASD keys in READY state - should not change direction
            game.handleKeyPress({ key: 'w' });

            // In READY state, any key starts the game
            // So direction shouldn't change but state should change to PLAYING
            expect(game.state).toBe(GameState.PLAYING);
        });

        test('should support rapid direction changes with WASD', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Press W (up)
            game.handleKeyPress({ key: 'w' });
            expect(game.nextDirection).toBe(Direction.UP);

            // Update direction
            game.direction = Direction.UP;

            // Press A (left) - should work since not moving right
            game.handleKeyPress({ key: 'a' });
            expect(game.nextDirection).toBe(Direction.LEFT);
        });

        test('should handle keyboard events from DOM', () => {
            game.state = GameState.PLAYING;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Simulate real keyboard event
            const event = new KeyboardEvent('keydown', { key: 'w' });
            document.dispatchEvent(event);

            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('all WASD keys should map to correct directions', () => {
            const keyMappings = [
                { key: 'w', expected: Direction.UP, setupDirection: Direction.RIGHT },
                { key: 'W', expected: Direction.UP, setupDirection: Direction.RIGHT },
                { key: 's', expected: Direction.DOWN, setupDirection: Direction.RIGHT },
                { key: 'S', expected: Direction.DOWN, setupDirection: Direction.RIGHT },
                { key: 'a', expected: Direction.LEFT, setupDirection: Direction.UP },
                { key: 'A', expected: Direction.LEFT, setupDirection: Direction.UP },
                { key: 'd', expected: Direction.RIGHT, setupDirection: Direction.UP },
                { key: 'D', expected: Direction.RIGHT, setupDirection: Direction.UP }
            ];

            keyMappings.forEach(({ key, expected, setupDirection }) => {
                game.state = GameState.PLAYING;
                game.direction = setupDirection;
                game.nextDirection = setupDirection;

                game.handleKeyPress({ key });

                expect(game.nextDirection).toBe(expected);
            });
        });

        test('WASD controls should not work in PAUSED state', () => {
            game.state = GameState.PAUSED;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 'w' });

            // Direction should not change when paused
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('WASD controls should not work in GAME_OVER state', () => {
            game.state = GameState.GAME_OVER;
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.handleKeyPress({ key: 'w' });

            // In GAME_OVER state, any key restarts - state changes, direction resets
            expect(game.state).toBe(GameState.PLAYING);
            expect(game.direction).toBe(Direction.RIGHT); // Reset to initial direction
        });
    });
});
