/**
 * Snake Game - Test Suite for Game Board Initialization
 *
 * Tests for Scenario: Game Board Initialization
 * - Test Case 1: Canvas element creation with defined dimensions
 * - Test Case 2: Game board boundary rendering
 * - Test Case 3: Game load time performance
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

describe('Snake Game - Game Board Initialization', () => {
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
     * Test Case 1: Canvas element is created with defined width and height
     * Input: Load game page in browser
     * Expected: Canvas element is created with defined width and height
     */
    describe('Test Case 1: Canvas Element Creation', () => {
        test('should create canvas element with defined width', () => {
            const result = game.init();

            expect(result).toBe(true);
            expect(game.getCanvas()).not.toBeNull();
            expect(game.getCanvas().width).toBe(CONFIG.BOARD_WIDTH);
        });

        test('should create canvas element with defined height', () => {
            const result = game.init();

            expect(result).toBe(true);
            expect(game.getCanvas().height).toBe(CONFIG.BOARD_HEIGHT);
        });

        test('should have valid canvas dimensions from config', () => {
            expect(CONFIG.BOARD_WIDTH).toBeGreaterThan(0);
            expect(CONFIG.BOARD_HEIGHT).toBeGreaterThan(0);
        });

        test('should get 2D rendering context successfully', () => {
            game.init();

            const canvas = game.getCanvas();
            expect(canvas.getContext).toHaveBeenCalledWith('2d');
        });

        test('should initialize game state to READY', () => {
            game.init();

            expect(game.getState()).toBe(GameState.READY);
        });

        test('should return false if canvas element is not found', () => {
            document.body.innerHTML = '<div></div>';
            const newGame = new SnakeGame();

            const result = newGame.init();

            expect(result).toBe(false);
        });

        test('should mark game as initialized after successful init', () => {
            expect(game.isInitialized()).toBe(false);

            game.init();

            expect(game.isInitialized()).toBe(true);
        });

        test('should have expected board width of 400', () => {
            expect(CONFIG.BOARD_WIDTH).toBe(400);
        });

        test('should have expected board height of 400', () => {
            expect(CONFIG.BOARD_HEIGHT).toBe(400);
        });
    });

    /**
     * Test Case 2: Game board has clearly visible rectangular boundaries
     * Input: Check game board boundaries
     * Expected: Game board has clearly visible rectangular boundaries
     */
    describe('Test Case 2: Game Board Boundaries', () => {
        test('should have border width configuration', () => {
            expect(CONFIG.BORDER_WIDTH).toBeGreaterThan(0);
        });

        test('should have border color configuration', () => {
            expect(CONFIG.BORDER_COLOR).toBeTruthy();
            expect(typeof CONFIG.BORDER_COLOR).toBe('string');
        });

        test('should have background color configuration', () => {
            expect(CONFIG.BACKGROUND_COLOR).toBeTruthy();
            expect(typeof CONFIG.BACKGROUND_COLOR).toBe('string');
        });

        test('should render boundary after initialization', () => {
            game.init();

            // Render is called in init()
            expect(mockCtx.strokeRect).toHaveBeenCalled();
        });

        test('should render rectangular boundary with correct dimensions', () => {
            game.init();

            // Verify strokeRect was called with boundary dimensions
            expect(mockCtx.strokeRect).toHaveBeenCalledWith(
                CONFIG.BORDER_WIDTH / 2,
                CONFIG.BORDER_WIDTH / 2,
                CONFIG.BOARD_WIDTH - CONFIG.BORDER_WIDTH,
                CONFIG.BOARD_HEIGHT - CONFIG.BORDER_WIDTH
            );
        });

        test('should set stroke style for boundary', () => {
            game.init();

            // After render, strokeStyle should be set
            expect(mockCtx.strokeStyle).toBe(CONFIG.BORDER_COLOR);
        });

        test('should clear canvas before rendering', () => {
            game.init();

            // First fillRect call should clear the canvas
            expect(mockCtx.fillRect).toHaveBeenCalledWith(0, 0, CONFIG.BOARD_WIDTH, CONFIG.BOARD_HEIGHT);
        });

        test('should have grid size configuration for game board', () => {
            expect(CONFIG.GRID_SIZE).toBeGreaterThan(0);
            expect(CONFIG.BOARD_WIDTH % CONFIG.GRID_SIZE).toBe(0);
            expect(CONFIG.BOARD_HEIGHT % CONFIG.GRID_SIZE).toBe(0);
        });

        test('should set background color when rendering', () => {
            game.init();

            // Background fill style should be set to background color
            expect(mockCtx.fillRect).toHaveBeenCalled();
        });

        test('should render boundary with correct line width', () => {
            game.init();

            expect(mockCtx.lineWidth).toBe(CONFIG.BORDER_WIDTH);
        });
    });

    /**
     * Test Case 3: Game loads and becomes interactive within 2 seconds
     * Input: Verify load time
     * Expected: Game loads and becomes interactive within 2 seconds
     */
    describe('Test Case 3: Game Load Time', () => {
        test('should initialize game within 2 seconds', () => {
            const startTime = Date.now();

            game.init();

            const endTime = Date.now();
            const loadTime = endTime - startTime;

            expect(loadTime).toBeLessThan(2000);
        });

        test('should be in READY state immediately after initialization', () => {
            game.init();

            expect(game.getState()).toBe(GameState.READY);
        });

        test('should display start prompt after initialization', () => {
            game.init();

            const statusElement = document.getElementById('game-status');
            expect(statusElement.textContent).toContain('Press any key to start');
        });

        test('should respond to keypress after initialization', () => {
            game.init();

            // Simulate keypress
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            document.dispatchEvent(event);

            expect(game.getState()).toBe(GameState.PLAYING);
        });

        test('should have score display ready after initialization', () => {
            game.init();

            const scoreElement = document.getElementById('score');
            expect(scoreElement).not.toBeNull();
            expect(scoreElement.textContent).toBe('0');
        });

        test('should have high score display ready after initialization', () => {
            game.init();

            const highScoreElement = document.getElementById('high-score');
            expect(highScoreElement).not.toBeNull();
        });

        test('should initialize within 100ms', () => {
            const startTime = performance.now();

            game.init();

            const loadTime = performance.now() - startTime;
            expect(loadTime).toBeLessThan(100);
        });

        test('should have all UI elements ready after initialization', () => {
            game.init();

            expect(document.getElementById('game-board')).not.toBeNull();
            expect(document.getElementById('score')).not.toBeNull();
            expect(document.getElementById('high-score')).not.toBeNull();
            expect(document.getElementById('game-status')).not.toBeNull();
        });
    });

    // Additional tests for game board initialization
    describe('Additional Game Board Tests', () => {
        test('should initialize snake at center of board', () => {
            game.init();

            const expectedX = Math.floor(CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE / 2);
            const expectedY = Math.floor(CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE / 2);

            // Access snake through game instance
            expect(game.snake[0].x).toBe(expectedX);
            expect(game.snake[0].y).toBe(expectedY);
        });

        test('should spawn food on initialization', () => {
            game.init();

            expect(game.food).not.toBeNull();
            expect(game.food.x).toBeDefined();
            expect(game.food.y).toBeDefined();
        });

        test('should not spawn food on snake position', () => {
            game.init();

            const isOnSnake = game.snake.some(
                segment => segment.x === game.food.x && segment.y === game.food.y
            );

            expect(isOnSnake).toBe(false);
        });

        test('should have correct initial score', () => {
            game.init();

            expect(game.score).toBe(0);
        });

        test('should have correct initial direction', () => {
            game.init();

            expect(game.direction).toEqual(Direction.RIGHT);
        });

        test('should initialize snake with 3 segments', () => {
            game.init();

            expect(game.snake.length).toBe(3);
        });

        test('should have food within grid boundaries', () => {
            game.init();

            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            expect(game.food.x).toBeGreaterThanOrEqual(0);
            expect(game.food.x).toBeLessThan(gridWidth);
            expect(game.food.y).toBeGreaterThanOrEqual(0);
            expect(game.food.y).toBeLessThan(gridHeight);
        });
    });
});

