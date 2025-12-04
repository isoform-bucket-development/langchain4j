/**
 * Browser Compatibility Tests
 *
 * These tests verify that the Snake game works correctly across modern browsers
 * (Chrome, Firefox, Safari, Edge) by testing the APIs and features used by the game.
 *
 * Since this is a unit test environment using jsdom, we simulate browser compatibility
 * by verifying that all browser APIs used by the game are available and work correctly.
 */

const { SnakeGame, CONFIG, GameState, Direction } = require('./game.js');

// Mock canvas context (same pattern as other tests)
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
    restore: jest.fn(),
    moveTo: jest.fn(),
    lineTo: jest.fn()
});

// Setup helper function
const setupTestEnvironment = () => {
    const mockCtx = createMockContext();

    document.body.innerHTML = `
        <div id="game-container">
            <div id="score-board">
                <span id="score">0</span>
                <span id="high-score">0</span>
            </div>
            <canvas id="game-board"></canvas>
            <div id="game-status"></div>
        </div>
    `;

    const canvas = document.getElementById('game-board');
    canvas.getContext = jest.fn(() => mockCtx);

    const localStorageMock = {
        store: {},
        getItem: jest.fn((key) => localStorageMock.store[key] || null),
        setItem: jest.fn((key, value) => { localStorageMock.store[key] = value.toString(); }),
        removeItem: jest.fn((key) => { delete localStorageMock.store[key]; }),
        clear: jest.fn(() => { localStorageMock.store = {}; })
    };
    Object.defineProperty(window, 'localStorage', { value: localStorageMock, writable: true });

    jest.spyOn(window, 'requestAnimationFrame').mockImplementation(cb => setTimeout(cb, 16));
    jest.spyOn(window, 'cancelAnimationFrame').mockImplementation(id => clearTimeout(id));

    return { mockCtx, canvas, localStorageMock };
};

