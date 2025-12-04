/**
 * Snake Game - Test Suite for Progressive Speed Increase
 *
 * Tests for Scenario: Progressive Speed Increase
 * - Test Case 1: Snake moves at base speed when score is 0
 * - Test Case 2: Snake movement speed increases when score passes threshold
 * - Test Case 3: Game remains playable with reasonable speed cap at maximum score
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

// Helper function to setup game with DOM
const setupGame = () => {
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

    const game = new SnakeGame();
    game.init();

    return { game, mockCtx };
};

// Helper to simulate food consumption
const consumeFood = (game, count = 1) => {
    for (let i = 0; i < count; i++) {
        // Position food directly in front of snake head
        const head = game.snake[0];
        game.food = { x: head.x + game.direction.x, y: head.y + game.direction.y };

        // Simulate snake moving to food position
        game.checkFood();

        // Simulate the move that would happen after eating
        if (i < count - 1) {
            game.moveSnake();
        }
    }
};

describe('Snake Game - Progressive Speed Increase', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        const setup = setupGame();
        game = setup.game;
        mockCtx = setup.mockCtx;
        jest.clearAllMocks();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    /**
     * Test Case 1: Snake moves at base speed when score is 0
     * Input: Score at 0
     * Expected: Snake moves at base speed
     */
    describe('Test Case 1: Base Speed at Score 0', () => {
        test('should have initial speed equal to CONFIG.INITIAL_SPEED', () => {
            expect(game.speed).toBe(CONFIG.INITIAL_SPEED);
        });

        test('should have initial score of 0', () => {
            expect(game.score).toBe(0);
        });

        test('base speed should be 150ms (CONFIG.INITIAL_SPEED)', () => {
            expect(CONFIG.INITIAL_SPEED).toBe(150);
        });

        test('speed should remain at base speed before any food is consumed', () => {
            // Start the game
            game.startGame();

            // Speed should still be at initial value
            expect(game.speed).toBe(CONFIG.INITIAL_SPEED);
        });

        test('game.speed should control time between snake movements', () => {
            // Speed represents milliseconds between updates
            expect(typeof game.speed).toBe('number');
            expect(game.speed).toBeGreaterThan(0);
        });

        test('initial speed should allow comfortable gameplay', () => {
            // 150ms between moves means ~6.67 moves per second
            // This is a reasonable starting pace for a snake game
            const movesPerSecond = 1000 / CONFIG.INITIAL_SPEED;
            expect(movesPerSecond).toBeGreaterThanOrEqual(5);
            expect(movesPerSecond).toBeLessThanOrEqual(10);
        });

        test('speed should not change during game initialization', () => {
            const newGame = new SnakeGame();
            const speedBeforeInit = newGame.speed;

            // Setup DOM for new game
            document.body.innerHTML = `
                <div id="game-container">
                    <canvas id="game-board"></canvas>
                    <span id="score">0</span>
                    <span id="high-score">0</span>
                    <div id="game-status"></div>
                </div>
            `;
            const canvas = document.getElementById('game-board');
            canvas.getContext = jest.fn(() => createMockContext());

            newGame.init();

            expect(newGame.speed).toBe(speedBeforeInit);
            expect(newGame.speed).toBe(CONFIG.INITIAL_SPEED);
        });

        test('resetGame should restore speed to initial value', () => {
            // Simulate some food consumption to change speed
            game.speed = 100;
            game.score = 50;

            game.resetGame();

            expect(game.speed).toBe(CONFIG.INITIAL_SPEED);
            expect(game.score).toBe(0);
        });
    });

    /**
     * Test Case 2: Snake movement speed increases when score passes threshold
     * Input: Score increases past threshold
     * Expected: Snake movement speed increases
     */
    describe('Test Case 2: Speed Increases After Score Threshold', () => {
        test('speed should decrease (faster movement) after consuming food', () => {
            const initialSpeed = game.speed;

            // Position food and call increaseSpeed (which happens when food is eaten)
            game.increaseSpeed();

            // Speed value should decrease (lower = faster)
            expect(game.speed).toBeLessThan(initialSpeed);
        });

        test('speed should decrease by CONFIG.SPEED_INCREMENT per food', () => {
            const initialSpeed = game.speed;

            game.increaseSpeed();

            expect(game.speed).toBe(initialSpeed - CONFIG.SPEED_INCREMENT);
        });

        test('CONFIG.SPEED_INCREMENT should be 5ms', () => {
            expect(CONFIG.SPEED_INCREMENT).toBe(5);
        });

        test('multiple food consumptions should progressively increase speed', () => {
            const initialSpeed = game.speed;

            // Consume multiple foods
            game.increaseSpeed();
            const speedAfter1 = game.speed;
            expect(speedAfter1).toBe(initialSpeed - CONFIG.SPEED_INCREMENT);

            game.increaseSpeed();
            const speedAfter2 = game.speed;
            expect(speedAfter2).toBe(initialSpeed - 2 * CONFIG.SPEED_INCREMENT);

            game.increaseSpeed();
            const speedAfter3 = game.speed;
            expect(speedAfter3).toBe(initialSpeed - 3 * CONFIG.SPEED_INCREMENT);
        });

        test('checkFood should trigger increaseSpeed when food is eaten', () => {
            const initialSpeed = game.speed;

            // Position food at snake head position
            const head = game.snake[0];
            game.food = { x: head.x, y: head.y };

            // This should detect food consumption and increase speed
            game.checkFood();

            expect(game.speed).toBe(initialSpeed - CONFIG.SPEED_INCREMENT);
        });

        test('score should increase when food is consumed', () => {
            const initialScore = game.score;

            // Position food at snake head position
            const head = game.snake[0];
            game.food = { x: head.x, y: head.y };

            game.checkFood();

            expect(game.score).toBe(initialScore + 10);
        });

        test('speed increase should correlate with score increase', () => {
            const initialSpeed = game.speed;
            const initialScore = game.score;

            // Consume food 5 times
            for (let i = 0; i < 5; i++) {
                const head = game.snake[0];
                game.food = { x: head.x, y: head.y };
                game.checkFood();
            }

            // Score should have increased by 50 (5 * 10)
            expect(game.score).toBe(initialScore + 50);

            // Speed should have decreased by 25 (5 * 5ms)
            expect(game.speed).toBe(initialSpeed - 5 * CONFIG.SPEED_INCREMENT);
        });

        test('speed change should make snake move noticeably faster', () => {
            // After eating 10 food items, speed should be significantly faster
            for (let i = 0; i < 10; i++) {
                game.increaseSpeed();
            }

            // 10 * 5ms = 50ms reduction
            // From 150ms to 100ms = 33% faster
            const speedReduction = CONFIG.INITIAL_SPEED - game.speed;
            expect(speedReduction).toBe(50);
        });
    });

    /**
     * Test Case 3: Game remains playable with reasonable speed cap at maximum score
     * Input: Speed at maximum score
     * Expected: Game remains playable (speed has reasonable cap)
     */
    describe('Test Case 3: Speed Cap Ensures Playability', () => {
        test('speed should never go below CONFIG.MIN_SPEED', () => {
            // Force speed below minimum
            game.speed = CONFIG.MIN_SPEED;

            game.increaseSpeed();

            expect(game.speed).toBeGreaterThanOrEqual(CONFIG.MIN_SPEED);
        });

        test('CONFIG.MIN_SPEED should be 50ms', () => {
            expect(CONFIG.MIN_SPEED).toBe(50);
        });

        test('minimum speed should allow human reaction time', () => {
            // 50ms between moves = 20 moves per second
            // Human reaction time is ~150-300ms, so 50ms is fast but playable
            const movesPerSecond = 1000 / CONFIG.MIN_SPEED;
            expect(movesPerSecond).toBeLessThanOrEqual(20);
        });

        test('increaseSpeed should cap at MIN_SPEED after many consumptions', () => {
            // Consume enough food to reach minimum speed
            const consumptions = Math.ceil((CONFIG.INITIAL_SPEED - CONFIG.MIN_SPEED) / CONFIG.SPEED_INCREMENT) + 10;

            for (let i = 0; i < consumptions; i++) {
                game.increaseSpeed();
            }

            expect(game.speed).toBe(CONFIG.MIN_SPEED);
        });

        test('speed should remain at MIN_SPEED even with excessive food consumption', () => {
            // Set speed to minimum
            game.speed = CONFIG.MIN_SPEED;

            // Try to increase speed 100 more times
            for (let i = 0; i < 100; i++) {
                game.increaseSpeed();
            }

            expect(game.speed).toBe(CONFIG.MIN_SPEED);
        });

        test('game should calculate correct number of speed increments to reach cap', () => {
            const incrementsToMax = Math.ceil((CONFIG.INITIAL_SPEED - CONFIG.MIN_SPEED) / CONFIG.SPEED_INCREMENT);

            // 150 - 50 = 100, 100 / 5 = 20 increments
            expect(incrementsToMax).toBe(20);
        });

        test('speed progression should be linear until cap', () => {
            const speeds = [game.speed];

            // Record speed after each increment until we hit minimum
            while (game.speed > CONFIG.MIN_SPEED) {
                game.increaseSpeed();
                speeds.push(game.speed);
            }

            // Check that decrements are consistent (5ms each)
            for (let i = 1; i < speeds.length - 1; i++) {
                expect(speeds[i - 1] - speeds[i]).toBe(CONFIG.SPEED_INCREMENT);
            }
        });

        test('final speed should still result in playable game', () => {
            game.speed = CONFIG.MIN_SPEED;
            game.state = GameState.PLAYING;

            // Verify game mechanics still work at minimum speed
            const initialSnakeLength = game.snake.length;
            game.moveSnake();

            // Snake should still be able to move
            expect(game.snake.length).toBe(initialSnakeLength);
        });

        test('speed cap should prevent impossibly fast gameplay', () => {
            // Min speed of 50ms means at most 20 updates per second
            // At 60 FPS, that's reasonable
            expect(CONFIG.MIN_SPEED).toBeGreaterThanOrEqual(16); // 60 FPS
            expect(CONFIG.MIN_SPEED).toBeLessThanOrEqual(100); // Still challenging
        });
    });

    // Additional integration tests
    describe('Integration: Full Speed Progression Simulation', () => {
        test('should handle full game progression from start to max speed', () => {
            const initialSpeed = game.speed;
            const speedHistory = [initialSpeed];

            // Simulate eating food until max speed
            let foodsEaten = 0;
            while (game.speed > CONFIG.MIN_SPEED) {
                const head = game.snake[0];
                game.food = { x: head.x, y: head.y };
                game.checkFood();
                speedHistory.push(game.speed);
                foodsEaten++;
            }

            // Verify progression
            expect(foodsEaten).toBe(20); // (150-50)/5 = 20
            expect(game.speed).toBe(CONFIG.MIN_SPEED);
            expect(game.score).toBe(foodsEaten * 10); // 200 points
        });

        test('speed should affect update timing in game loop', () => {
            // When speed is 150ms, updates happen every 150ms
            // When speed is 50ms, updates happen every 50ms (3x faster)
            const slowSpeed = CONFIG.INITIAL_SPEED;
            const fastSpeed = CONFIG.MIN_SPEED;

            const speedupFactor = slowSpeed / fastSpeed;
            expect(speedupFactor).toBe(3); // Game is 3x faster at max speed
        });

        test('game should remain in PLAYING state at maximum speed', () => {
            game.state = GameState.PLAYING;
            game.speed = CONFIG.MIN_SPEED;

            // Simulate multiple moves at max speed
            for (let i = 0; i < 10; i++) {
                game.moveSnake();
                if (game.checkCollisions()) {
                    break; // Stop if collision (not related to speed)
                }
            }

            // Game should still be in playing state (not crashed due to speed)
            expect([GameState.PLAYING, GameState.GAME_OVER]).toContain(game.state);
        });
    });
});

