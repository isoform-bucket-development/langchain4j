/**
 * Snake Game - Test Suite for Single Food Item Constraint
 *
 * Scenario: Single Food Item Constraint
 * Verifies that only one food item is visible at a time throughout gameplay
 *
 * Test Cases:
 * - Test Case 1: Initial game state has exactly one food item
 * - Test Case 2: After food consumption, exactly one new food item exists
 * - Test Case 3: Throughout extended gameplay, never more than one food item visible
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

/**
 * Helper function to count food items in game state
 * @param {SnakeGame} game - The game instance
 * @returns {number} Number of food items (should always be 1)
 */
const countFoodItems = (game) => {
    // The game stores food as a single object property
    // This function validates that food is a single item, not an array or multiple items
    if (game.food === null || game.food === undefined) {
        return 0;
    }

    // Check if food is a single object with x and y coordinates
    if (typeof game.food === 'object' &&
        typeof game.food.x === 'number' &&
        typeof game.food.y === 'number' &&
        !Array.isArray(game.food)) {
        return 1;
    }

    // If food is an array (which it shouldn't be), return its length
    if (Array.isArray(game.food)) {
        return game.food.length;
    }

    return 0;
};

/**
 * Helper to simulate eating food by moving snake head to food position
 * @param {SnakeGame} game - The game instance
 */
const simulateEatFood = (game) => {
    // Position snake head at food location
    game.snake[0].x = game.food.x;
    game.snake[0].y = game.food.y;
    // Call checkFood which handles food consumption
    game.checkFood();
};

