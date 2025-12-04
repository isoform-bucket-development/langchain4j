/**
 * Snake Game - Test Suite for Visual Food Consumption Feedback
 *
 * Tests for Scenario: Visual Food Consumption Feedback
 * - Test Case 1: Food consumed - Visual feedback effect is triggered
 * - Test Case 2: Feedback timing - Feedback occurs immediately upon consumption
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
    clearRect: jest.fn(),
    save: jest.fn(),
    restore: jest.fn(),
    globalAlpha: 1
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
 */
const simulateGameFrame = (game) => {
    game.direction = game.nextDirection;
    game.moveSnake();
    game.checkFood();
};

describe('Visual Food Consumption Feedback', () => {
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
     * Test Case 1: Food consumed - Visual feedback effect is triggered
     * Input: Food consumed
     * Expected: Visual feedback effect is triggered
     */
    describe('Test Case 1: Visual Feedback Effect on Food Consumption', () => {
        test('visual feedback state should be triggered when food is consumed', () => {
            // Position food directly in front of snake head
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Simulate food consumption
            simulateGameFrame(game);

            // Verify that the visual feedback state is active
            expect(game.visualFeedbackActive).toBe(true);
        });

        test('visual feedback should have a duration property', () => {
            // Check CONFIG has feedback duration
            expect(CONFIG.FEEDBACK_DURATION).toBeDefined();
            expect(typeof CONFIG.FEEDBACK_DURATION).toBe('number');
            expect(CONFIG.FEEDBACK_DURATION).toBeGreaterThan(0);
        });

        test('visual feedback should track the last food position', () => {
            const head = game.snake[0];
            const foodPosition = { x: head.x + 1, y: head.y };
            game.food = { ...foodPosition };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);

            // The last consumed food position should be stored for visual effect
            expect(game.lastFoodConsumedPosition).toBeDefined();
            expect(game.lastFoodConsumedPosition.x).toBe(foodPosition.x);
            expect(game.lastFoodConsumedPosition.y).toBe(foodPosition.y);
        });

        test('visual feedback should have a start time for animation timing', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            const beforeConsumption = Date.now();
            simulateGameFrame(game);
            const afterConsumption = Date.now();

            // Visual feedback should track when it started
            expect(game.feedbackStartTime).toBeDefined();
            expect(game.feedbackStartTime).toBeGreaterThanOrEqual(beforeConsumption);
            expect(game.feedbackStartTime).toBeLessThanOrEqual(afterConsumption);
        });

        test('visual feedback color should be defined in CONFIG', () => {
            expect(CONFIG.FEEDBACK_COLOR).toBeDefined();
            expect(typeof CONFIG.FEEDBACK_COLOR).toBe('string');
        });

        test('visual feedback should deactivate after duration expires', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Consume food to trigger feedback
            simulateGameFrame(game);
            expect(game.visualFeedbackActive).toBe(true);

            // Simulate time passing beyond feedback duration
            game.feedbackStartTime = Date.now() - CONFIG.FEEDBACK_DURATION - 100;

            // Trigger feedback check (would happen during render)
            game.updateVisualFeedback();

            expect(game.visualFeedbackActive).toBe(false);
        });

        test('visual feedback should NOT trigger when food is not consumed', () => {
            // Position food far away from snake path
            game.food = { x: 15, y: 15 };
            const head = game.snake[0];
            if (head.x === 15 && head.y === 15) {
                game.food = { x: 0, y: 0 };
            }
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;
            game.visualFeedbackActive = false;

            simulateGameFrame(game);

            // Visual feedback should NOT be triggered
            expect(game.visualFeedbackActive).toBe(false);
        });

        test('drawFeedbackEffect method should be called during render when active', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Spy on the drawFeedbackEffect method
            const drawFeedbackSpy = jest.spyOn(game, 'drawFeedbackEffect');

            simulateGameFrame(game);
            game.render();

            expect(drawFeedbackSpy).toHaveBeenCalled();
            drawFeedbackSpy.mockRestore();
        });

        test('feedback effect should draw at the consumed food position', () => {
            const head = game.snake[0];
            const foodPosition = { x: head.x + 1, y: head.y };
            game.food = { ...foodPosition };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);
            game.render();

            // The context should have been used to draw the feedback effect
            // at the position where food was consumed
            expect(mockCtx.beginPath).toHaveBeenCalled();
        });
    });

    /**
     * Test Case 2: Feedback timing - Feedback occurs immediately upon consumption
     * Input: Feedback timing
     * Expected: Feedback occurs immediately upon consumption
     */
    describe('Test Case 2: Immediate Feedback Timing', () => {
        test('visual feedback should start immediately when food is consumed', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Store time before consumption
            const timeBeforeConsumption = Date.now();

            // Consume food
            simulateGameFrame(game);

            // Feedback should be active immediately
            expect(game.visualFeedbackActive).toBe(true);

            // Feedback start time should be within same frame (very close to consumption time)
            expect(game.feedbackStartTime - timeBeforeConsumption).toBeLessThan(100);
        });

        test('feedback should be rendered in the same frame as consumption', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Spy on render method
            const renderSpy = jest.spyOn(game, 'render');
            const drawFeedbackSpy = jest.spyOn(game, 'drawFeedbackEffect');

            // Simulate game frame (which includes food check)
            simulateGameFrame(game);

            // Manually trigger render (as update() would do)
            game.render();

            // Both render and feedback should happen
            expect(renderSpy).toHaveBeenCalled();
            expect(game.visualFeedbackActive).toBe(true);
            expect(drawFeedbackSpy).toHaveBeenCalled();

            renderSpy.mockRestore();
            drawFeedbackSpy.mockRestore();
        });

        test('checkFood should activate feedback in the same call', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Move snake to food position
            game.moveSnake();

            // Check that visual feedback is not yet active (before checkFood)
            // Note: If constructor initializes it to false, this should be false
            const beforeCheck = game.visualFeedbackActive;

            // Call checkFood
            game.checkFood();

            // Immediately after checkFood, feedback should be active
            expect(game.visualFeedbackActive).toBe(true);
        });

        test('feedback timing should not delay game rendering', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            const startTime = Date.now();

            // Perform full game cycle
            simulateGameFrame(game);
            game.render();

            const endTime = Date.now();

            // The entire process should be fast (under 50ms is reasonable for test env)
            expect(endTime - startTime).toBeLessThan(50);

            // Feedback should be active
            expect(game.visualFeedbackActive).toBe(true);
        });

        test('multiple rapid food consumptions should each trigger immediate feedback', () => {
            for (let i = 0; i < 3; i++) {
                const head = game.snake[0];
                game.food = { x: head.x + 1, y: head.y };
                game.direction = Direction.RIGHT;
                game.nextDirection = Direction.RIGHT;

                const timeBeforeConsumption = Date.now();
                simulateGameFrame(game);

                // Each consumption should immediately activate feedback
                expect(game.visualFeedbackActive).toBe(true);
                expect(game.feedbackStartTime - timeBeforeConsumption).toBeLessThan(100);

                // Simulate time passing to expire previous feedback
                game.feedbackStartTime = Date.now() - CONFIG.FEEDBACK_DURATION - 10;
                game.updateVisualFeedback();
            }
        });
    });

    /**
     * Additional integration tests for visual feedback
     */
    describe('Visual Feedback Integration Tests', () => {
        test('visual feedback should be non-disruptive (short duration)', () => {
            // Feedback duration should be short enough to not disrupt gameplay
            expect(CONFIG.FEEDBACK_DURATION).toBeLessThanOrEqual(500);
            expect(CONFIG.FEEDBACK_DURATION).toBeGreaterThanOrEqual(100);
        });

        test('feedback effect should be visually distinct (use different color)', () => {
            expect(CONFIG.FEEDBACK_COLOR).not.toBe(CONFIG.SNAKE_COLOR);
            expect(CONFIG.FEEDBACK_COLOR).not.toBe(CONFIG.FOOD_COLOR);
            expect(CONFIG.FEEDBACK_COLOR).not.toBe(CONFIG.BACKGROUND_COLOR);
        });

        test('game should continue functioning normally after feedback', () => {
            const initialLength = game.snake.length;
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            // Trigger feedback (first food consumption)
            simulateGameFrame(game);
            expect(game.visualFeedbackActive).toBe(true);

            // Growth frame for first food
            simulateGameFrame(game);
            expect(game.snake.length).toBe(initialLength + 1);

            // Expire feedback
            game.feedbackStartTime = Date.now() - CONFIG.FEEDBACK_DURATION - 10;
            game.updateVisualFeedback();
            expect(game.visualFeedbackActive).toBe(false);

            // Game should continue normally - set up new food and consume it
            const lengthAfterFirstFood = game.snake.length;
            const newHead = game.snake[0];
            game.food = { x: newHead.x + 1, y: newHead.y };

            // Second food consumption and growth
            simulateGameFrame(game);
            simulateGameFrame(game);

            // Snake should still grow normally (by 1 more)
            expect(game.snake.length).toBe(lengthAfterFirstFood + 1);
        });

        test('updateVisualFeedback method should exist', () => {
            expect(typeof game.updateVisualFeedback).toBe('function');
        });

        test('drawFeedbackEffect method should exist', () => {
            expect(typeof game.drawFeedbackEffect).toBe('function');
        });

        test('feedback effect should use canvas context for drawing', () => {
            const head = game.snake[0];
            game.food = { x: head.x + 1, y: head.y };
            game.direction = Direction.RIGHT;
            game.nextDirection = Direction.RIGHT;

            simulateGameFrame(game);
            game.drawFeedbackEffect();

            // Should use canvas context methods for drawing the effect
            expect(mockCtx.beginPath).toHaveBeenCalled();
            expect(mockCtx.arc).toHaveBeenCalled();
        });
    });
});

// E2E style integration test
describe('E2E: Visual Food Consumption Feedback Cycle', () => {
    test('complete visual feedback cycle from consumption to expiration', () => {
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

        // Step 1: Initial state - no feedback active
        expect(game.visualFeedbackActive).toBe(false);

        // Step 2: Navigate snake to food
        const head = game.snake[0];
        game.food = { x: head.x + 1, y: head.y };
        game.direction = Direction.RIGHT;
        game.nextDirection = Direction.RIGHT;

        // Step 3: Consume food - feedback should trigger immediately
        game.direction = game.nextDirection;
        game.moveSnake();
        game.checkFood();

        expect(game.visualFeedbackActive).toBe(true);
        expect(game.lastFoodConsumedPosition).toEqual({ x: head.x + 1, y: head.y });

        // Step 4: Verify visual feedback is rendered
        game.render();

        // Canvas context should have been used for feedback effect
        expect(mockCtx.beginPath).toHaveBeenCalled();

        // Step 5: Feedback expires after duration
        game.feedbackStartTime = Date.now() - CONFIG.FEEDBACK_DURATION - 10;
        game.updateVisualFeedback();

        expect(game.visualFeedbackActive).toBe(false);
    });
});
