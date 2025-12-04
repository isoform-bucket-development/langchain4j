/**
 * Snake Game - Game Board Initialization and Core Logic
 *
 * This module handles the initialization and rendering of the game board,
 * including canvas setup, boundary drawing, and game state management.
 */

// Game configuration constants
const CONFIG = {
    BOARD_WIDTH: 400,
    BOARD_HEIGHT: 400,
    GRID_SIZE: 20,
    BORDER_WIDTH: 2,
    BORDER_COLOR: '#4ecca3',
    BACKGROUND_COLOR: '#0f0f23',
    SNAKE_COLOR: '#4ecca3',
    FOOD_COLOR: '#ff6b6b',
    INITIAL_SPEED: 150,
    SPEED_INCREMENT: 5,
    MIN_SPEED: 50,
    FEEDBACK_DURATION: 200,
    FEEDBACK_COLOR: '#ffff00'
};

// Game states
const GameState = {
    READY: 'ready',
    PLAYING: 'playing',
    PAUSED: 'paused',
    GAME_OVER: 'game_over'
};

// Direction constants
const Direction = {
    UP: { x: 0, y: -1 },
    DOWN: { x: 0, y: 1 },
    LEFT: { x: -1, y: 0 },
    RIGHT: { x: 1, y: 0 }
};

/**
 * SnakeGame class - Main game controller
 */
class SnakeGame {
    constructor() {
        this.canvas = null;
        this.ctx = null;
        this.state = GameState.READY;
        this.score = 0;
        this.highScore = 0;
        this.snake = [];
        this.food = null;
        this.direction = Direction.RIGHT;
        this.nextDirection = Direction.RIGHT;
        this.gameLoop = null;
        this.speed = CONFIG.INITIAL_SPEED;
        this.lastUpdateTime = 0;
        this.initialized = false;
        this.loadStartTime = Date.now();
        this.visualFeedbackActive = false;
        this.feedbackStartTime = 0;
        this.lastFoodConsumedPosition = null;
    }

    /**
     * Initialize the game board and canvas
     * @returns {boolean} True if initialization was successful
     */
    init() {
        try {
            // Get canvas element
            this.canvas = document.getElementById('game-board');
            if (!this.canvas) {
                throw new Error('Canvas element not found');
            }

            // Set canvas dimensions
            this.canvas.width = CONFIG.BOARD_WIDTH;
            this.canvas.height = CONFIG.BOARD_HEIGHT;

            // Get 2D rendering context
            this.ctx = this.canvas.getContext('2d');
            if (!this.ctx) {
                throw new Error('Could not get 2D context');
            }

            // Load high score from localStorage
            this.loadHighScore();

            // Initialize snake at starting position
            this.initSnake();

            // Spawn initial food
            this.spawnFood();

            // Set up keyboard controls
            this.setupControls();

            // Draw initial game board
            this.render();

            // Update status display
            this.updateStatus('Press any key to start');

            this.initialized = true;

            // Record load time
            const loadTime = Date.now() - this.loadStartTime;
            console.log(`Game initialized in ${loadTime}ms`);

            return true;
        } catch (error) {
            console.error('Game initialization failed:', error);
            return false;
        }
    }

    /**
     * Initialize the snake at starting position
     */
    initSnake() {
        const startX = Math.floor(CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE / 2);
        const startY = Math.floor(CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE / 2);

        this.snake = [
            { x: startX, y: startY },
            { x: startX - 1, y: startY },
            { x: startX - 2, y: startY }
        ];
    }

    /**
     * Spawn food at a random empty position
     */
    spawnFood() {
        const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
        const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

        let newFood;
        do {
            newFood = {
                x: Math.floor(Math.random() * gridWidth),
                y: Math.floor(Math.random() * gridHeight)
            };
        } while (this.isSnakePosition(newFood.x, newFood.y));

        this.food = newFood;
    }

    /**
     * Check if a position is occupied by the snake
     * @param {number} x - X coordinate
     * @param {number} y - Y coordinate
     * @returns {boolean} True if position is occupied by snake
     */
    isSnakePosition(x, y) {
        return this.snake.some(segment => segment.x === x && segment.y === y);
    }

