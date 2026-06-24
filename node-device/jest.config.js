export default {
    testEnvironment: 'node',
    transform: {},
    clearMocks: true,
    restoreMocks: true,
    collectCoverageFrom: [
        'src/service/**/*.js',
        'src/mqtt/**/*.js',
        'src/websocket/**/*.js'
    ],
    coverageDirectory: 'coverage'
};