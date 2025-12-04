/**
 * Snake Game - Test Suite for Snake Growth on Food Consumption
 *
 * Tests for Scenario: Snake Growth on Food Consumption
 * - Test Case 1: Snake head reaches food position - Snake length increases by 1
 * - Test Case 2: Food consumed - New food item spawns at random location
 * - Test Case 3: Multiple food consumptions - Snake grows correctly after each consumption
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

// Helper function to set up game environment
const setupGameEnvironment = () => {
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
 * Helper function to simulate one game frame
 * This mimics the update() function's core logic:
 * 1. moveSnake() - adds head, removes tail (unless foodEaten from previous frame)
 * 2. checkFood() - checks if head is on food, sets foodEaten flag
 */
const simulateGameFrame = (game) => {
    game.direction = game.nextDirection;
    game.moveSnake();
    game.checkFood();
};

describe('Snake Growth on Food Consumption', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        mockCtx = setupGameEnvironment();
        game = new SnakeGame();
        game.init();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Snake head reaches food position
     * Input: Snake head reaches food position
     * Expected: Snake length increases by 1
     *
     * Game Mechanics:
     * - Frame N: moveSnake() adds head, pops tail. checkFood() detects food, sets foodEaten=true
     * - Frame N+1: moveSnake() adds head, sees foodEaten=true, does NOT pop tail (growth!)
     */
    describe('Test Case 1: Snake Growth When Consuming Food', () => {
        test('snake should grow by one segment when head reaches food position', () => {
            const initialLength = game.snake.length;

            // Position food directly in front of snake head
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Frame 1: Snake moves to food position, checkFood sets foodEaten=true
            simulateGameFrame(game);

            // Frame 2: moveSnake sees foodEaten=true, doesn't pop tail = growth!
            simulateGameFrame(game);

            // Snake should have grown by exactly one segment
            expect(game.snake.length).toBe(initialLength + 1);
        });

        test('snake should NOT grow when NOT consuming food', () => {
            const initialLength = game.snake.length;

            // Position food far away from snake path
            game.food = { x: 0, y: 0 };
            const head = game.snake[0];
            if (head.x === 0 && head.y === 0) {
                game.food = { x: 15, y: 15 };
            }
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Simulate multiple frames without eating food
            simulateGameFrame(game);
            simulateGameFrame(game);
            simulateGameFrame(game);

            // Snake length should remain unchanged
            expect(game.snake.length).toBe(initialLength);
        });

        test('snake head should move to food position when consuming', () => {
            const head = game.snake[0];
            const foodPosition = { x: head.x + 1, y: head.y };
            game.food = { ...foodPosition };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.moveSnake();

            // New head should be at food position
            expect(game.snake[0].x).toBe(foodPosition.x);
            expect(game.snake[0].y).toBe(foodPosition.y);
        });

        test('score should increase when food is consumed', () => {
            const initialScore = game.score;

            // Position food at next head position
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);

            expect(game.score).toBe(initialScore + 10);
        });

        test('foodEaten flag should be set when consuming food', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.moveSnake();
            game.checkFood();

            // After checkFood(), foodEaten should be true
            expect(game.foodEaten).toBe(true);
        });

        test('snake should grow from the tail (tail preserved on growth frame)', () => {
            // Record initial tail position
            const initialTail = { ...game.snake[game.snake.length - 1] };
            const initialLength = game.snake.length;

            // Position food in front of snake
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Frame 1: Eat food (sets foodEaten)
            simulateGameFrame(game);

            // The tail from frame 1 should be preserved due to foodEaten flag
            // On growth frame, the tail is NOT removed
            // After frame 1, snake length is still initialLength (tail was popped, head added)
            // but foodEaten is now true

            // Frame 2: Growth happens (tail not removed)
            const tailBeforeGrowth = { ...game.snake[game.snake.length - 1] };
            simulateGameFrame(game);

            // After growth, snake should be longer and tail preserved
            expect(game.snake.length).toBe(initialLength + 1);

            // The tail that existed before frame 2 should still be in snake
            const tailStillExists = game.snake.some(
                segment => segment.x === tailBeforeGrowth.x && segment.y === tailBeforeGrowth.y
            );
            expect(tailStillExists).toBe(true);
        });

        test('snake head coordinates should match food coordinates when consuming', () => {
            const head = game.snake[0];
            const foodPos = { x: head.x + 1, y: head.y };
            game.food = { ...foodPos };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            game.moveSnake();

            const newHead = game.snake[0];
            expect(newHead.x).toBe(foodPos.x);
            expect(newHead.y).toBe(foodPos.y);
        });
    });

    /**
     * Test Case 2: Food consumed
     * Input: Food consumed
     * Expected: New food item spawns at random location
     */
    describe('Test Case 2: New Food Spawns After Consumption', () => {
        test('new food should spawn after consumption', () => {
            const head = game.snake[0];
            const oldFood = { x: head.x + 1, y: head.y };
            game.food = { ...oldFood };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);

            // Food should exist after consumption
            expect(game.food).toBeDefined();
            expect(game.food).not.toBeNull();
        });

        test('new food should be at valid position after consumption', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);

            // New food should be within grid bounds
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            expect(game.food.x).toBeGreaterThanOrEqual(0);
            expect(game.food.x).toBeLessThan(gridWidth);
            expect(game.food.y).toBeGreaterThanOrEqual(0);
            expect(game.food.y).toBeLessThan(gridHeight);
        });

        test('new food should NOT spawn on snake position', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);

            // Verify new food is not on any snake segment
            const foodOnSnake = game.snake.some(
                segment => segment.x === game.food.x && segment.y === game.food.y
            );
            expect(foodOnSnake).toBe(false);
        });

        test('spawnFood should always produce food within bounds', () => {
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            // Call spawnFood multiple times to verify bounds
            for (let i = 0; i < 100; i++) {
                game.spawnFood();
                expect(game.food.x).toBeGreaterThanOrEqual(0);
                expect(game.food.x).toBeLessThan(gridWidth);
                expect(game.food.y).toBeGreaterThanOrEqual(0);
                expect(game.food.y).toBeLessThan(gridHeight);
            }
        });

        test('food position should be random (different across multiple spawns)', () => {
            const positions = new Set();

            // Spawn food multiple times and collect positions
            for (let i = 0; i < 50; i++) {
                game.spawnFood();
                positions.add(`${game.food.x},${game.food.y}`);
            }

            // Should have multiple different positions (randomness)
            expect(positions.size).toBeGreaterThan(1);
        });

        test('food should spawn at empty grid position', () => {
            // Fill most of the grid with snake to test edge case
            game.snake = [];
            for (let x = 0; x < 10; x++) {
                for (let y = 0; y < 10; y++) {
                    game.snake.push({ x, y });
                }
            }

            game.spawnFood();

            // Verify food is not on snake
            const isOnSnake = game.isSnakePosition(game.food.x, game.food.y);
            expect(isOnSnake).toBe(false);
        });
    });

    /**
     * Test Case 3: Multiple food consumptions
     * Input: Multiple food consumptions
     * Expected: Snake grows correctly after each consumption
     */
    describe('Test Case 3: Multiple Food Consumptions (Integration)', () => {
        test('snake should grow correctly after multiple food consumptions', () => {
            const initialLength = game.snake.length;
            const foodsToEat = 5;

            for (let i = 0; i < foodsToEat; i++) {
                // Position food directly in front of snake head
                const head = game.snake[0];
                game.food = { x: head.x + 1, y: head.y };
                game.direction = Direction.RIGHT;
                game.nextDirection = Direction.RIGHT;

                // Frame 1: Move to food, consume it
                simulateGameFrame(game);
                // Frame 2: Growth happens
                simulateGameFrame(game);
            }

            // Snake should have grown by exactly 5 segments
            expect(game.snake.length).toBe(initialLength + foodsToEat);
        });

        test('score should accumulate correctly with multiple consumptions', () => {
            const initialScore = game.score;
            const foodsToEat = 3;
            const scorePerFood = 10;

            for (let i = 0; i < foodsToEat; i++) {
                const head = game.snake[0];
                game.food = { x: head.x + 1, y: head.y };
                game.direction = Direction.RIGHT;
                game.nextDirection = Direction.RIGHT;

                simulateGameFrame(game);
            }

            expect(game.score).toBe(initialScore + (foodsToEat * scorePerFood));
        });

        test('each food consumption should trigger new food spawn', () => {
            const foodsToEat = 5;
            const foodPositions = [];

            for (let i = 0; i < foodsToEat; i++) {
                const head = game.snake[0];
                game.food = { x: head.x + 1, y: head.y };
                game.direction = Direction.RIGHT;
                game.nextDirection = Direction.RIGHT;

                simulateGameFrame(game);

                // Record each new food position after consumption
                foodPositions.push({ ...game.food });
            }

            // All food positions should be valid (within bounds)
            const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
            const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

            foodPositions.forEach(pos => {
                expect(pos.x).toBeGreaterThanOrEqual(0);
                expect(pos.x).toBeLessThan(gridWidth);
                expect(pos.y).toBeGreaterThanOrEqual(0);
                expect(pos.y).toBeLessThan(gridHeight);
            });
        });

        test('snake segments should remain connected after growth', () => {
            // Eat 3 foods
            for (let i = 0; i < 3; i++) {
                const head = game.snake[0];
                game.food = { x: head.x + 1, y: head.y };
                game.direction = Direction.RIGHT;
                game.nextDirection = Direction.RIGHT;

                simulateGameFrame(game);
                simulateGameFrame(game);
            }

            // Verify each segment is adjacent to the next
            for (let i = 0; i < game.snake.length - 1; i++) {
                const current = game.snake[i];
                const next = game.snake[i + 1];
                const distance = Math.abs(current.x - next.x) + Math.abs(current.y - next.y);
                expect(distance).toBe(1); // Adjacent cells have distance of 1
            }
        });

        test('game speed should increase after consuming food', () => {
            const initialSpeed = game.speed;

            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);

            // Speed should decrease (faster = lower value)
            expect(game.speed).toBeLessThanOrEqual(initialSpeed);
        });

        test('snake should maintain integrity after rapid consecutive consumptions', () => {
            const consumptionCount = 10;
            const initialLength = game.snake.length;

            for (let i = 0; i < consumptionCount; i++) {
                const head = game.snake[0];
                // Position food in snake's path
                game.food = { x: head.x + 1, y: head.y };
                game.direction = Direction.RIGHT;
                game.nextDirection = Direction.RIGHT;

                // Consume food and grow
                simulateGameFrame(game);
                simulateGameFrame(game);
            }

            // Verify length increased correctly
            expect(game.snake.length).toBe(initialLength + consumptionCount);

            // Verify no duplicate positions in snake
            const positions = new Set(game.snake.map(s => `${s.x},${s.y}`));
            expect(positions.size).toBe(game.snake.length);
        });

        test('food should never spawn on growing snake', () => {
            for (let i = 0; i < 15; i++) {
                const head = game.snake[0];
                game.food = { x: head.x + 1, y: head.y };
                game.direction = Direction.RIGHT;
                game.nextDirection = Direction.RIGHT;

                simulateGameFrame(game);

                // After each consumption, verify food is not on snake
                const foodOnSnake = game.isSnakePosition(game.food.x, game.food.y);
                expect(foodOnSnake).toBe(false);

                // Also do growth frame
                simulateGameFrame(game);
            }
        });
    });

    // Edge case tests
    describe('Edge Cases for Snake Growth', () => {
        test('snake should grow when eating food at board edge', () => {
            // Position snake near edge
            game.snake = [
                { x: 18, y: 10 },
                { x: 17, y: 10 },
                { x: 16, y: 10 }
            ];
            const initialLength = game.snake.length;

            // Position food at edge
            game.food = { x: 19, y: 10 };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Consume food
            simulateGameFrame(game);
            // Growth frame
            simulateGameFrame(game);

            expect(game.snake.length).toBe(initialLength + 1);
        });

        test('snake should grow when eating food at corner', () => {
            // Position snake approaching corner
            game.snake = [
                { x: 17, y: 0 },
                { x: 16, y: 0 },
                { x: 15, y: 0 }
            ];
            const initialLength = game.snake.length;

            // Position food in corner approach
            game.food = { x: 18, y: 0 };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);
            simulateGameFrame(game);

            expect(game.snake.length).toBe(initialLength + 1);
        });

        test('isSnakePosition should correctly identify snake segments', () => {
            game.snake = [
                { x: 5, y: 5 },
                { x: 4, y: 5 },
                { x: 3, y: 5 }
            ];

            expect(game.isSnakePosition(5, 5)).toBe(true);
            expect(game.isSnakePosition(4, 5)).toBe(true);
            expect(game.isSnakePosition(3, 5)).toBe(true);
            expect(game.isSnakePosition(6, 5)).toBe(false);
            expect(game.isSnakePosition(5, 6)).toBe(false);
        });
    });
});