    /**
     * Load high score from localStorage
     */
    loadHighScore() {
        const saved = localStorage.getItem('snakeHighScore');
        this.highScore = saved ? parseInt(saved, 10) : 0;
        this.updateHighScoreDisplay();
    }

    /**
     * Save high score to localStorage
     */
    saveHighScore() {
        if (this.score > this.highScore) {
            this.highScore = this.score;
            localStorage.setItem('snakeHighScore', this.highScore.toString());
            this.updateHighScoreDisplay();
        }
    }

    /**
     * Set up keyboard controls
     */
    setupControls() {
        document.addEventListener('keydown', (event) => {
            this.handleKeyPress(event);
        });
    }

    /**
     * Handle keyboard input
     * @param {KeyboardEvent} event - Keyboard event
     */
    handleKeyPress(event) {
        // Start game on any key if in ready state
        if (this.state === GameState.READY) {
            this.startGame();
            return;
        }

        // Handle direction changes during gameplay
        if (this.state === GameState.PLAYING) {
            switch (event.key) {
                case 'ArrowUp':
                case 'w':
                case 'W':
                    if (this.direction !== Direction.DOWN) {
                        this.nextDirection = Direction.UP;
                    }
                    break;
                case 'ArrowDown':
                case 's':
                case 'S':
                    if (this.direction !== Direction.UP) {
                        this.nextDirection = Direction.DOWN;
                    }
                    break;
                case 'ArrowLeft':
                case 'a':
                case 'A':
                    if (this.direction !== Direction.RIGHT) {
                        this.nextDirection = Direction.LEFT;
                    }
                    break;
                case 'ArrowRight':
                case 'd':
                case 'D':
                    if (this.direction !== Direction.LEFT) {
                        this.nextDirection = Direction.RIGHT;
                    }
                    break;
                case ' ':
                    this.togglePause();
                    break;
            }
        }

        // Restart game on any key if game over
        if (this.state === GameState.GAME_OVER) {
            this.resetGame();
            this.startGame();
        }

        // Resume on space if paused
        if (this.state === GameState.PAUSED && event.key === ' ') {
            this.togglePause();
        }
    }

    /**
     * Start the game
     */
    startGame() {
        this.state = GameState.PLAYING;
        this.updateStatus('');
        this.lastUpdateTime = Date.now();
        this.gameLoop = requestAnimationFrame(() => this.update());
    }

    /**
     * Main game update loop
     */
    update() {
        if (this.state !== GameState.PLAYING) {
            return;
        }

        const currentTime = Date.now();
        const deltaTime = currentTime - this.lastUpdateTime;

        if (deltaTime >= this.speed) {
            this.lastUpdateTime = currentTime;

            // Update direction
            this.direction = this.nextDirection;

            // Move snake
            this.moveSnake();

            // Check collisions
            if (this.checkCollisions()) {
                this.gameOver();
                return;
            }

            // Check food consumption
            this.checkFood();

            // Render game
            this.render();
        }

        this.gameLoop = requestAnimationFrame(() => this.update());
    }

    /**
     * Move the snake in the current direction
     */
    moveSnake() {
        const head = this.snake[0];
        const newHead = {
            x: head.x + this.direction.x,
            y: head.y + this.direction.y
        };

        this.snake.unshift(newHead);

        // Remove tail unless food was eaten (handled in checkFood)
        if (!this.foodEaten) {
            this.snake.pop();
        }
        this.foodEaten = false;
    }

