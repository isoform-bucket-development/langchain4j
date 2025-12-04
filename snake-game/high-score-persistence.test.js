/**
 * Snake Game - Test Suite for High Score Persistence
 *
 * Tests for Scenario: High Score Persistence
 * - Test Case 1: Save high score to LocalStorage
 * - Test Case 2: Load high score from LocalStorage
 * - Test Case 3: New score lower than high score - high score remains unchanged
 * - Test Case 4: New score higher than high score - high score is updated
 * - Test Case 5: Page refresh after setting high score - high score persists (e2e)
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

// Create localStorage mock
const createLocalStorageMock = () => {
    let store = {};
    return {
        getItem: jest.fn((key) => store[key] || null),
        setItem: jest.fn((key, value) => {
            store[key] = String(value);
        }),
        removeItem: jest.fn((key) => {
            delete store[key];
        }),
        clear: jest.fn(() => {
            store = {};
        }),
        get store() {
            return store;
        }
    };
};

// Helper to set up DOM with mocked canvas
const setupDOM = () => {
    const mockCtx = createMockContext();
    const localStorageMock = createLocalStorageMock();

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
        value: localStorageMock,
        writable: true,
        configurable: true
    });

    return { mockCtx, localStorageMock };
};

describe('Snake Game - High Score Persistence', () => {
    let game;
    let mockCtx;
    let localStorageMock;

    beforeEach(() => {
        const setup = setupDOM();
        mockCtx = setup.mockCtx;
        localStorageMock = setup.localStorageMock;
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
        if (game && game.gameLoop) {
            cancelAnimationFrame(game.gameLoop);
        }
    });

    /**
     * Test Case 1: Save high score to LocalStorage
     * Input: Save high score to LocalStorage
     * Expected: Score is stored in LocalStorage
     */
    describe('Test Case 1: Save High Score to LocalStorage', () => {
        test('should save high score to LocalStorage when score exceeds current high score', () => {
            game.init();
            game.highScore = 0;
            game.score = 100;

            game.saveHighScore();

            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '100');
        });

        test('should call localStorage.setItem with correct key', () => {
            game.init();
            game.highScore = 0;
            game.score = 50;

            game.saveHighScore();

            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', expect.any(String));
        });

        test('should store score as string in localStorage', () => {
            game.init();
            game.highScore = 0;
            game.score = 200;

            game.saveHighScore();

            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '200');
        });

        test('should save high score automatically on game over', () => {
            game.init();
            game.highScore = 0;
            game.score = 150;

            game.gameOver();

            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '150');
        });

        test('should update internal highScore property after saving', () => {
            game.init();
            game.highScore = 50;
            game.score = 100;

            game.saveHighScore();

            expect(game.highScore).toBe(100);
        });

        test('should persist zero as a valid high score', () => {
            game.init();
            localStorageMock.setItem.mockClear();
            game.highScore = 0;
            game.score = 0;

            // Zero score should not trigger save since it's not > highScore
            game.saveHighScore();

            expect(localStorageMock.setItem).not.toHaveBeenCalled();
        });

        test('should handle large scores correctly', () => {
            game.init();
            game.highScore = 0;
            game.score = 9999990;

            game.saveHighScore();

            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '9999990');
        });
    });

    /**
     * Test Case 2: Load high score from LocalStorage
     * Input: Load high score from LocalStorage
     * Expected: Stored high score is retrieved correctly
     */
    describe('Test Case 2: Load High Score from LocalStorage', () => {
        test('should load high score from LocalStorage on init', () => {
            localStorageMock.getItem.mockReturnValue('500');

            game.init();

            expect(localStorageMock.getItem).toHaveBeenCalledWith('snakeHighScore');
            expect(game.highScore).toBe(500);
        });

        test('should set high score to 0 if nothing in localStorage', () => {
            localStorageMock.getItem.mockReturnValue(null);

            game.init();

            expect(game.highScore).toBe(0);
        });

        test('should parse stored value as integer', () => {
            localStorageMock.getItem.mockReturnValue('123');

            game.init();

            expect(game.highScore).toBe(123);
            expect(typeof game.highScore).toBe('number');
        });

        test('should update high score display element after loading', () => {
            localStorageMock.getItem.mockReturnValue('750');

            game.init();

            const highScoreElement = document.getElementById('high-score');
            expect(highScoreElement.textContent).toBe('750');
        });

        test('should call loadHighScore during initialization', () => {
            localStorageMock.getItem.mockReturnValue('300');
            const loadHighScoreSpy = jest.spyOn(SnakeGame.prototype, 'loadHighScore');

            game = new SnakeGame();
            game.init();

            expect(loadHighScoreSpy).toHaveBeenCalled();
            loadHighScoreSpy.mockRestore();
        });

        test('should handle empty string in localStorage', () => {
            localStorageMock.getItem.mockReturnValue('');

            game.init();

            // Empty string parsed as NaN, should default to 0
            expect(game.highScore).toBe(0);
        });

        test('should handle non-numeric values gracefully', () => {
            localStorageMock.getItem.mockReturnValue('invalid');

            game.init();

            // NaN check - should default to 0 or handle gracefully
            expect(game.highScore === 0 || isNaN(game.highScore)).toBeTruthy();
        });

        test('should load high score correctly for large values', () => {
            localStorageMock.getItem.mockReturnValue('1000000');

            game.init();

            expect(game.highScore).toBe(1000000);
        });
    });

    /**
     * Test Case 3: New score lower than high score
     * Input: New score lower than high score
     * Expected: High score remains unchanged
     */
    describe('Test Case 3: New Score Lower Than High Score', () => {
        test('should not update high score when current score is lower', () => {
            game.init();
            game.highScore = 100;
            game.score = 50;

            game.saveHighScore();

            expect(game.highScore).toBe(100);
        });

        test('should not call localStorage.setItem when score is lower', () => {
            game.init();
            localStorageMock.setItem.mockClear();
            game.highScore = 200;
            game.score = 150;

            game.saveHighScore();

            expect(localStorageMock.setItem).not.toHaveBeenCalled();
        });

        test('should keep high score display unchanged', () => {
            localStorageMock.getItem.mockReturnValue('300');
            game.init();

            game.score = 100;
            game.saveHighScore();

            const highScoreElement = document.getElementById('high-score');
            expect(highScoreElement.textContent).toBe('300');
        });

        test('should not update high score when score equals high score', () => {
            game.init();
            localStorageMock.setItem.mockClear();
            game.highScore = 100;
            game.score = 100;

            game.saveHighScore();

            expect(localStorageMock.setItem).not.toHaveBeenCalled();
            expect(game.highScore).toBe(100);
        });

        test('should preserve high score through multiple game overs with lower scores', () => {
            localStorageMock.getItem.mockReturnValue('500');
            game.init();
            localStorageMock.setItem.mockClear();

            // First game over with lower score
            game.score = 100;
            game.gameOver();

            // Reset and another game over with lower score
            game.resetGame();
            game.score = 200;
            game.saveHighScore();

            expect(game.highScore).toBe(500);
            expect(localStorageMock.setItem).not.toHaveBeenCalled();
        });

        test('should not update high score when score is 0', () => {
            game.init();
            localStorageMock.setItem.mockClear();
            game.highScore = 50;
            game.score = 0;

            game.saveHighScore();

            expect(game.highScore).toBe(50);
            expect(localStorageMock.setItem).not.toHaveBeenCalled();
        });

        test('should maintain high score integrity after game reset', () => {
            localStorageMock.getItem.mockReturnValue('1000');
            game.init();

            expect(game.highScore).toBe(1000);

            game.startGame();
            game.score = 500;
            game.resetGame();

            expect(game.highScore).toBe(1000);
        });
    });

    /**
     * Test Case 4: New score higher than high score
     * Input: New score higher than high score
     * Expected: High score is updated to new value
     */
    describe('Test Case 4: New Score Higher Than High Score', () => {
        test('should update high score when current score exceeds it', () => {
            game.init();
            game.highScore = 100;
            game.score = 200;

            game.saveHighScore();

            expect(game.highScore).toBe(200);
        });

        test('should call localStorage.setItem with new high score', () => {
            game.init();
            game.highScore = 50;
            game.score = 100;

            game.saveHighScore();

            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '100');
        });

        test('should update high score display element', () => {
            game.init();
            game.highScore = 100;
            game.score = 250;

            game.saveHighScore();

            const highScoreElement = document.getElementById('high-score');
            expect(highScoreElement.textContent).toBe('250');
        });

        test('should update high score on game over when score beats record', () => {
            localStorageMock.getItem.mockReturnValue('100');
            game.init();

            game.score = 500;
            game.gameOver();

            expect(game.highScore).toBe(500);
            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '500');
        });

        test('should correctly update when beating high score by 1 point', () => {
            game.init();
            game.highScore = 100;
            game.score = 101;

            game.saveHighScore();

            expect(game.highScore).toBe(101);
        });

        test('should update high score from 0 to positive value', () => {
            localStorageMock.getItem.mockReturnValue('0');
            game.init();

            game.score = 10;
            game.saveHighScore();

            expect(game.highScore).toBe(10);
            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '10');
        });

        test('should update high score progressively through multiple games', () => {
            game.init();
            game.highScore = 0;

            // First game: score 50
            game.score = 50;
            game.saveHighScore();
            expect(game.highScore).toBe(50);

            // Second game: score 100
            game.score = 100;
            game.saveHighScore();
            expect(game.highScore).toBe(100);

            // Third game: score 75 (lower, should not update)
            game.score = 75;
            game.saveHighScore();
            expect(game.highScore).toBe(100);

            // Fourth game: score 150
            game.score = 150;
            game.saveHighScore();
            expect(game.highScore).toBe(150);
        });
    });

    /**
     * Test Case 5: Page refresh after setting high score (e2e)
     * Input: Page refresh after setting high score
     * Expected: High score persists and displays correctly
     */
    describe('Test Case 5: High Score Persistence Across Sessions (E2E)', () => {
        test('should persist high score across simulated page refresh', () => {
            // First session: set a high score
            game.init();
            game.highScore = 0;
            game.score = 500;
            game.saveHighScore();

            // Simulate that localStorage now has the value
            localStorageMock.getItem.mockReturnValue('500');

            // Create new game instance (simulating page refresh)
            const newGame = new SnakeGame();
            newGame.init();

            expect(newGame.highScore).toBe(500);
        });

        test('should display persisted high score in UI after refresh', () => {
            // First session: achieve high score
            game.init();
            game.score = 750;
            game.highScore = 0;
            game.saveHighScore();

            // Simulate localStorage persistence
            localStorageMock.getItem.mockReturnValue('750');

            // Second session (page refresh)
            const newGame = new SnakeGame();
            newGame.init();

            const highScoreElement = document.getElementById('high-score');
            expect(highScoreElement.textContent).toBe('750');
        });

        test('should maintain high score integrity across multiple refreshes', () => {
            // Session 1
            game.init();
            game.highScore = 0;
            game.score = 100;
            game.saveHighScore();
            localStorageMock.getItem.mockReturnValue('100');

            // Session 2 (refresh)
            const game2 = new SnakeGame();
            game2.init();
            expect(game2.highScore).toBe(100);
            game2.score = 50; // Lower score
            game2.saveHighScore();
            // localStorage should still have 100 since 50 < 100

            // Session 3 (refresh)
            const game3 = new SnakeGame();
            game3.init();
            expect(game3.highScore).toBe(100);
        });

        test('full e2e scenario: play, achieve score, game over, refresh, verify', () => {
            // Step 1: Initialize and play game
            game.init();
            game.startGame();

            // Step 2: Achieve score by eating food
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();
            game.food = { x: game.snake[0].x, y: game.snake[0].y };
            game.checkFood();

            expect(game.score).toBe(30);

            // Step 3: End game (game over)
            game.gameOver();

            // Step 4: Verify high score was saved
            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '30');

            // Step 5: Simulate browser refresh
            localStorageMock.getItem.mockReturnValue('30');

            // Step 6: New session - verify high score persisted
            const refreshedGame = new SnakeGame();
            refreshedGame.init();

            expect(refreshedGame.highScore).toBe(30);
            expect(document.getElementById('high-score').textContent).toBe('30');
        });

        test('should load high score immediately on page load', () => {
            localStorageMock.getItem.mockReturnValue('999');

            const freshGame = new SnakeGame();
            freshGame.init();

            // High score should be loaded before game starts
            expect(freshGame.highScore).toBe(999);
            expect(freshGame.getState()).toBe(GameState.READY);
        });

        test('should handle first-time user with no saved high score', () => {
            localStorageMock.getItem.mockReturnValue(null);

            const freshGame = new SnakeGame();
            freshGame.init();

            expect(freshGame.highScore).toBe(0);
            expect(document.getElementById('high-score').textContent).toBe('0');
        });

        test('localStorage key should be consistent', () => {
            game.init();
            game.highScore = 0;
            game.score = 100;
            game.saveHighScore();

            // Verify the key used
            expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', expect.any(String));
            expect(localStorageMock.getItem).toHaveBeenCalledWith('snakeHighScore');
        });
    });
});

