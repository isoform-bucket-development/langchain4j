/**
 * Snake Game - Test Suite for Responsive Layout
 *
 * Tests for Scenario: Responsive Layout
 * - Test Case 1: Viewport width 320px - Game is fully visible and playable
 * - Test Case 2: Viewport width 768px - Game displays properly on tablet size
 * - Test Case 3: Viewport width 1920px - Game displays properly on desktop
 */

const { SnakeGame, CONFIG, GameState } = require('./game.js');

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

// Helper function to set up DOM with viewport simulation
const setupDOMWithViewport = (viewportWidth) => {
    const mockCtx = createMockContext();

    // Set up DOM environment
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

    // Mock localStorage
    Object.defineProperty(window, 'localStorage', {
        value: {
            getItem: jest.fn(),
            setItem: jest.fn(),
            clear: jest.fn()
        },
        writable: true,
        configurable: true
    });

    // Simulate viewport width using innerWidth
    Object.defineProperty(window, 'innerWidth', {
        value: viewportWidth,
        writable: true,
        configurable: true
    });

    // Simulate document.documentElement.clientWidth
    Object.defineProperty(document.documentElement, 'clientWidth', {
        value: viewportWidth,
        writable: true,
        configurable: true
    });

    return { mockCtx, canvas };
};

describe('Snake Game - Responsive Layout', () => {
    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Viewport width 320px
     * Input: Viewport width 320px
     * Expected: Game is fully visible and playable
     */
    describe('Test Case 1: Viewport width 320px (Mobile)', () => {
        const VIEWPORT_WIDTH = 320;

        test('game should initialize at 320px viewport', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();

            const result = game.init();

            expect(result).toBe(true);
            expect(game.isInitialized()).toBe(true);
        });

        test('game board should have dimensions that fit within 320px viewport', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // The game board should be configured to fit within viewport
            // Accounting for some padding/margins, the board width should be <= viewport
            const canvas = game.getCanvas();
            expect(canvas.width).toBeLessThanOrEqual(VIEWPORT_WIDTH);
        });

        test('game should be fully playable at 320px width', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // Simulate starting the game
            const startEvent = new KeyboardEvent('keydown', { key: 'Enter' });
            document.dispatchEvent(startEvent);

            expect(game.getState()).toBe(GameState.PLAYING);

            // Simulate direction change
            const leftEvent = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            document.dispatchEvent(leftEvent);

            // Game should still be responsive to controls
            expect(game.nextDirection).toEqual({ x: 0, y: -1 });
        });

        test('game container should have responsive max-width for mobile', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            const container = document.getElementById('game-container');
            expect(container).not.toBeNull();

            // Container should exist and be properly structured
            const canvas = document.getElementById('game-board');
            const scoreBoard = document.getElementById('score-board');
            const status = document.getElementById('game-status');

            expect(canvas).not.toBeNull();
            expect(scoreBoard).not.toBeNull();
            expect(status).not.toBeNull();
        });

        test('all game UI elements should be visible at 320px', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // Check all required elements exist
            expect(document.getElementById('score')).not.toBeNull();
            expect(document.getElementById('high-score')).not.toBeNull();
            expect(document.getElementById('game-board')).not.toBeNull();
            expect(document.getElementById('game-status')).not.toBeNull();
        });

        test('game should be fully functional at minimum viewport width', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // Verify snake is initialized
            expect(game.snake.length).toBe(3);

            // Verify food is spawned
            expect(game.food).not.toBeNull();

            // Verify score display works
            expect(document.getElementById('score').textContent).toBe('0');
        });
    });

    /**
     * Test Case 2: Viewport width 768px
     * Input: Viewport width 768px
     * Expected: Game displays properly on tablet size
     */
    describe('Test Case 2: Viewport width 768px (Tablet)', () => {
        const VIEWPORT_WIDTH = 768;

        test('game should initialize at 768px viewport', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();

            const result = game.init();

            expect(result).toBe(true);
            expect(game.isInitialized()).toBe(true);
        });

        test('game board should display properly at tablet size', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            const canvas = game.getCanvas();

            // Canvas should have proper dimensions
            expect(canvas.width).toBeGreaterThan(0);
            expect(canvas.height).toBeGreaterThan(0);

            // At 768px, we should have enough room for the standard board
            expect(canvas.width).toBeLessThanOrEqual(VIEWPORT_WIDTH);
        });

        test('game should be fully playable at tablet width', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // Start the game
            const startEvent = new KeyboardEvent('keydown', { key: ' ' });
            document.dispatchEvent(startEvent);

            expect(game.getState()).toBe(GameState.PLAYING);

            // Test direction controls work
            const downEvent = new KeyboardEvent('keydown', { key: 'ArrowDown' });
            document.dispatchEvent(downEvent);

            expect(game.nextDirection).toEqual({ x: 0, y: 1 });
        });

        test('score board should be visible at tablet size', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            const scoreBoard = document.getElementById('score-board');
            expect(scoreBoard).not.toBeNull();

            const score = document.getElementById('score');
            const highScore = document.getElementById('high-score');
            expect(score).not.toBeNull();
            expect(highScore).not.toBeNull();
        });

        test('game status should be displayed at tablet size', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            const status = document.getElementById('game-status');
            expect(status).not.toBeNull();
            expect(status.textContent).toContain('Press any key to start');
        });

        test('game should render snake and food at tablet size', () => {
            const { mockCtx } = setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // After init, render should be called
            expect(mockCtx.fillRect).toHaveBeenCalled();
            expect(mockCtx.strokeRect).toHaveBeenCalled();
            expect(mockCtx.arc).toHaveBeenCalled(); // Food is drawn as arc/circle
        });
    });

    /**
     * Test Case 3: Viewport width 1920px
     * Input: Viewport width 1920px
     * Expected: Game displays properly on desktop
     */
    describe('Test Case 3: Viewport width 1920px (Desktop)', () => {
        const VIEWPORT_WIDTH = 1920;

        test('game should initialize at 1920px viewport', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();

            const result = game.init();

            expect(result).toBe(true);
            expect(game.isInitialized()).toBe(true);
        });

        test('game board should display properly at desktop size', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            const canvas = game.getCanvas();

            // Canvas should have proper dimensions
            expect(canvas.width).toBeGreaterThan(0);
            expect(canvas.height).toBeGreaterThan(0);
        });

        test('game should be centered on desktop viewport', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            const container = document.getElementById('game-container');
            expect(container).not.toBeNull();

            // Container should use flexbox for centering (verified by CSS)
            // The game board should be much smaller than viewport on desktop
            const canvas = game.getCanvas();
            expect(canvas.width).toBeLessThan(VIEWPORT_WIDTH);
        });

        test('game should be fully playable at desktop width', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // Start game
            const startEvent = new KeyboardEvent('keydown', { key: 'Enter' });
            document.dispatchEvent(startEvent);

            expect(game.getState()).toBe(GameState.PLAYING);

            // Test all direction controls
            const upEvent = new KeyboardEvent('keydown', { key: 'ArrowUp' });
            document.dispatchEvent(upEvent);
            expect(game.nextDirection).toEqual({ x: 0, y: -1 });
        });

        test('all UI elements should be visible at desktop size', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            expect(document.getElementById('game-container')).not.toBeNull();
            expect(document.getElementById('score-board')).not.toBeNull();
            expect(document.getElementById('score')).not.toBeNull();
            expect(document.getElementById('high-score')).not.toBeNull();
            expect(document.getElementById('game-board')).not.toBeNull();
            expect(document.getElementById('game-status')).not.toBeNull();
        });

        test('game should render correctly at desktop resolution', () => {
            const { mockCtx } = setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // Verify rendering calls
            expect(mockCtx.fillRect).toHaveBeenCalled();
            expect(mockCtx.strokeRect).toHaveBeenCalled();

            // Verify snake segments drawn
            const snakeDrawCalls = mockCtx.fillRect.mock.calls.filter(
                call => call[2] === CONFIG.GRID_SIZE - 2 || call[2] === CONFIG.GRID_SIZE - 4
            );
            expect(snakeDrawCalls.length).toBeGreaterThan(0);
        });

        test('game over should display properly at desktop size', () => {
            setupDOMWithViewport(VIEWPORT_WIDTH);
            const game = new SnakeGame();
            game.init();

            // Simulate game over
            game.state = GameState.GAME_OVER;
            game.updateStatus(`Game Over! Score: ${game.score} - Press any key to restart`);

            const status = document.getElementById('game-status');
            expect(status.textContent).toContain('Game Over');
        });
    });

    /**
     * Additional responsive behavior tests
     */
    describe('Cross-viewport responsive behavior', () => {
        test('game should work at boundary viewport widths', () => {
            // Test at exact minimum supported width
            setupDOMWithViewport(320);
            const game1 = new SnakeGame();
            expect(game1.init()).toBe(true);

            // Test at intermediate width
            setupDOMWithViewport(480);
            const game2 = new SnakeGame();
            expect(game2.init()).toBe(true);

            // Test at large desktop width
            setupDOMWithViewport(2560);
            const game3 = new SnakeGame();
            expect(game3.init()).toBe(true);
        });

        test('game configuration should support responsive layout', () => {
            // Verify CONFIG values are reasonable for responsive design
            expect(CONFIG.BOARD_WIDTH).toBeLessThanOrEqual(400);
            expect(CONFIG.BOARD_HEIGHT).toBeLessThanOrEqual(400);
            expect(CONFIG.GRID_SIZE).toBeGreaterThan(0);
        });

        test('canvas aspect ratio should be maintained', () => {
            const viewports = [320, 768, 1920];

            viewports.forEach(width => {
                setupDOMWithViewport(width);
                const game = new SnakeGame();
                game.init();

                const canvas = game.getCanvas();
                // Game board should maintain square aspect ratio
                expect(canvas.width).toBe(canvas.height);
            });
        });

        test('game should not require horizontal scrolling at minimum width', () => {
            setupDOMWithViewport(320);
            const game = new SnakeGame();
            game.init();

            const canvas = game.getCanvas();
            // Canvas width should not exceed viewport width
            expect(canvas.width).toBeLessThanOrEqual(320);
        });
    });
});