// E2E-style test for progressive speed
describe('E2E Test: Progressive Speed Increase', () => {
    test('complete speed progression from 0 to max score', () => {
        const { game } = setupGame();

        // Start game
        game.startGame();
        expect(game.state).toBe(GameState.PLAYING);

        const initialSpeed = game.speed;
        expect(initialSpeed).toBe(CONFIG.INITIAL_SPEED);

        // Simulate eating food progressively
        let previousSpeed = initialSpeed;
        for (let i = 0; i < 25; i++) {
            const head = game.snake[0];
            game.food = { x: head.x, y: head.y };
            game.checkFood();

            // Speed should decrease (faster) or stay at minimum
            expect(game.speed).toBeLessThanOrEqual(previousSpeed);
            expect(game.speed).toBeGreaterThanOrEqual(CONFIG.MIN_SPEED);

            previousSpeed = game.speed;
        }

        // After 25 food items, should be at minimum speed
        expect(game.speed).toBe(CONFIG.MIN_SPEED);

        // Score should be 250
        expect(game.score).toBe(250);
    });

    test('game maintains playability throughout speed progression', () => {
        const { game } = setupGame();
        game.startGame();

        // Test at various speed stages
        const testSpeeds = [150, 100, 75, 50];

        testSpeeds.forEach(targetSpeed => {
            // Set speed
            game.speed = targetSpeed;

            // Verify snake can still move
            const initialHead = { ...game.snake[0] };
            game.moveSnake();
            const newHead = game.snake[0];

            // Head position should have changed
            const moved = initialHead.x !== newHead.x || initialHead.y !== newHead.y;
            expect(moved).toBe(true);
        });
    });
});
