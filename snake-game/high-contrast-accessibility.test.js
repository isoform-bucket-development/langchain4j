/**
 * High Contrast Visual Accessibility Tests
 *
 * Scenario: Verify that the game uses high contrast colors for accessibility
 * - Snake has sufficient contrast against background
 * - Food is easily distinguishable
 * - Game boundaries are clearly visible
 */

const { SnakeGame, CONFIG, GameState, Direction } = require('./game.js');

// Helper function to parse hex color to RGB
function hexToRgb(hex) {
    const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
    return result ? {
        r: parseInt(result[1], 16),
        g: parseInt(result[2], 16),
        b: parseInt(result[3], 16)
    } : null;
}

// Calculate relative luminance according to WCAG 2.1
function getRelativeLuminance(rgb) {
    const sRGB = [rgb.r / 255, rgb.g / 255, rgb.b / 255];
    const transformed = sRGB.map(val => {
        return val <= 0.03928
            ? val / 12.92
            : Math.pow((val + 0.055) / 1.055, 2.4);
    });
    return 0.2126 * transformed[0] + 0.7152 * transformed[1] + 0.0722 * transformed[2];
}

// Calculate contrast ratio between two colors (WCAG 2.1)
function getContrastRatio(color1, color2) {
    const lum1 = getRelativeLuminance(hexToRgb(color1));
    const lum2 = getRelativeLuminance(hexToRgb(color2));
    const lighter = Math.max(lum1, lum2);
    const darker = Math.min(lum1, lum2);
    return (lighter + 0.05) / (darker + 0.05);
}

// WCAG AA requires minimum contrast ratio of 3:1 for large text/graphics
// WCAG AAA requires 4.5:1 for normal text, 3:1 for large text
const MIN_CONTRAST_RATIO_GRAPHICS = 3.0;

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

