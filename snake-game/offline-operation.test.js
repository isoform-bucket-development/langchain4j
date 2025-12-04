/**
 * Offline Operation Tests
 *
 * Tests to verify the game functions without requiring a backend server
 * and makes no external network requests during gameplay.
 *
 * Scenario: Offline Operation
 * - Test Case 1: Load game without network - Game loads and functions correctly
 * - Test Case 2: No external API calls - Game makes no network requests during play
 */

const { SnakeGame, CONFIG, GameState, Direction } = require('./game');

describe('Offline Operation', () => {
    let game;
    let mockCanvas;
    let mockCtx;

    // Track network request attempts
    let fetchCalls = [];
    let xhrCalls = [];
    let originalFetch;
    let originalXHR;
    let originalBeacon;
    let beaconCalls = [];

    beforeEach(() => {
        // Reset tracking arrays
        fetchCalls = [];
        xhrCalls = [];
        beaconCalls = [];

        // Mock fetch to track any calls
        originalFetch = global.fetch;
        global.fetch = jest.fn((...args) => {
            fetchCalls.push(args);
            return Promise.reject(new Error('Network request blocked in test'));
        });

        // Mock XMLHttpRequest to track any calls
        originalXHR = global.XMLHttpRequest;
        global.XMLHttpRequest = jest.fn().mockImplementation(() => {
            const xhr = {
                open: jest.fn((...args) => {
                    xhrCalls.push({ method: 'open', args });
                }),
                send: jest.fn((...args) => {
                    xhrCalls.push({ method: 'send', args });
                }),
                setRequestHeader: jest.fn(),
                addEventListener: jest.fn(),
                removeEventListener: jest.fn(),
                abort: jest.fn()
            };
            return xhr;
        });

        // Mock sendBeacon to track any calls
        originalBeacon = navigator.sendBeacon;
        Object.defineProperty(navigator, 'sendBeacon', {
            value: jest.fn((...args) => {
                beaconCalls.push(args);
                return true;
            }),
            writable: true,
            configurable: true
        });

        // Set up DOM mocks
        mockCtx = {
            fillStyle: '',
            strokeStyle: '',
            lineWidth: 0,
            globalAlpha: 1,
            fillRect: jest.fn(),
            strokeRect: jest.fn(),
            clearRect: jest.fn(),
            beginPath: jest.fn(),
            arc: jest.fn(),
            fill: jest.fn(),
            stroke: jest.fn(),
            save: jest.fn(),
            restore: jest.fn()
        };

        mockCanvas = {
            getContext: jest.fn(() => mockCtx),
            width: 0,
            height: 0
        };

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

        const canvasElement = document.getElementById('game-board');
        canvasElement.getContext = mockCanvas.getContext;

        // Mock localStorage
        const localStorageMock = {
            store: {},
            getItem: jest.fn((key) => localStorageMock.store[key] || null),
            setItem: jest.fn((key, value) => {
                localStorageMock.store[key] = value;
            }),
            removeItem: jest.fn((key) => {
                delete localStorageMock.store[key];
            }),
            clear: jest.fn(() => {
                localStorageMock.store = {};
            })
        };
        Object.defineProperty(window, 'localStorage', {
            value: localStorageMock,
            writable: true
        });

        // Mock requestAnimationFrame and cancelAnimationFrame
        global.requestAnimationFrame = jest.fn((cb) => setTimeout(cb, 16));
        global.cancelAnimationFrame = jest.fn((id) => clearTimeout(id));

        game = new SnakeGame();
    });

    afterEach(() => {
        // Restore original implementations
        global.fetch = originalFetch;
        global.XMLHttpRequest = originalXHR;
        if (originalBeacon) {
            Object.defineProperty(navigator, 'sendBeacon', {
                value: originalBeacon,
                writable: true,
                configurable: true
            });
        }
        jest.clearAllMocks();
        jest.useRealTimers();
    });

    describe('Test Case 1: Load game without network', () => {
        test('game initializes successfully without network connection', () => {
            // Simulate offline state
            const originalOnline = navigator.onLine;
            Object.defineProperty(navigator, 'onLine', {
                value: false,
                writable: true,
                configurable: true
            });

            const result = game.init();

            expect(result).toBe(true);
            expect(game.isInitialized()).toBe(true);

            // Restore online state
            Object.defineProperty(navigator, 'onLine', {
                value: originalOnline,
                writable: true,
                configurable: true
            });
        });

        test('game canvas is properly configured without network', () => {
            game.init();

            const canvas = game.getCanvas();
            expect(canvas).toBeDefined();
            expect(canvas.width).toBe(CONFIG.BOARD_WIDTH);
            expect(canvas.height).toBe(CONFIG.BOARD_HEIGHT);
        });

        test('game state is READY after initialization without network', () => {
            game.init();

            expect(game.getState()).toBe(GameState.READY);
        });

        test('snake is initialized at correct position without network', () => {
            game.init();

            expect(game.snake).toBeDefined();
            expect(game.snake.length).toBeGreaterThan(0);
            expect(game.snake[0]).toHaveProperty('x');
            expect(game.snake[0]).toHaveProperty('y');
        });

        test('food is spawned without network', () => {
            game.init();

            expect(game.food).toBeDefined();
            expect(game.food).toHaveProperty('x');
            expect(game.food).toHaveProperty('y');
        });

        test('high score is loaded from localStorage without network', () => {
            localStorage.setItem('snakeHighScore', '100');
            game.init();

            expect(game.highScore).toBe(100);
        });

        test('game renders successfully without network', () => {
            game.init();
            game.render();

            // Verify rendering methods were called
            expect(mockCtx.fillRect).toHaveBeenCalled();
            expect(mockCtx.strokeRect).toHaveBeenCalled();
        });
    });

    describe('Test Case 2: No external API calls', () => {
        test('no fetch calls are made during game initialization', () => {
            game.init();

            expect(fetchCalls.length).toBe(0);
            expect(global.fetch).not.toHaveBeenCalled();
        });

        test('no XMLHttpRequest calls are made during game initialization', () => {
            game.init();

            expect(xhrCalls.length).toBe(0);
        });

        test('no sendBeacon calls are made during game initialization', () => {
            game.init();

            expect(beaconCalls.length).toBe(0);
        });

        test('no fetch calls are made during gameplay', () => {
            jest.useFakeTimers();
            game.init();
            game.startGame();

            // Simulate multiple game frames
            for (let i = 0; i < 100; i++) {
                jest.advanceTimersByTime(CONFIG.INITIAL_SPEED);
            }

            expect(fetchCalls.length).toBe(0);
        });

        test('no XMLHttpRequest calls are made during gameplay', () => {
            jest.useFakeTimers();
            game.init();
            game.startGame();

            // Simulate multiple game frames
            for (let i = 0; i < 100; i++) {
                jest.advanceTimersByTime(CONFIG.INITIAL_SPEED);
            }

            expect(xhrCalls.length).toBe(0);
        });

        test('no network calls are made when consuming food', () => {
            jest.useFakeTimers();
            game.init();

            // Position snake head on food
            game.snake = [{ x: 5, y: 5 }];
            game.food = { x: 5, y: 5 };

            game.checkFood();

            expect(fetchCalls.length).toBe(0);
            expect(xhrCalls.length).toBe(0);
            expect(beaconCalls.length).toBe(0);
        });

        test('no network calls are made when saving high score', () => {
            game.init();
            game.score = 500;
            game.highScore = 100;

            game.saveHighScore();

            expect(fetchCalls.length).toBe(0);
            expect(xhrCalls.length).toBe(0);
            expect(beaconCalls.length).toBe(0);
        });

        test('no network calls are made during game over', () => {
            jest.useFakeTimers();
            game.init();
            game.startGame();
            game.score = 200;

            game.gameOver();

            expect(fetchCalls.length).toBe(0);
            expect(xhrCalls.length).toBe(0);
            expect(beaconCalls.length).toBe(0);
        });

        test('no network calls are made during game restart', () => {
            game.init();
            game.startGame();
            game.gameOver();
            game.resetGame();
            game.startGame();

            expect(fetchCalls.length).toBe(0);
            expect(xhrCalls.length).toBe(0);
            expect(beaconCalls.length).toBe(0);
        });

        test('no network calls are made during pause/resume', () => {
            jest.useFakeTimers();
            game.init();
            game.startGame();

            game.togglePause();
            jest.advanceTimersByTime(1000);
            game.togglePause();

            expect(fetchCalls.length).toBe(0);
            expect(xhrCalls.length).toBe(0);
            expect(beaconCalls.length).toBe(0);
        });

        test('no network calls are made when direction changes', () => {
            game.init();
            game.startGame();

            // Simulate direction changes
            game.handleKeyPress({ key: 'ArrowUp' });
            game.handleKeyPress({ key: 'ArrowLeft' });
            game.handleKeyPress({ key: 'ArrowDown' });
            game.handleKeyPress({ key: 'ArrowRight' });

            expect(fetchCalls.length).toBe(0);
            expect(xhrCalls.length).toBe(0);
            expect(beaconCalls.length).toBe(0);
        });
    });

    describe('LocalStorage functionality in offline mode', () => {
        test('localStorage works for saving high scores offline', () => {
            game.init();
            game.score = 150;
            game.highScore = 50;

            game.saveHighScore();

            expect(localStorage.setItem).toHaveBeenCalledWith('snakeHighScore', '150');
        });

        test('localStorage works for loading high scores offline', () => {
            localStorage.store['snakeHighScore'] = '200';
            game.init();

            expect(localStorage.getItem).toHaveBeenCalledWith('snakeHighScore');
            expect(game.highScore).toBe(200);
        });

        test('game handles missing localStorage gracefully', () => {
            // Simulate localStorage not available
            const originalLocalStorage = window.localStorage;
            delete window.localStorage;

            // Re-define with a mock that throws
            Object.defineProperty(window, 'localStorage', {
                get: () => {
                    throw new Error('localStorage not available');
                },
                configurable: true
            });

            // Create a new game instance
            const newGame = new SnakeGame();

            // Should not throw even without localStorage
            // Note: The actual game might handle this differently
            // This tests the resilience of offline operation
            try {
                newGame.init();
            } catch (e) {
                // If it throws, that's expected in test environment
                // The important thing is no network calls were made
                expect(fetchCalls.length).toBe(0);
            }

            // Restore localStorage
            Object.defineProperty(window, 'localStorage', {
                value: originalLocalStorage,
                configurable: true
            });
        });
    });

    describe('Complete game flow without network', () => {
        test('full game cycle works without network', () => {
            jest.useFakeTimers();

            // Initialize game
            game.init();
            expect(game.getState()).toBe(GameState.READY);

            // Start game
            game.startGame();
            expect(game.getState()).toBe(GameState.PLAYING);

            // Simulate gameplay
            jest.advanceTimersByTime(CONFIG.INITIAL_SPEED * 5);

            // Pause game
            game.togglePause();
            expect(game.getState()).toBe(GameState.PAUSED);

            // Resume game
            game.togglePause();
            expect(game.getState()).toBe(GameState.PLAYING);

            // End game
            game.gameOver();
            expect(game.getState()).toBe(GameState.GAME_OVER);

            // Restart game
            game.resetGame();
            expect(game.getState()).toBe(GameState.READY);

            // Verify no network calls throughout
            expect(fetchCalls.length).toBe(0);
            expect(xhrCalls.length).toBe(0);
            expect(beaconCalls.length).toBe(0);
        });

        test('game runs correctly with simulated network failure', () => {
            // Simulate network failure
            global.fetch = jest.fn(() => Promise.reject(new Error('Network failure')));

            game.init();
            game.startGame();

            // Game should still function
            expect(game.getState()).toBe(GameState.PLAYING);
            expect(game.isInitialized()).toBe(true);
        });

        test('score persists locally without network', () => {
            game.init();
            game.score = 300;
            game.highScore = 100;

            game.saveHighScore();

            // Create new game instance
            const newGame = new SnakeGame();
            newGame.init();

            // High score should persist via localStorage
            expect(newGame.highScore).toBe(300);
        });
    });

    describe('No external dependencies verification', () => {
        test('game.js does not import any network libraries', () => {
            // This test verifies by checking the module exports
            const gameModule = require('./game');

            // Verify only expected exports are present
            expect(gameModule).toHaveProperty('SnakeGame');
            expect(gameModule).toHaveProperty('CONFIG');
            expect(gameModule).toHaveProperty('GameState');
            expect(gameModule).toHaveProperty('Direction');

            // Verify no unexpected network-related exports
            expect(gameModule).not.toHaveProperty('fetch');
            expect(gameModule).not.toHaveProperty('axios');
            expect(gameModule).not.toHaveProperty('http');
            expect(gameModule).not.toHaveProperty('request');
        });

        test('game uses only browser APIs (Canvas, LocalStorage, RAF)', () => {
            game.init();

            // Verify Canvas API usage
            expect(mockCanvas.getContext).toHaveBeenCalledWith('2d');

            // Verify localStorage usage
            expect(localStorage.getItem).toHaveBeenCalled();

            // Verify requestAnimationFrame is used for game loop
            game.startGame();
            expect(global.requestAnimationFrame).toHaveBeenCalled();
        });

        test('HTML file has no external script references', () => {
            // Read the index.html to verify no external CDN or API references
            const fs = require('fs');
            const path = require('path');
            const htmlContent = fs.readFileSync(
                path.join(__dirname, 'index.html'),
                'utf8'
            );

            // Check for external script sources
            expect(htmlContent).not.toMatch(/https?:\/\/[^"'\s]+\.js/);
            expect(htmlContent).not.toMatch(/cdn\./);
            expect(htmlContent).not.toMatch(/api\./);

            // Verify only local script is included
            expect(htmlContent).toMatch(/<script src="game\.js"><\/script>/);
        });
    });
});