// Integration test simulating actual gameplay
describe('Integration Test: Complete Food Consumption Cycle', () => {
    test('full gameplay cycle: navigate, consume, grow, respawn food', () => {
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

        // Initial state
        const initialLength = game.snake.length;
        const initialScore = game.score;

        // Step 1: Navigate snake towards food (position food in front)
        const head = game.snake[0];
        game.food = { x: head.x + 1, y: head.y };
        game.direction = Direction.RIGHT;
        game.nextDirection = Direction.RIGHT;

        // Step 2: Consume food (move snake head to food position)
        game.moveSnake();
        expect(game.snake[0].x).toBe(head.x + 1);
        expect(game.snake[0].y).toBe(head.y);

        // checkFood detects consumption, sets foodEaten, spawns new food
        game.checkFood();
        expect(game.foodEaten).toBe(true);
        expect(game.score).toBe(initialScore + 10);

        // Step 3: Verify snake growth on next frame
        game.moveSnake();
        expect(game.snake.length).toBe(initialLength + 1);

        // Step 4: Verify new food spawns at valid location
        const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
        const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;
        expect(game.food.x).toBeGreaterThanOrEqual(0);
        expect(game.food.x).toBeLessThan(gridWidth);
        expect(game.food.y).toBeGreaterThanOrEqual(0);
        expect(game.food.y).toBeLessThan(gridHeight);

        // New food should not be on snake
        const foodOnSnake = game.isSnakePosition(game.food.x, game.food.y);
        expect(foodOnSnake).toBe(false);
    });
});