describe('High Contrast Visual Accessibility', () => {
    let game;
    let mockCtx;

    beforeEach(() => {
        // Create mock context
        mockCtx = createMockContext();

        // Set up DOM environment with mocked canvas
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

        // Mock canvas getContext to return our mock context
        const canvas = document.getElementById('game-board');
        canvas.getContext = jest.fn(() => mockCtx);

        // Mock localStorage
        const localStorageMock = {
            getItem: jest.fn(),
            setItem: jest.fn(),
            clear: jest.fn()
        };
        Object.defineProperty(window, 'localStorage', {
            value: localStorageMock,
            writable: true
        });

        // Create new game instance
        game = new SnakeGame();
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    describe('Test Case 1: Snake Color Contrast Ratio', () => {
        it('should have snake color defined as a valid hex color', () => {
            expect(CONFIG.SNAKE_COLOR).toBeDefined();
            expect(typeof CONFIG.SNAKE_COLOR).toBe('string');
            expect(CONFIG.SNAKE_COLOR).toMatch(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/);
        });

        it('should have background color defined as a valid hex color', () => {
            expect(CONFIG.BACKGROUND_COLOR).toBeDefined();
            expect(typeof CONFIG.BACKGROUND_COLOR).toBe('string');
            expect(CONFIG.BACKGROUND_COLOR).toMatch(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/);
        });

        it('should have snake color that contrasts sufficiently with background', () => {
            const snakeColor = CONFIG.SNAKE_COLOR;
            const backgroundColor = CONFIG.BACKGROUND_COLOR;

            const contrastRatio = getContrastRatio(snakeColor, backgroundColor);

            // WCAG 2.1 requires minimum 3:1 contrast ratio for large graphics
            expect(contrastRatio).toBeGreaterThanOrEqual(MIN_CONTRAST_RATIO_GRAPHICS);
        });

        it('should have snake color visually distinct from background (different RGB values)', () => {
            const snakeRgb = hexToRgb(CONFIG.SNAKE_COLOR);
            const bgRgb = hexToRgb(CONFIG.BACKGROUND_COLOR);

            // Colors should not be the same
            expect(snakeRgb).not.toEqual(bgRgb);

            // At least one RGB channel should differ significantly (by at least 50)
            const rDiff = Math.abs(snakeRgb.r - bgRgb.r);
            const gDiff = Math.abs(snakeRgb.g - bgRgb.g);
            const bDiff = Math.abs(snakeRgb.b - bgRgb.b);
            const maxDiff = Math.max(rDiff, gDiff, bDiff);

            expect(maxDiff).toBeGreaterThanOrEqual(50);
        });
    });

    describe('Test Case 2: Food Color Contrast Ratio', () => {
        it('should have food color defined as a valid hex color', () => {
            expect(CONFIG.FOOD_COLOR).toBeDefined();
            expect(typeof CONFIG.FOOD_COLOR).toBe('string');
            expect(CONFIG.FOOD_COLOR).toMatch(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/);
        });

        it('should have food color that contrasts sufficiently with background', () => {
            const foodColor = CONFIG.FOOD_COLOR;
            const backgroundColor = CONFIG.BACKGROUND_COLOR;

            const contrastRatio = getContrastRatio(foodColor, backgroundColor);

            // WCAG 2.1 requires minimum 3:1 contrast ratio for graphics
            expect(contrastRatio).toBeGreaterThanOrEqual(MIN_CONTRAST_RATIO_GRAPHICS);
        });

        it('should have food color distinct from snake color (easily distinguishable)', () => {
            const foodColor = CONFIG.FOOD_COLOR;
            const snakeColor = CONFIG.SNAKE_COLOR;

            const foodRgb = hexToRgb(foodColor);
            const snakeRgb = hexToRgb(snakeColor);

            // Food and snake should not be the same color
            expect(foodRgb).not.toEqual(snakeRgb);

            // Calculate color distance (Euclidean distance in RGB space)
            const colorDistance = Math.sqrt(
                Math.pow(foodRgb.r - snakeRgb.r, 2) +
                Math.pow(foodRgb.g - snakeRgb.g, 2) +
                Math.pow(foodRgb.b - snakeRgb.b, 2)
            );

            // Colors should be visually distinct (minimum distance of 100 in RGB space)
            expect(colorDistance).toBeGreaterThanOrEqual(100);
        });

        it('should have food color visually distinct from background (different RGB values)', () => {
            const foodRgb = hexToRgb(CONFIG.FOOD_COLOR);
            const bgRgb = hexToRgb(CONFIG.BACKGROUND_COLOR);

            // Colors should not be the same
            expect(foodRgb).not.toEqual(bgRgb);

            // At least one RGB channel should differ significantly
            const rDiff = Math.abs(foodRgb.r - bgRgb.r);
            const gDiff = Math.abs(foodRgb.g - bgRgb.g);
            const bDiff = Math.abs(foodRgb.b - bgRgb.b);
            const maxDiff = Math.max(rDiff, gDiff, bDiff);

            expect(maxDiff).toBeGreaterThanOrEqual(50);
        });
    });

    describe('Test Case 3: Boundary Visibility', () => {
        it('should have border color defined as a valid hex color', () => {
            expect(CONFIG.BORDER_COLOR).toBeDefined();
            expect(typeof CONFIG.BORDER_COLOR).toBe('string');
            expect(CONFIG.BORDER_COLOR).toMatch(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/);
        });

        it('should have border width defined for clear boundary visibility', () => {
            expect(CONFIG.BORDER_WIDTH).toBeDefined();
            expect(typeof CONFIG.BORDER_WIDTH).toBe('number');
            // Border should be at least 1 pixel wide to be visible
            expect(CONFIG.BORDER_WIDTH).toBeGreaterThanOrEqual(1);
        });

        it('should have boundary color that contrasts sufficiently with background', () => {
            const borderColor = CONFIG.BORDER_COLOR;
            const backgroundColor = CONFIG.BACKGROUND_COLOR;

            const contrastRatio = getContrastRatio(borderColor, backgroundColor);

            // WCAG 2.1 requires minimum 3:1 contrast ratio for graphics
            expect(contrastRatio).toBeGreaterThanOrEqual(MIN_CONTRAST_RATIO_GRAPHICS);
        });

        it('should have boundary visually distinguishable (sufficient border width)', () => {
            // For clear visibility, border should be at least 2 pixels
            expect(CONFIG.BORDER_WIDTH).toBeGreaterThanOrEqual(2);
        });

        it('should have boundary color visible against background (different RGB values)', () => {
            const borderRgb = hexToRgb(CONFIG.BORDER_COLOR);
            const bgRgb = hexToRgb(CONFIG.BACKGROUND_COLOR);

            // Colors should not be the same
            expect(borderRgb).not.toEqual(bgRgb);

            // At least one RGB channel should differ significantly
            const rDiff = Math.abs(borderRgb.r - bgRgb.r);
            const gDiff = Math.abs(borderRgb.g - bgRgb.g);
            const bDiff = Math.abs(borderRgb.b - bgRgb.b);
            const maxDiff = Math.max(rDiff, gDiff, bDiff);

            expect(maxDiff).toBeGreaterThanOrEqual(50);
        });
    });

    describe('Overall Accessibility Compliance', () => {
        it('should have all game elements with sufficient contrast (WCAG 2.1 Level AA)', () => {
            const backgroundColor = CONFIG.BACKGROUND_COLOR;

            // All visible elements should meet WCAG AA standards for graphics (3:1)
            const snakeContrast = getContrastRatio(CONFIG.SNAKE_COLOR, backgroundColor);
            const foodContrast = getContrastRatio(CONFIG.FOOD_COLOR, backgroundColor);
            const borderContrast = getContrastRatio(CONFIG.BORDER_COLOR, backgroundColor);

            expect(snakeContrast).toBeGreaterThanOrEqual(MIN_CONTRAST_RATIO_GRAPHICS);
            expect(foodContrast).toBeGreaterThanOrEqual(MIN_CONTRAST_RATIO_GRAPHICS);
            expect(borderContrast).toBeGreaterThanOrEqual(MIN_CONTRAST_RATIO_GRAPHICS);
        });

        it('should have color palette that supports accessibility needs', () => {
            // All colors should be valid hex colors
            const colors = [
                CONFIG.SNAKE_COLOR,
                CONFIG.FOOD_COLOR,
                CONFIG.BACKGROUND_COLOR,
                CONFIG.BORDER_COLOR
            ];

            colors.forEach(color => {
                expect(color).toMatch(/^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/);
            });

            // Verify visual hierarchy exists (different luminance levels)
            const snakeLum = getRelativeLuminance(hexToRgb(CONFIG.SNAKE_COLOR));
            const foodLum = getRelativeLuminance(hexToRgb(CONFIG.FOOD_COLOR));
            const bgLum = getRelativeLuminance(hexToRgb(CONFIG.BACKGROUND_COLOR));

            // Background should be significantly darker or lighter than game elements
            expect(Math.abs(snakeLum - bgLum)).toBeGreaterThan(0.1);
            expect(Math.abs(foodLum - bgLum)).toBeGreaterThan(0.1);
        });
    });
});