describe('Single Food Item Constraint', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        mockCtx = setupDOM();
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
        if (game.gameLoop) {
            cancelAnimationFrame(game.gameLoop);
        }
    });

    /**
     * Test Case 1: Initial game state has exactly one food item
     * Input: Initial game state
     * Expected: Exactly one food item exists
     */
    describe('Test Case 1: Initial Game State - Exactly One Food Item', () => {
        test('game initialization should create exactly one food item', () => {
            game.init();

            const foodCount = countFoodItems(game);
            expect(foodCount).toBe(1);
        });

        test('food property should be a single object, not an array', () => {
            game.init();

            expect(game.food).toBeDefined();
            expect(game.food).not.toBeNull();
            expect(Array.isArray(game.food)).toBe(false);
            expect(typeof game.food).toBe('object');
        });

        test('food should have exactly two coordinate properties (x and y)', () => {
            game.init();

            expect(game.food).toHaveProperty('x');
            expect(game.food).toHaveProperty('y');
            expect(typeof game.food.x).toBe('number');
            expect(typeof game.food.y).toBe('number');
        });

        test('only one food position should exist at game start', () => {
            game.init();

            // Verify there's exactly one food position
            const foodX = game.food.x;
            const foodY = game.food.y;

            expect(Number.isInteger(foodX)).toBe(true);
            expect(Number.isInteger(foodY)).toBe(true);

            // There should be no other food references or arrays
            expect(game.foods).toBeUndefined();
            expect(game.foodItems).toBeUndefined();
            expect(game.foodList).toBeUndefined();
        });

        test('spawnFood should replace existing food, not add to collection', () => {
            game.init();

            const initialFood = { ...game.food };

            // Spawn new food multiple times
            for (let i = 0; i < 10; i++) {
                game.spawnFood();

                // Should still have exactly one food item
                const foodCount = countFoodItems(game);
                expect(foodCount).toBe(1);
            }
        });

        test('food property structure should be consistent after initialization', () => {
            game.init();

            // Check that food is a plain object with position
            const foodKeys = Object.keys(game.food);
            expect(foodKeys).toContain('x');
            expect(foodKeys).toContain('y');
        });
    });

    /**
     * Test Case 2: After food consumption, exactly one new food item exists
     * Input: After food consumption
     * Expected: Exactly one new food item exists
     */
    describe('Test Case 2: After Food Consumption - Exactly One New Food Item', () => {
        test('consuming food should result in exactly one new food item', () => {
            game.init();

            // Record initial food
            const initialFood = { ...game.food };

            // Simulate eating food
            simulateEatFood(game);

            // Verify exactly one food item exists after consumption
            const foodCount = countFoodItems(game);
            expect(foodCount).toBe(1);
        });

        test('food position should change after consumption', () => {
            game.init();

            // Move snake head to food position
            const initialFoodX = game.food.x;
            const initialFoodY = game.food.y;

            // Force food spawn to different position by manipulating random
            // Put snake away from most positions
            game.snake = [{ x: 0, y: 0 }];

            // Simulate eating multiple times to verify new positions
            let positionChanged = false;
            for (let i = 0; i < 50; i++) {
                game.spawnFood();
                if (game.food.x !== initialFoodX || game.food.y !== initialFoodY) {
                    positionChanged = true;
                    break;
                }
            }

            // Food should eventually spawn at a different position
            expect(positionChanged).toBe(true);

            // Still exactly one food item
            const foodCount = countFoodItems(game);
            expect(foodCount).toBe(1);
        });

        test('checkFood method should spawn new single food after eating', () => {
            game.init();

            // Position head at food
            game.snake[0].x = game.food.x;
            game.snake[0].y = game.food.y;

            // Before checkFood
            const beforeCount = countFoodItems(game);
            expect(beforeCount).toBe(1);

            // Call checkFood
            game.checkFood();

            // After checkFood - still exactly one
            const afterCount = countFoodItems(game);
            expect(afterCount).toBe(1);
        });

        test('consecutive food consumptions should maintain single food item', () => {
            game.init();

            // Simulate eating food multiple times
            for (let i = 0; i < 20; i++) {
                // Position head at food
                game.snake[0].x = game.food.x;
                game.snake[0].y = game.food.y;

                game.checkFood();

                // Verify single food after each consumption
                const foodCount = countFoodItems(game);
                expect(foodCount).toBe(1);
            }
        });

        test('food should never be null or undefined after consumption', () => {
            game.init();

            for (let i = 0; i < 10; i++) {
                simulateEatFood(game);

                expect(game.food).not.toBeNull();
                expect(game.food).not.toBeUndefined();
                expect(game.food.x).toBeDefined();
                expect(game.food.y).toBeDefined();
            }
        });

        test('old food position should be replaced, not persisted', () => {
            game.init();

            const foodHistory = [];

            // Collect food positions through multiple consumptions
            for (let i = 0; i < 5; i++) {
                foodHistory.push({ ...game.food });

                // Make sure snake is small to have space for food
                game.snake = [{ x: 0, y: 0 }];
                game.spawnFood();
            }

            // At any point, only one food reference should exist
            const currentFoodCount = countFoodItems(game);
            expect(currentFoodCount).toBe(1);
        });
    });

    /**
     * Test Case 3: Throughout extended gameplay, never more than one food item visible
     * Input: Throughout extended gameplay
     * Expected: Never more than one food item visible
     * Type: Integration
     */
    describe('Test Case 3: Extended Gameplay - Never More Than One Food Item (Integration)', () => {
        test('food count should remain 1 through entire game simulation', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Simulate 100 game ticks
            for (let tick = 0; tick < 100; tick++) {
                // Verify single food at each tick
                const foodCount = countFoodItems(game);
                expect(foodCount).toBe(1);

                // Occasionally eat food
                if (tick % 10 === 0) {
                    game.snake[0].x = game.food.x;
                    game.snake[0].y = game.food.y;
                    game.checkFood();
                }
            }
        });

        test('rapid food consumption should never create multiple food items', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Rapidly consume food
            for (let i = 0; i < 50; i++) {
                // Move head to food
                game.snake[0].x = game.food.x;
                game.snake[0].y = game.food.y;

                // Consume
                game.checkFood();

                // Immediate verification
                expect(countFoodItems(game)).toBe(1);
                expect(Array.isArray(game.food)).toBe(false);
            }
        });

        test('game state transitions should maintain single food item', () => {
            game.init();

            // Check in READY state
            expect(countFoodItems(game)).toBe(1);

            // Start game (PLAYING state)
            game.state = GameState.PLAYING;
            expect(countFoodItems(game)).toBe(1);

            // Pause game
            game.state = GameState.PAUSED;
            expect(countFoodItems(game)).toBe(1);

            // Resume
            game.state = GameState.PLAYING;
            expect(countFoodItems(game)).toBe(1);

            // Eat food while playing
            game.snake[0].x = game.food.x;
            game.snake[0].y = game.food.y;
            game.checkFood();
            expect(countFoodItems(game)).toBe(1);

            // Game over
            game.state = GameState.GAME_OVER;
            expect(countFoodItems(game)).toBe(1);
        });

        test('game reset should maintain single food item constraint', () => {
            game.init();

            // Play and eat some food
            game.state = GameState.PLAYING;
            simulateEatFood(game);
            simulateEatFood(game);

            expect(countFoodItems(game)).toBe(1);

            // Reset game
            game.resetGame();

            // Should still have exactly one food item
            expect(countFoodItems(game)).toBe(1);
        });

        test('extended gameplay loop simulation maintains single food constraint', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Simulate extended gameplay with movement and food consumption
            for (let round = 0; round < 30; round++) {
                // Move snake (simplified simulation)
                const head = game.snake[0];
                const newHead = { x: head.x + 1, y: head.y };
                game.snake.unshift(newHead);

                // Check if eating food
                if (newHead.x === game.food.x && newHead.y === game.food.y) {
                    game.checkFood();
                } else {
                    game.snake.pop();
                }

                // Verify constraint at each step
                expect(countFoodItems(game)).toBe(1);
                expect(game.food).toBeDefined();
                expect(game.food.x).toBeDefined();
                expect(game.food.y).toBeDefined();
            }
        });

        test('spawnFood never creates additional food instances', () => {
            game.init();

            // Track that food property is always replaced, never accumulated
            let previousFood = { ...game.food };

            for (let i = 0; i < 100; i++) {
                game.spawnFood();

                // Food should be a new object (or same position by chance)
                const currentFood = game.food;

                // Verify single food
                expect(countFoodItems(game)).toBe(1);

                // Verify it's a proper food object
                expect(typeof currentFood.x).toBe('number');
                expect(typeof currentFood.y).toBe('number');

                previousFood = { ...currentFood };
            }
        });

        test('no food array or collection exists in game state', () => {
            game.init();
            game.state = GameState.PLAYING;

            // Verify no food collection properties exist
            expect(game.foods).toBeUndefined();
            expect(game.foodItems).toBeUndefined();
            expect(game.foodList).toBeUndefined();
            expect(game.foodArray).toBeUndefined();

            // Simulate gameplay
            for (let i = 0; i < 20; i++) {
                simulateEatFood(game);

                // Re-verify no collections created
                expect(game.foods).toBeUndefined();
                expect(game.foodItems).toBeUndefined();
                expect(game.foodList).toBeUndefined();
                expect(game.foodArray).toBeUndefined();
            }
        });

        test('rendering should only draw one food item', () => {
            game.init();

            // Clear previous calls
            mockCtx.arc.mockClear();
            mockCtx.fill.mockClear();

            // Render food
            game.drawFood();

            // arc should be called exactly once for food (drawn as circle)
            expect(mockCtx.arc).toHaveBeenCalledTimes(1);
            expect(mockCtx.fill).toHaveBeenCalledTimes(1);
        });

        test('drawFood should draw at single food position', () => {
            game.init();

            // Clear and render
            mockCtx.arc.mockClear();
            game.drawFood();

            // Verify arc was called with correct food position
            const expectedX = game.food.x * CONFIG.GRID_SIZE + CONFIG.GRID_SIZE / 2;
            const expectedY = game.food.y * CONFIG.GRID_SIZE + CONFIG.GRID_SIZE / 2;

            expect(mockCtx.arc).toHaveBeenCalledWith(
                expectedX,
                expectedY,
                expect.any(Number),
                0,
                Math.PI * 2
            );
        });
    });

    /**
     * Additional edge case tests
     */
    describe('Edge Cases for Single Food Item Constraint', () => {
        test('food type should be consistent object', () => {
            game.init();

            expect(game.food.constructor).toBe(Object);

            game.spawnFood();
            expect(game.food.constructor).toBe(Object);
        });

        test('food coordinates should always be numbers', () => {
            game.init();

            for (let i = 0; i < 50; i++) {
                game.spawnFood();

                expect(typeof game.food.x).toBe('number');
                expect(typeof game.food.y).toBe('number');
                expect(Number.isFinite(game.food.x)).toBe(true);
                expect(Number.isFinite(game.food.y)).toBe(true);
            }
        });

        test('PRD requirement: One food item visible at a time', () => {
            // PRD states in Appendix: "One food item visible at a time"
            game.init();

            // Verify throughout multiple operations
            for (let i = 0; i < 10; i++) {
                // Count should always be 1
                expect(countFoodItems(game)).toBe(1);

                // Single food property
                expect(typeof game.food).toBe('object');
                expect(Array.isArray(game.food)).toBe(false);

                game.spawnFood();
            }
        });
    });
});