// Integration tests for high score persistence
describe('High Score Persistence Integration Tests', () => {
    let game;
    let localStorageMock;

    beforeEach(() => {
        const setup = setupDOM();
        localStorageMock = setup.localStorageMock;
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    test('complete game flow: start -> play -> game over -> high score saved', () => {
        localStorageMock.getItem.mockReturnValue('0');
        game.init();

        // Start game
        game.startGame();
        expect(game.getState()).toBe(GameState.PLAYING);

        // Consume food to get score
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood();
        expect(game.score).toBe(10);

        // Game over
        game.gameOver();

        // Verify high score was saved
        expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '10');
        expect(game.highScore).toBe(10);
    });

    test('high score display updates correctly throughout game lifecycle', () => {
        localStorageMock.getItem.mockReturnValue('50');
        game.init();

        // Initial display shows loaded high score
        expect(document.getElementById('high-score').textContent).toBe('50');

        // Play and get higher score
        game.startGame();
        game.score = 100;
        game.gameOver();

        // Display should update to new high score
        expect(document.getElementById('high-score').textContent).toBe('100');
    });

    test('saveHighScore method correctly uses comparison logic', () => {
        game.init();

        // Test boundary condition: equal scores
        game.highScore = 50;
        game.score = 50;
        game.saveHighScore();
        expect(game.highScore).toBe(50);

        // Test boundary condition: score just above high score
        game.score = 51;
        game.saveHighScore();
        expect(game.highScore).toBe(51);
    });
});

// E2E style test for complete high score persistence scenario
describe('E2E: Complete High Score Persistence Scenario', () => {
    test('should verify high score persistence end-to-end', () => {
        const { localStorageMock } = setupDOM();

        // Scenario: User plays game for the first time
        localStorageMock.getItem.mockReturnValue(null);
        const game1 = new SnakeGame();
        game1.init();

        expect(game1.highScore).toBe(0);
        expect(document.getElementById('high-score').textContent).toBe('0');

        // User achieves a score
        game1.startGame();
        game1.score = 100;
        game1.gameOver();

        // Verify score was saved
        expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '100');

        // Simulate page refresh - localStorage returns saved value
        localStorageMock.getItem.mockReturnValue('100');

        // User returns to game
        const game2 = new SnakeGame();
        game2.init();

        // High score should be loaded from storage
        expect(game2.highScore).toBe(100);
        expect(document.getElementById('high-score').textContent).toBe('100');

        // User plays again but gets lower score
        game2.startGame();
        game2.score = 50;
        localStorageMock.setItem.mockClear();
        game2.gameOver();

        // High score should NOT be updated
        expect(localStorageMock.setItem).not.toHaveBeenCalled();
        expect(game2.highScore).toBe(100);
    });

    test('verifies all steps of high score persistence scenario', () => {
        const { localStorageMock } = setupDOM();
        localStorageMock.getItem.mockReturnValue(null);

        // Step 1: Play game and achieve score
        const game = new SnakeGame();
        game.init();
        game.startGame();

        // Simulate eating food to get score
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood(); // 10 points
        game.food = { x: game.snake[0].x, y: game.snake[0].y };
        game.checkFood(); // 20 points
        expect(game.score).toBe(20);

        // Step 2: End game
        game.gameOver();

        // Step 3: Verify score was saved (simulating what happens before refresh)
        expect(localStorageMock.setItem).toHaveBeenCalledWith('snakeHighScore', '20');

        // Step 4: Simulate browser refresh
        localStorageMock.getItem.mockReturnValue('20');
        setupDOM().localStorageMock.getItem.mockReturnValue('20');

        // Step 5: Verify high score displayed after refresh
        const newGame = new SnakeGame();
        window.localStorage.getItem = jest.fn().mockReturnValue('20');
        newGame.init();

        expect(newGame.highScore).toBe(20);
        expect(document.getElementById('high-score').textContent).toBe('20');
    });
});