    /**
     * Check for collisions with walls or self
     * @returns {boolean} True if collision occurred
     */
    checkCollisions() {
        const head = this.snake[0];
        const gridWidth = CONFIG.BOARD_WIDTH / CONFIG.GRID_SIZE;
        const gridHeight = CONFIG.BOARD_HEIGHT / CONFIG.GRID_SIZE;

        // Wall collision
        if (head.x < 0 || head.x >= gridWidth || head.y < 0 || head.y >= gridHeight) {
            return true;
        }

        // Self collision (check if head collides with body)
        for (let i = 1; i < this.snake.length; i++) {
            if (head.x === this.snake[i].x && head.y === this.snake[i].y) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if snake head is on food
     */
    checkFood() {
        const head = this.snake[0];

        if (head.x === this.food.x && head.y === this.food.y) {
            // Store position for visual feedback before spawning new food
            this.lastFoodConsumedPosition = { x: this.food.x, y: this.food.y };
            this.visualFeedbackActive = true;
            this.feedbackStartTime = Date.now();

            this.foodEaten = true;
            this.score += 10;
            this.updateScoreDisplay();
            this.spawnFood();

            // Increase speed
            this.increaseSpeed();
        }
    }

    /**
     * Increase game speed
     */
    increaseSpeed() {
        if (this.speed > CONFIG.MIN_SPEED) {
            this.speed = Math.max(CONFIG.MIN_SPEED, this.speed - CONFIG.SPEED_INCREMENT);
        }
    }

    /**
     * Handle game over
     */
    gameOver() {
        this.state = GameState.GAME_OVER;
        cancelAnimationFrame(this.gameLoop);
        this.saveHighScore();
        this.updateStatus(`Game Over! Score: ${this.score} - Press any key to restart`);
        this.render();
    }

    /**
     * Toggle pause state
     */
    togglePause() {
        if (this.state === GameState.PLAYING) {
            this.state = GameState.PAUSED;
            cancelAnimationFrame(this.gameLoop);
            this.updateStatus('Paused - Press Space to continue');
        } else if (this.state === GameState.PAUSED) {
            this.state = GameState.PLAYING;
            this.updateStatus('');
            this.lastUpdateTime = Date.now();
            this.gameLoop = requestAnimationFrame(() => this.update());
        }
    }

    /**
     * Reset game to initial state
     */
    resetGame() {
        this.score = 0;
        this.speed = CONFIG.INITIAL_SPEED;
        this.direction = Direction.RIGHT;
        this.nextDirection = Direction.RIGHT;
        this.state = GameState.READY;
        this.foodEaten = false;
        this.visualFeedbackActive = false;
        this.feedbackStartTime = 0;
        this.lastFoodConsumedPosition = null;
        this.initSnake();
        this.spawnFood();
        this.updateScoreDisplay();
    }

    /**
     * Render the game board
     */
    render() {
        // Clear canvas
        this.ctx.fillStyle = CONFIG.BACKGROUND_COLOR;
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        // Draw boundary
        this.drawBoundary();

        // Draw snake
        this.drawSnake();

        // Draw food
        this.drawFood();

        // Draw visual feedback effect if active
        this.updateVisualFeedback();
        if (this.visualFeedbackActive) {
            this.drawFeedbackEffect();
        }
    }

    /**
     * Draw the game board boundary
     */
    drawBoundary() {
        this.ctx.strokeStyle = CONFIG.BORDER_COLOR;
        this.ctx.lineWidth = CONFIG.BORDER_WIDTH;
        this.ctx.strokeRect(
            CONFIG.BORDER_WIDTH / 2,
            CONFIG.BORDER_WIDTH / 2,
            this.canvas.width - CONFIG.BORDER_WIDTH,
            this.canvas.height - CONFIG.BORDER_WIDTH
        );
    }

    /**
     * Draw the snake
     */
    drawSnake() {
        this.ctx.fillStyle = CONFIG.SNAKE_COLOR;

        this.snake.forEach((segment, index) => {
            const x = segment.x * CONFIG.GRID_SIZE;
            const y = segment.y * CONFIG.GRID_SIZE;

            // Head is slightly larger/different
            if (index === 0) {
                this.ctx.fillRect(x + 1, y + 1, CONFIG.GRID_SIZE - 2, CONFIG.GRID_SIZE - 2);
            } else {
                this.ctx.fillRect(x + 2, y + 2, CONFIG.GRID_SIZE - 4, CONFIG.GRID_SIZE - 4);
            }
        });
    }

    /**
     * Draw the food
     */
    drawFood() {
        this.ctx.fillStyle = CONFIG.FOOD_COLOR;
        const x = this.food.x * CONFIG.GRID_SIZE;
        const y = this.food.y * CONFIG.GRID_SIZE;

        // Draw food as a circle
        this.ctx.beginPath();
        this.ctx.arc(
            x + CONFIG.GRID_SIZE / 2,
            y + CONFIG.GRID_SIZE / 2,
            CONFIG.GRID_SIZE / 2 - 2,
            0,
            Math.PI * 2
        );
        this.ctx.fill();
    }

    /**
     * Update visual feedback state based on elapsed time
     */
    updateVisualFeedback() {
        if (this.visualFeedbackActive) {
            const elapsed = Date.now() - this.feedbackStartTime;
            if (elapsed >= CONFIG.FEEDBACK_DURATION) {
                this.visualFeedbackActive = false;
            }
        }
    }

    /**
     * Draw visual feedback effect at the consumed food position
     */
    drawFeedbackEffect() {
        if (!this.lastFoodConsumedPosition) {
            return;
        }

        const elapsed = Date.now() - this.feedbackStartTime;
        const progress = Math.min(elapsed / CONFIG.FEEDBACK_DURATION, 1);

        const x = this.lastFoodConsumedPosition.x * CONFIG.GRID_SIZE;
        const y = this.lastFoodConsumedPosition.y * CONFIG.GRID_SIZE;
        const centerX = x + CONFIG.GRID_SIZE / 2;
        const centerY = y + CONFIG.GRID_SIZE / 2;

        // Expanding ring effect with fade out
        const maxRadius = CONFIG.GRID_SIZE * 1.5;
        const radius = CONFIG.GRID_SIZE / 2 + (maxRadius - CONFIG.GRID_SIZE / 2) * progress;
        const alpha = 1 - progress;

        // Use save/restore if available, otherwise just set properties
        if (typeof this.ctx.save === 'function') {
            this.ctx.save();
        }
        const originalAlpha = this.ctx.globalAlpha;
        this.ctx.globalAlpha = alpha;
        this.ctx.strokeStyle = CONFIG.FEEDBACK_COLOR;
        this.ctx.lineWidth = 3;
        this.ctx.beginPath();
        this.ctx.arc(centerX, centerY, radius, 0, Math.PI * 2);
        this.ctx.stroke();
        if (typeof this.ctx.restore === 'function') {
            this.ctx.restore();
        } else {
            this.ctx.globalAlpha = originalAlpha;
        }
    }

    /**
     * Update score display
     */
    updateScoreDisplay() {
        const scoreElement = document.getElementById('score');
        if (scoreElement) {
            scoreElement.textContent = this.score;
        }
    }

    /**
     * Update high score display
     */
    updateHighScoreDisplay() {
        const highScoreElement = document.getElementById('high-score');
        if (highScoreElement) {
            highScoreElement.textContent = this.highScore;
        }
    }

    /**
     * Update game status message
     * @param {string} message - Status message to display
     */
    updateStatus(message) {
        const statusElement = document.getElementById('game-status');
        if (statusElement) {
            statusElement.textContent = message;
        }
    }

    /**
     * Check if game is initialized
     * @returns {boolean} True if game is initialized
     */
    isInitialized() {
        return this.initialized;
    }

    /**
     * Get the canvas element
     * @returns {HTMLCanvasElement|null} The canvas element
     */
    getCanvas() {
        return this.canvas;
    }

    /**
     * Get the current game state
     * @returns {string} Current game state
     */
    getState() {
        return this.state;
    }

    /**
     * Get game configuration
     * @returns {Object} Game configuration
     */
    getConfig() {
        return CONFIG;
    }
}

// Export for testing (Node.js environment)
if (typeof module !== 'undefined' && module.exports) {
    module.exports = { SnakeGame, CONFIG, GameState, Direction };
}

// Initialize game when DOM is loaded
let game;
document.addEventListener('DOMContentLoaded', () => {
    game = new SnakeGame();
    game.init();
});