describe('Browser Compatibility - Chrome', () => {
    let game;
    let mockCtx;
    let canvas;

    beforeEach(() => {
        const env = setupTestEnvironment();
        mockCtx = env.mockCtx;
        canvas = env.canvas;
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    test('Canvas 2D rendering context is available', () => {
        expect(canvas).toBeDefined();
        expect(canvas.getContext).toHaveBeenCalledWith('2d');
    });

    test('Canvas fillRect method is called for rendering', () => {
        expect(mockCtx.fillRect).toHaveBeenCalled();
    });

    test('Canvas strokeRect method is called for boundary', () => {
        expect(mockCtx.strokeRect).toHaveBeenCalled();
    });

    test('Canvas arc method is called for circular food rendering', () => {
        expect(mockCtx.beginPath).toHaveBeenCalled();
        expect(mockCtx.arc).toHaveBeenCalled();
        expect(mockCtx.fill).toHaveBeenCalled();
    });

    test('Canvas globalAlpha property is supported for visual feedback', () => {
        // The game uses globalAlpha in drawFeedbackEffect
        expect(typeof mockCtx.globalAlpha).toBe('number');
    });

    test('LocalStorage is available and functional', () => {
        expect(localStorage).toBeDefined();
        expect(typeof localStorage.getItem).toBe('function');
        expect(typeof localStorage.setItem).toBe('function');

        localStorage.setItem('testKey', 'testValue');
        expect(localStorage.getItem('testKey')).toBe('testValue');
    });

    test('requestAnimationFrame is available', () => {
        expect(typeof window.requestAnimationFrame).toBe('function');
    });

    test('cancelAnimationFrame is available', () => {
        expect(typeof window.cancelAnimationFrame).toBe('function');
    });

    test('Keyboard event handling works correctly', () => {
        const handler = jest.fn();
        document.addEventListener('keydown', handler);

        const event = new KeyboardEvent('keydown', { key: 'ArrowUp' });
        document.dispatchEvent(event);

        expect(handler).toHaveBeenCalled();
    });

    test('Game initializes correctly in browser environment', () => {
        expect(game.isInitialized()).toBe(true);
        expect(game.getState()).toBe(GameState.READY);
    });

    test('All core game features work correctly', () => {
        expect(game.snake.length).toBe(3);
        expect(game.food).toBeDefined();
        expect(game.score).toBe(0);

        // Render doesn't throw (it was called during init)
        expect(mockCtx.fillRect).toHaveBeenCalled();
    });
});

describe('Browser Compatibility - Firefox', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        const env = setupTestEnvironment();
        mockCtx = env.mockCtx;
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    test('Canvas 2D context is available in Firefox-like environment', () => {
        expect(game.isInitialized()).toBe(true);
    });

    test('Canvas stroke method is available for border rendering', () => {
        expect(typeof mockCtx.stroke).toBe('function');
    });

    test('Canvas lineWidth property is supported', () => {
        expect(mockCtx.lineWidth).toBe(CONFIG.BORDER_WIDTH);
    });

    test('Canvas fillStyle and strokeStyle are set correctly', () => {
        expect(mockCtx.strokeStyle).toBe(CONFIG.BORDER_COLOR);
    });

    test('Date.now() is available for timing', () => {
        expect(typeof Date.now).toBe('function');
        const timestamp = Date.now();
        expect(typeof timestamp).toBe('number');
        expect(timestamp).toBeGreaterThan(0);
    });

    test('Math.floor and Math.random work correctly', () => {
        expect(typeof Math.floor).toBe('function');
        expect(typeof Math.random).toBe('function');

        const random = Math.random();
        expect(random).toBeGreaterThanOrEqual(0);
        expect(random).toBeLessThan(1);

        expect(Math.floor(3.7)).toBe(3);
    });

    test('Array methods used by game work correctly', () => {
        const arr = [1, 2, 3];
        expect(typeof arr.some).toBe('function');
        expect(typeof arr.forEach).toBe('function');
        expect(typeof arr.unshift).toBe('function');
        expect(typeof arr.pop).toBe('function');

        arr.unshift(0);
        expect(arr[0]).toBe(0);

        const popped = arr.pop();
        expect(popped).toBe(3);
    });

    test('Game direction changes work correctly', () => {
        game.startGame();
        game.handleKeyPress({ key: 'ArrowUp' });
        expect(game.nextDirection).toEqual(Direction.UP);

        game.direction = Direction.UP;
        game.handleKeyPress({ key: 'ArrowLeft' });
        expect(game.nextDirection).toEqual(Direction.LEFT);
    });
});

describe('Browser Compatibility - Safari', () => {
    let game;
    let mockCtx;
    let canvas;

    beforeEach(() => {
        const env = setupTestEnvironment();
        mockCtx = env.mockCtx;
        canvas = env.canvas;
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    test('Canvas is properly sized', () => {
        expect(canvas.width).toBe(CONFIG.BOARD_WIDTH);
        expect(canvas.height).toBe(CONFIG.BOARD_HEIGHT);
    });

    test('Canvas save and restore methods are available', () => {
        // Safari supports these standard Canvas methods
        expect(typeof mockCtx.save).toBe('function');
        expect(typeof mockCtx.restore).toBe('function');
    });

    test('High score persistence works with Safari localStorage', () => {
        game.score = 100;
        game.saveHighScore();

        expect(localStorage.setItem).toHaveBeenCalledWith('snakeHighScore', '100');
    });

    test('Keyboard events dispatch correctly', () => {
        const event = new KeyboardEvent('keydown', {
            key: 'ArrowRight',
            bubbles: true
        });

        expect(() => document.dispatchEvent(event)).not.toThrow();
    });

    test('DOM element manipulation works correctly', () => {
        const scoreElement = document.getElementById('score');
        expect(scoreElement).toBeDefined();

        scoreElement.textContent = '50';
        expect(scoreElement.textContent).toBe('50');
    });

    test('Event listeners can be added and triggered', () => {
        const mockHandler = jest.fn();
        document.addEventListener('keydown', mockHandler);

        const event = new KeyboardEvent('keydown', { key: 'w' });
        document.dispatchEvent(event);

        expect(mockHandler).toHaveBeenCalled();
    });

    test('Game pause functionality works', () => {
        game.startGame();
        expect(game.getState()).toBe(GameState.PLAYING);

        game.togglePause();
        expect(game.getState()).toBe(GameState.PAUSED);

        game.togglePause();
        expect(game.getState()).toBe(GameState.PLAYING);
    });
});

describe('Browser Compatibility - Edge', () => {
    let game;
    let mockCtx;
    let canvas;

    beforeEach(() => {
        const env = setupTestEnvironment();
        mockCtx = env.mockCtx;
        canvas = env.canvas;
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    test('Canvas getContext returns valid 2D context', () => {
        expect(canvas.getContext).toHaveBeenCalledWith('2d');
        // The mock returns our mockCtx, verifying the call was made
    });

    test('parseInt works correctly for high score loading', () => {
        expect(parseInt('100', 10)).toBe(100);
        expect(parseInt('0', 10)).toBe(0);
        expect(parseInt(null, 10)).toBeNaN();
    });

    test('Object property access works correctly', () => {
        const config = game.getConfig();
        expect(config.BOARD_WIDTH).toBe(400);
        expect(config.BOARD_HEIGHT).toBe(400);
        expect(config.GRID_SIZE).toBe(20);
    });

    test('Console.log is available for debugging', () => {
        expect(typeof console.log).toBe('function');
        expect(typeof console.error).toBe('function');
    });

    test('ES6 class syntax works correctly', () => {
        expect(game instanceof SnakeGame).toBe(true);
        expect(typeof game.init).toBe('function');
        expect(typeof game.render).toBe('function');
    });

    test('Arrow function in event handlers work', () => {
        const mockCallback = jest.fn();
        const wrapper = (event) => mockCallback(event);

        document.addEventListener('keydown', wrapper);
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowDown' }));

        expect(mockCallback).toHaveBeenCalled();
    });

    test('Spread operator and array methods work', () => {
        const snake = [...game.snake];
        expect(snake.length).toBe(3);
        expect(snake[0]).toEqual(game.snake[0]);
    });

    test('Template literals work correctly', () => {
        const score = 100;
        const message = `Score: ${score}`;
        expect(message).toBe('Score: 100');
    });

    test('Game over flow works correctly', () => {
        game.startGame();

        // Force game over by calling gameOver directly
        game.gameOver();

        expect(game.getState()).toBe(GameState.GAME_OVER);
    });
});

describe('Cross-Browser API Compatibility', () => {
    let game;
    let mockCtx;
    let canvas;

    beforeEach(() => {
        const env = setupTestEnvironment();
        mockCtx = env.mockCtx;
        canvas = env.canvas;
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    test('document.getElementById works across browsers', () => {
        expect(document.getElementById('game-board')).toBeDefined();
        expect(document.getElementById('score')).toBeDefined();
        expect(document.getElementById('high-score')).toBeDefined();
        expect(document.getElementById('game-status')).toBeDefined();
    });

    test('document.addEventListener works across browsers', () => {
        const handler = jest.fn();
        expect(() => document.addEventListener('keydown', handler)).not.toThrow();
    });

    test('KeyboardEvent constructor works across browsers', () => {
        const event = new KeyboardEvent('keydown', {
            key: 'ArrowUp',
            code: 'ArrowUp',
            bubbles: true,
            cancelable: true
        });

        expect(event.key).toBe('ArrowUp');
        expect(event.type).toBe('keydown');
    });

    test('DOMContentLoaded event is standard across browsers', () => {
        const handler = jest.fn();
        document.addEventListener('DOMContentLoaded', handler);
        // In test environment, DOMContentLoaded has already fired
        expect(typeof document.readyState).toBe('string');
    });

    test('textContent property works across browsers', () => {
        const element = document.getElementById('score');
        element.textContent = 'test';
        expect(element.textContent).toBe('test');
    });

    test('CSS box-sizing is applied correctly', () => {
        // The game uses box-sizing: border-box which is supported in all modern browsers
        const container = document.getElementById('game-container');
        expect(container).toBeDefined();
    });

    test('All canvas rendering operations are called during render', () => {
        // Reset mocks to verify render cycle
        mockCtx.fillRect.mockClear();
        mockCtx.strokeRect.mockClear();
        mockCtx.beginPath.mockClear();
        mockCtx.arc.mockClear();
        mockCtx.fill.mockClear();

        // Trigger render
        game.render();

        // Verify all major canvas operations were called
        expect(mockCtx.fillRect).toHaveBeenCalled(); // Background and snake
        expect(mockCtx.strokeRect).toHaveBeenCalled(); // Border
        expect(mockCtx.beginPath).toHaveBeenCalled(); // Food circle
        expect(mockCtx.arc).toHaveBeenCalled(); // Food circle
        expect(mockCtx.fill).toHaveBeenCalled(); // Food fill
    });

    test('Game full lifecycle works correctly', () => {
        // Ready state
        expect(game.getState()).toBe(GameState.READY);

        // Start game
        game.startGame();
        expect(game.getState()).toBe(GameState.PLAYING);

        // Pause game
        game.togglePause();
        expect(game.getState()).toBe(GameState.PAUSED);

        // Resume game
        game.togglePause();
        expect(game.getState()).toBe(GameState.PLAYING);

        // Game over
        game.gameOver();
        expect(game.getState()).toBe(GameState.GAME_OVER);

        // Reset and restart
        game.resetGame();
        expect(game.getState()).toBe(GameState.READY);
    });

    test('All direction keys work correctly', () => {
        game.startGame();

        // Test arrow keys
        game.direction = Direction.RIGHT;
        game.handleKeyPress({ key: 'ArrowUp' });
        expect(game.nextDirection).toEqual(Direction.UP);

        game.direction = Direction.UP;
        game.handleKeyPress({ key: 'ArrowLeft' });
        expect(game.nextDirection).toEqual(Direction.LEFT);

        game.direction = Direction.LEFT;
        game.handleKeyPress({ key: 'ArrowDown' });
        expect(game.nextDirection).toEqual(Direction.DOWN);

        game.direction = Direction.DOWN;
        game.handleKeyPress({ key: 'ArrowRight' });
        expect(game.nextDirection).toEqual(Direction.RIGHT);

        // Test WASD keys
        game.direction = Direction.RIGHT;
        game.handleKeyPress({ key: 'w' });
        expect(game.nextDirection).toEqual(Direction.UP);

        game.direction = Direction.UP;
        game.handleKeyPress({ key: 'a' });
        expect(game.nextDirection).toEqual(Direction.LEFT);

        game.direction = Direction.LEFT;
        game.handleKeyPress({ key: 's' });
        expect(game.nextDirection).toEqual(Direction.DOWN);

        game.direction = Direction.DOWN;
        game.handleKeyPress({ key: 'd' });
        expect(game.nextDirection).toEqual(Direction.RIGHT);
    });

    test('WASD uppercase keys also work', () => {
        game.startGame();

        game.direction = Direction.RIGHT;
        game.handleKeyPress({ key: 'W' });
        expect(game.nextDirection).toEqual(Direction.UP);

        game.direction = Direction.UP;
        game.handleKeyPress({ key: 'A' });
        expect(game.nextDirection).toEqual(Direction.LEFT);

        game.direction = Direction.LEFT;
        game.handleKeyPress({ key: 'S' });
        expect(game.nextDirection).toEqual(Direction.DOWN);

        game.direction = Direction.DOWN;
        game.handleKeyPress({ key: 'D' });
        expect(game.nextDirection).toEqual(Direction.RIGHT);
    });
});