// E2E-style integration test for game load time
describe('E2E Test: Game Load Time Performance', () => {
    test('game should be fully interactive within 2000ms', async () => {
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

        const localStorageMock = {
            getItem: jest.fn(),
            setItem: jest.fn(),
            clear: jest.fn()
        };
        Object.defineProperty(window, 'localStorage', {
            value: localStorageMock,
            writable: true
        });

        const startTime = performance.now();

        // Initialize game
        const game = new SnakeGame();
        const initSuccess = game.init();

        // Verify initialization was successful
        expect(initSuccess).toBe(true);

        // Verify game is interactive (responds to input)
        const keyEvent = new KeyboardEvent('keydown', { key: 'ArrowRight' });
        document.dispatchEvent(keyEvent);

        const endTime = performance.now();
        const totalLoadTime = endTime - startTime;

        // Verify load time is under 2 seconds
        expect(totalLoadTime).toBeLessThan(2000);

        // Verify game state changed (is interactive)
        expect(game.getState()).toBe(GameState.PLAYING);

        // Log load time for debugging
        console.log(`E2E Load time: ${totalLoadTime.toFixed(2)}ms`);
    });

    test('should render game board immediately after load', () => {
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

        // Verify render was called
        expect(mockCtx.fillRect).toHaveBeenCalled();
        expect(mockCtx.strokeRect).toHaveBeenCalled();
    });
});
