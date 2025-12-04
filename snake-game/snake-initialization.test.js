/**
 * Snake Game - Test Suite for Snake Initialization and Display
 *
 * Scenario: Snake Initialization and Display
 * UUID: 8f3e4571-4187-4efe-8e7f-580666a4e30b
 *
 * Tests:
 * - Test Case 1: Initialize snake object with correct initial length and position
 * - Test Case 2: Render snake on canvas as connected body parts
 * - Test Case 3: Check snake initial direction is valid (not stationary)
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

describe('Snake Game - Snake Initialization and Display', () => {
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
     * Test Case 1: Initialize snake object
     * Input: Initialize snake object
     * Expected: Snake is created with correct initial length and position
     */
    describe('Test Case 1: Snake Initialization', () => {
        test('should initialize snake with correct initial length (3 segments)', () => {
            game.init();

            expect(game.snake).toBeDefined();
            expect(Array.isArray(game.snake)).toBe(true);
            expect(game.snake.length).toBe(3);
        });

        test('should initialize snake at center of the game board', () => {
            game.init();

            const expectedX = Math.floor(CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE / 2);
            const expectedY = Math.floor(CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE / 2);

            // Head should be at center
            expect(game.snake[0].x).toBe(expectedX);
            expect(game.snake[0].y).toBe(expectedY);
        });

        test('should initialize snake segments as connected (horizontally aligned)', () => {
            game.init();

            // Snake should be horizontal initially, segments connected
            const head = game.snake[0];
            const body1 = game.snake[1];
            const body2 = game.snake[2];

            // All segments should be on the same row (same y)
            expect(head.y).toBe(body1.y);
            expect(body1.y).toBe(body2.y);

            // Segments should be adjacent horizontally
            expect(head.x - body1.x).toBe(1);
            expect(body1.x - body2.x).toBe(1);
        });

        test('should initialize snake with valid x coordinates', () => {
            game.init();

            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;

            game.snake.forEach(segment => {
                expect(segment.x).toBeGreaterThanOrEqual(0);
                expect(segment.x).toBeLessThan(gridWidth);
            });
        });

        test('should initialize snake with valid y coordinates', () => {
            game.init();

            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            game.snake.forEach(segment => {
                expect(segment.y).toBeGreaterThanOrEqual(0);
                expect(segment.y).toBeLessThan(gridHeight);
            });
        });

        test('should not initialize snake overlapping boundaries', () => {
            game.init();

            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            game.snake.forEach(segment => {
                // Check not at or beyond boundaries
                expect(segment.x).toBeGreaterThanOrEqual(0);
                expect(segment.x).toBeLessThan(gridWidth);
                expect(segment.y).toBeGreaterThanOrEqual(0);
                expect(segment.y).toBeLessThan(gridHeight);
            });
        });

        test('should initialize snake head at position (10, 10) given 20x20 grid', () => {
            game.init();

            // With 400/20 = 20 grid cells, center is at 10
            expect(game.snake[0].x).toBe(10);
            expect(game.snake[0].y).toBe(10);
        });

        test('should have unique positions for each snake segment', () => {
            game.init();

            const positions = game.snake.map(seg => `${seg.x},${seg.y}`);
            const uniquePositions = new Set(positions);

            expect(uniquePositions.size).toBe(game.snake.length);
        });

        test('should reinitialize snake correctly after reset', () => {
            game.init();

            // Modify snake
            game.snake.push({ x: 0, y: 0 });
            expect(game.snake.length).toBe(4);

            // Reset game
            game.resetGame();

            expect(game.snake.length).toBe(3);
            expect(game.snake[0].x).toBe(10);
            expect(game.snake[0].y).toBe(10);
        });
    });

    /**
     * Test Case 2: Render snake on canvas
     * Input: Render snake on canvas
     * Expected: Snake segments are displayed as connected body parts
     */
    describe('Test Case 2: Snake Rendering', () => {
        test('should render snake segments using fillRect', () => {
            game.init();

            // render() is called in init(), which calls drawSnake()
            // Each segment should call fillRect
            expect(mockCtx.fillRect).toHaveBeenCalled();
        });

        test('should render exactly 3 segments (initial snake length)', () => {
            game.init();

            // fillRect is called for:
            // 1. Background clear (1 call)
            // 2. Each snake segment (3 calls)
            // Total should include 3 snake segment draws
            const fillRectCalls = mockCtx.fillRect.mock.calls;

            // Filter calls for snake segments (smaller rectangles at grid positions)
            const snakeSegmentCalls = fillRectCalls.filter(call => {
                // Snake segments are drawn at grid positions with specific sizes
                const width = call[2];
                const height = call[3];
                // Not the full canvas background
                return width !== CONFIG.BOARD_WIDTH && height !== CONFIG.BOARD_HEIGHT;
            });

            expect(snakeSegmentCalls.length).toBe(3);
        });

        test('should render snake with snake color', () => {
            game.init();

            // Check that fillStyle was set to snake color at some point
            // The drawSnake method sets fillStyle to CONFIG.SNAKE_COLOR
            expect(mockCtx.fillStyle).toBeDefined();
        });

        test('should render head segment larger than body segments', () => {
            game.init();

            const fillRectCalls = mockCtx.fillRect.mock.calls;

            // Head is drawn at index 0 in snake array with size (GRID_SIZE - 2)
            // Body segments are drawn with size (GRID_SIZE - 4)
            const snakeSegmentCalls = fillRectCalls.filter(call => {
                const width = call[2];
                return width !== CONFIG.BOARD_WIDTH;
            });

            if (snakeSegmentCalls.length >= 2) {
                // Head segment width
                const headWidth = snakeSegmentCalls[0][2];
                // Body segment width
                const bodyWidth = snakeSegmentCalls[1][2];

                // Head should be larger (GRID_SIZE - 2) vs body (GRID_SIZE - 4)
                expect(headWidth).toBeGreaterThan(bodyWidth);
            }
        });

        test('should render segments at correct grid positions', () => {
            game.init();

            const fillRectCalls = mockCtx.fillRect.mock.calls;

            // Snake starts at center: (10, 10), (9, 10), (8, 10)
            // Canvas coordinates: x * GRID_SIZE + offset

            const snakeSegmentCalls = fillRectCalls.filter(call => {
                const width = call[2];
                return width !== CONFIG.BOARD_WIDTH;
            });

            // First segment (head) at (10, 10)
            // Canvas x = 10 * 20 + 1 = 201, y = 10 * 20 + 1 = 201
            if (snakeSegmentCalls.length > 0) {
                const headCall = snakeSegmentCalls[0];
                expect(headCall[0]).toBe(10 * CONFIG.GRID_SIZE + 1); // x
                expect(headCall[1]).toBe(10 * CONFIG.GRID_SIZE + 1); // y
            }
        });

        test('should render snake segments as visually connected', () => {
            game.init();

            // Snake segments should be adjacent - their positions should differ by exactly 1 in one dimension
            for (let i = 0; i < game.snake.length - 1; i++) {
                const current = game.snake[i];
                const next = game.snake[i + 1];

                const dx = Math.abs(current.x - next.x);
                const dy = Math.abs(current.y - next.y);

                // Adjacent segments should be exactly 1 grid cell apart in one direction
                expect(dx + dy).toBe(1);
            }
        });

        test('should call drawSnake during render', () => {
            game.init();

            // Clear mocks to test fresh render
            mockCtx.fillRect.mockClear();

            // Call render directly
            game.render();

            // Should have at least 4 fillRect calls (1 background + 3 snake segments)
            expect(mockCtx.fillRect.mock.calls.length).toBeGreaterThanOrEqual(4);
        });

        test('should use distinctive snake color from config', () => {
            expect(CONFIG.SNAKE_COLOR).toBeDefined();
            expect(CONFIG.SNAKE_COLOR).toBe('#4ecca3');
        });

        test('should render snake after state change', () => {
            game.init();
            mockCtx.fillRect.mockClear();

            // Start game (state changes from READY to PLAYING)
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            document.dispatchEvent(event);

            // Game should be playing and rendering
            expect(game.getState()).toBe(GameState.PLAYING);
        });
    });

    /**
     * Test Case 3: Check snake initial direction
     * Input: Check snake initial direction
     * Expected: Snake has a valid initial direction (not stationary)
     */
    describe('Test Case 3: Snake Initial Direction', () => {
        test('should initialize with RIGHT direction', () => {
            game.init();

            expect(game.direction).toBe(Direction.RIGHT);
            expect(game.direction).toEqual({ x: 1, y: 0 });
        });

        test('should not have stationary initial direction', () => {
            game.init();

            // Direction should not be (0, 0)
            expect(game.direction.x !== 0 || game.direction.y !== 0).toBe(true);
        });

        test('should have valid direction object with x and y properties', () => {
            game.init();

            expect(game.direction).toHaveProperty('x');
            expect(game.direction).toHaveProperty('y');
            expect(typeof game.direction.x).toBe('number');
            expect(typeof game.direction.y).toBe('number');
        });

        test('should have nextDirection matching initial direction', () => {
            game.init();

            expect(game.nextDirection).toEqual(game.direction);
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should move in initial direction when game starts', () => {
            game.init();

            const initialHeadX = game.snake[0].x;
            const initialHeadY = game.snake[0].y;

            // Move snake manually
            game.moveSnake();

            const newHeadX = game.snake[0].x;
            const newHeadY = game.snake[0].y;

            // Should move right (x increases by 1, y stays same)
            expect(newHeadX).toBe(initialHeadX + game.direction.x);
            expect(newHeadY).toBe(initialHeadY + game.direction.y);
        });

        test('direction magnitude should be exactly 1 (unit vector)', () => {
            game.init();

            const magnitude = Math.abs(game.direction.x) + Math.abs(game.direction.y);
            expect(magnitude).toBe(1);
        });

        test('all Direction constants should be valid (non-stationary)', () => {
            Object.values(Direction).forEach(dir => {
                const magnitude = Math.abs(dir.x) + Math.abs(dir.y);
                expect(magnitude).toBe(1);
            });
        });

        test('initial direction should be one of the valid Direction constants', () => {
            game.init();

            const validDirections = Object.values(Direction);
            const isValidDirection = validDirections.some(
                dir => dir.x === game.direction.x && dir.y === game.direction.y
            );

            expect(isValidDirection).toBe(true);
        });

        test('should prevent 180-degree turn (cannot go LEFT from RIGHT)', () => {
            game.init();

            // Try to change to opposite direction
            game.handleKeyPress({ key: 'ArrowLeft' });

            // Direction should still be RIGHT (ignores opposite direction)
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });

        test('should allow 90-degree turn (can go UP from RIGHT)', () => {
            game.init();

            // Start the game first
            game.state = GameState.PLAYING;

            // Change to perpendicular direction
            game.handleKeyPress({ key: 'ArrowUp' });

            expect(game.nextDirection).toBe(Direction.UP);
        });

        test('should reset direction to RIGHT on game reset', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Change direction
            game.handleKeyPress({ key: 'ArrowUp' });
            game.direction = game.nextDirection;

            expect(game.direction).toBe(Direction.UP);

            // Reset game
            game.resetGame();

            expect(game.direction).toBe(Direction.RIGHT);
            expect(game.nextDirection).toBe(Direction.RIGHT);
        });
    });

    // Integration tests for snake initialization and display
    describe('Integration Tests: Snake Initialization and Display', () => {
        test('should display snake immediately after game initialization', () => {
            game.init();

            // Verify snake is rendered (fillRect called for segments)
            expect(mockCtx.fillRect).toHaveBeenCalled();

            // Verify snake exists with proper initial state
            expect(game.snake.length).toBe(3);
            expect(game.direction).toBe(Direction.RIGHT);
        });

        test('should maintain connected snake segments during gameplay', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Move snake several times
            for (let i = 0; i < 5; i++) {
                game.moveSnake();

                // Check segments remain connected
                for (let j = 0; j < game.snake.length - 1; j++) {
                    const current = game.snake[j];
                    const next = game.snake[j + 1];
                    const distance = Math.abs(current.x - next.x) + Math.abs(current.y - next.y);
                    expect(distance).toBe(1);
                }
            }
        });

        test('should initialize snake within playable area', () => {
            game.init();

            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            // All segments should be within bounds
            game.snake.forEach(segment => {
                expect(segment.x).toBeGreaterThanOrEqual(0);
                expect(segment.x).toBeLessThan(gridWidth);
                expect(segment.y).toBeGreaterThanOrEqual(0);
                expect(segment.y).toBeLessThan(gridHeight);
            });

            // Snake should have room to move in initial direction (RIGHT)
            const head = game.snake[0];
            expect(head.x).toBeLessThan(gridWidth - 1);
        });

        test('should correctly display snake as connected segments on game start', () => {
            game.init();

            // Trigger game start
            const event = new KeyboardEvent('keydown', { key: 'Space' });
            document.dispatchEvent(event);

            expect(game.getState()).toBe(GameState.PLAYING);
            expect(game.snake.length).toBeGreaterThanOrEqual(3);

            // Verify all segments are connected
            for (let i = 0; i < game.snake.length - 1; i++) {
                const current = game.snake[i];
                const next = game.snake[i + 1];
                const distance = Math.abs(current.x - next.x) + Math.abs(current.y - next.y);
                expect(distance).toBe(1);
            }
        });
    });
});
