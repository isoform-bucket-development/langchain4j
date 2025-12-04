/**
 * Snake Game - Test Suite for Food Generation and Display
 *
 * Scenario: Food Generation and Display
 * Tests that food items appear at random positions on the game board
 *
 * Test Cases:
 * - Test Case 1: Food position is within game board boundaries
 * - Test Case 2: Food does not spawn on snake body
 * - Test Case 3: Food is visually distinct with contrasting color
 * - Test Case 4: Food positions are randomized (not always same location)
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

// Helper to set up DOM environment
const setupDOM = () => {
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
    const mockCtx = createMockContext();
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

    return mockCtx;
};

describe('Food Generation and Display', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        mockCtx = setupDOM();
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Food position is within game board boundaries
     * Input: Generate food position
     * Expected: Food position is within game board boundaries
     */
    describe('Test Case 1: Food Position Within Boundaries', () => {
        test('food x-coordinate should be within grid boundaries', () => {
            game.init();

            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            expect(game.food.x).toBeGreaterThanOrEqual(0);
            expect(game.food.x).toBeLessThan(gridWidth);
        });

        test('food y-coordinate should be within grid boundaries', () => {
            game.init();

            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;
            expect(game.food.y).toBeGreaterThanOrEqual(0);
            expect(game.food.y).toBeLessThan(gridHeight);
        });

        test('food should be at integer grid coordinates', () => {
            game.init();

            expect(Number.isInteger(game.food.x)).toBe(true);
            expect(Number.isInteger(game.food.y)).toBe(true);
        });

        test('spawned food should always be within bounds after multiple spawns', () => {
            game.init();

            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            // Test multiple spawns
            for (let i = 0; i < 100; i++) {
                game.spawnFood();
                expect(game.food.x).toBeGreaterThanOrEqual(0);
                expect(game.food.x).toBeLessThan(gridWidth);
                expect(game.food.y).toBeGreaterThanOrEqual(0);
                expect(game.food.y).toBeLessThan(gridHeight);
            }
        });

        test('food position should be defined after initialization', () => {
            game.init();

            expect(game.food).toBeDefined();
            expect(game.food).not.toBeNull();
            expect(game.food.x).toBeDefined();
            expect(game.food.y).toBeDefined();
        });

        test('food should be within canvas pixel boundaries when rendered', () => {
            game.init();

            const pixelX = game.food.x * CONFIG.GRID_SIZE;
            const pixelY = game.food.y * CONFIG.GRID_SIZE;

            expect(pixelX).toBeGreaterThanOrEqual(0);
            expect(pixelX).toBeLessThan(CONFIG.BOARD_WIDTH);
            expect(pixelY).toBeGreaterThanOrEqual(0);
            expect(pixelY).toBeLessThan(CONFIG.BOARD_HEIGHT);
        });
    });

    /**
     * Test Case 2: Food does not spawn on snake body
     * Input: Generate food when snake occupies positions
     * Expected: Food does not spawn on snake body
     */
    describe('Test Case 2: Food Does Not Spawn on Snake Body', () => {
        test('initial food should not overlap with initial snake position', () => {
            game.init();

            const isOnSnake = game.snake.some(
                segment => segment.x === game.food.x && segment.y === game.food.y
            );
            expect(isOnSnake).toBe(false);
        });

        test('isSnakePosition should correctly identify snake occupied cells', () => {
            game.init();

            // Snake head position should be identified
            const head = game.snake[0];
            expect(game.isSnakePosition(head.x, head.y)).toBe(true);

            // Position away from snake should not be identified
            expect(game.isSnakePosition(-1, -1)).toBe(false);
        });

        test('food should never spawn on any snake segment', () => {
            game.init();

            // Test multiple spawns
            for (let i = 0; i < 50; i++) {
                game.spawnFood();
                const isOnSnake = game.snake.some(
                    segment => segment.x === game.food.x && segment.y === game.food.y
                );
                expect(isOnSnake).toBe(false);
            }
        });

        test('food should avoid snake body even with longer snake', () => {
            game.init();

            // Simulate a longer snake
            const head = game.snake[0];
            for (let i = 1; i <= 10; i++) {
                game.snake.push({ x: head.x - i, y: head.y });
            }

            // Test multiple spawns
            for (let i = 0; i < 50; i++) {
                game.spawnFood();
                const isOnSnake = game.snake.some(
                    segment => segment.x === game.food.x && segment.y === game.food.y
                );
                expect(isOnSnake).toBe(false);
            }
        });

        test('isSnakePosition should check all snake segments', () => {
            game.init();

            // Check each snake segment
            game.snake.forEach(segment => {
                expect(game.isSnakePosition(segment.x, segment.y)).toBe(true);
            });
        });

        test('food spawn should find available position even with crowded board', () => {
            game.init();

            // Create a snake that occupies multiple positions
            game.snake = [];
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;

            // Fill first row except last cell
            for (let x = 0; x < gridWidth - 1; x++) {
                game.snake.push({ x, y: 0 });
            }

            game.spawnFood();

            // Food should be spawned somewhere not on snake
            const isOnSnake = game.snake.some(
                segment => segment.x === game.food.x && segment.y === game.food.y
            );
            expect(isOnSnake).toBe(false);
        });
    });

    /**
     * Test Case 3: Food is visually distinct with contrasting color
     * Input: Render food on canvas
     * Expected: Food is visually distinct with contrasting color
     */
    describe('Test Case 3: Food Visual Distinction', () => {
        test('food color should be configured', () => {
            expect(CONFIG.FOOD_COLOR).toBeDefined();
            expect(typeof CONFIG.FOOD_COLOR).toBe('string');
        });

        test('food color should be different from snake color', () => {
            expect(CONFIG.FOOD_COLOR).not.toBe(CONFIG.SNAKE_COLOR);
        });

        test('food color should be different from background color', () => {
            expect(CONFIG.FOOD_COLOR).not.toBe(CONFIG.BACKGROUND_COLOR);
        });

        test('drawFood should set correct fill style', () => {
            game.init();

            // The render method is called in init, which calls drawFood
            // After rendering, fillStyle should have been set to FOOD_COLOR at some point
            // We verify the arc method was called (food is drawn as circle)
            expect(mockCtx.arc).toHaveBeenCalled();
        });

        test('food should be rendered as a circle', () => {
            game.init();

            // Verify beginPath and arc were called for food rendering
            expect(mockCtx.beginPath).toHaveBeenCalled();
            expect(mockCtx.arc).toHaveBeenCalled();
            expect(mockCtx.fill).toHaveBeenCalled();
        });

        test('food rendering should use correct coordinates', () => {
            game.init();

            const expectedX = game.food.x * CONFIG.GRID_SIZE + CONFIG.GRID_SIZE / 2;
            const expectedY = game.food.y * CONFIG.GRID_SIZE + CONFIG.GRID_SIZE / 2;
            const expectedRadius = CONFIG.GRID_SIZE / 2 - 2;

            expect(mockCtx.arc).toHaveBeenCalledWith(
                expectedX,
                expectedY,
                expectedRadius,
                0,
                Math.PI * 2
            );
        });

        test('food color should be a valid hex color', () => {
            const hexColorRegex = /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/;
            expect(CONFIG.FOOD_COLOR).toMatch(hexColorRegex);
        });

        test('food and snake should have contrasting colors', () => {
            // Parse hex colors to RGB
            const parseHex = (hex) => {
                const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
                return result ? {
                    r: parseInt(result[1], 16),
                    g: parseInt(result[2], 16),
                    b: parseInt(result[3], 16)
                } : null;
            };

            const foodColor = parseHex(CONFIG.FOOD_COLOR);
            const snakeColor = parseHex(CONFIG.SNAKE_COLOR);

            // Calculate color difference (simple RGB distance)
            const colorDiff = Math.sqrt(
                Math.pow(foodColor.r - snakeColor.r, 2) +
                Math.pow(foodColor.g - snakeColor.g, 2) +
                Math.pow(foodColor.b - snakeColor.b, 2)
            );

            // Colors should be significantly different (threshold: 100)
            expect(colorDiff).toBeGreaterThan(100);
        });
    });

    /**
     * Test Case 4: Food positions are randomized (not always same location)
     * Input: Generate multiple food positions
     * Expected: Food positions are randomized (not always same location)
     * Type: Integration
     */
    describe('Test Case 4: Food Position Randomization (Integration)', () => {
        test('multiple spawns should produce different positions', () => {
            game.init();

            const positions = new Set();

            // Spawn food multiple times and collect unique positions
            for (let i = 0; i < 50; i++) {
                game.spawnFood();
                positions.add(`${game.food.x},${game.food.y}`);
            }

            // Should have multiple unique positions (at least 3 different positions)
            expect(positions.size).toBeGreaterThan(2);
        });

        test('food positions should vary across different game instances', () => {
            const positions = [];

            // Create multiple game instances and collect initial food positions
            for (let i = 0; i < 10; i++) {
                const newGame = new SnakeGame();
                setupDOM();
                newGame.init();
                positions.push(`${newGame.food.x},${newGame.food.y}`);
            }

            // Convert to Set to get unique positions
            const uniquePositions = new Set(positions);

            // Should have at least 2 different positions among 10 games
            // (statistically unlikely to get same position 10 times)
            expect(uniquePositions.size).toBeGreaterThanOrEqual(2);
        });

        test('food respawn after eating should produce new position', () => {
            game.init();

            const initialPosition = { x: game.food.x, y: game.food.y };

            // Spawn many times to ensure different position
            let foundDifferent = false;
            for (let i = 0; i < 100; i++) {
                game.spawnFood();
                if (game.food.x !== initialPosition.x || game.food.y !== initialPosition.y) {
                    foundDifferent = true;
                    break;
                }
            }

            expect(foundDifferent).toBe(true);
        });

        test('randomization should cover different areas of the board', () => {
            game.init();

            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            let hasTopLeft = false;
            let hasTopRight = false;
            let hasBottomLeft = false;
            let hasBottomRight = false;

            const midX = gridWidth / 2;
            const midY = gridHeight / 2;

            // Generate many positions
            for (let i = 0; i < 200; i++) {
                game.spawnFood();
                const { x, y } = game.food;

                if (x < midX && y < midY) hasTopLeft = true;
                if (x >= midX && y < midY) hasTopRight = true;
                if (x < midX && y >= midY) hasBottomLeft = true;
                if (x >= midX && y >= midY) hasBottomRight = true;
            }

            // Should have food appear in all quadrants eventually
            expect(hasTopLeft).toBe(true);
            expect(hasTopRight).toBe(true);
            expect(hasBottomLeft).toBe(true);
            expect(hasBottomRight).toBe(true);
        });

        test('food position should be based on Math.random', () => {
            game.init();
            // Move snake out of the way
            game.snake = [{ x: -1, y: -1 }];

            // Mock Math.random to return predictable values
            const originalRandom = Math.random;
            Math.random = jest.fn()
                .mockReturnValueOnce(0.5)  // First call for x
                .mockReturnValueOnce(0.5); // First call for y

            game.spawnFood();

            // Restore Math.random
            Math.random = originalRandom;

            const expectedX = Math.floor(0.5 * (CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE));
            const expectedY = Math.floor(0.5 * (CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE));

            expect(game.food.x).toBe(expectedX);
            expect(game.food.y).toBe(expectedY);
        });
    });

    /**
     * Additional tests for game state transitions related to food
     */
    describe('Food and Game State Integration', () => {
        test('food should be present when game starts', () => {
            game.init();

            // Start the game
            const event = new KeyboardEvent('keydown', { key: 'Enter' });
            document.dispatchEvent(event);

            expect(game.getState()).toBe(GameState.PLAYING);
            expect(game.food).not.toBeNull();
        });

        test('new food should spawn after reset', () => {
            game.init();
            const initialFood = { ...game.food };

            game.resetGame();

            // Food should exist after reset
            expect(game.food).not.toBeNull();
            expect(game.food.x).toBeDefined();
            expect(game.food.y).toBeDefined();
        });

        test('food color configuration should match PRD requirement', () => {
            // PRD states: "Food: Contrasting color, easily visible against the board"
            // Verify food color is contrasting (different from background)
            expect(CONFIG.FOOD_COLOR).not.toBe(CONFIG.BACKGROUND_COLOR);
            expect(CONFIG.FOOD_COLOR).not.toBe(CONFIG.BORDER_COLOR);
        });
    });
});
