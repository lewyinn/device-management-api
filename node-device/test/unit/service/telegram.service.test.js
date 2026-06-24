import {
    afterEach,
    beforeEach,
    describe,
    expect,
    jest,
    test
} from '@jest/globals';
import {
    sendDeviceRegisteredTelegramNotification,
    sendTelegramMessage
} from '../../../src/service/telegram.service.js';

describe('Telegram Service', () => {
    const originalBotToken = process.env.TELEGRAM_BOT_TOKEN;
    const originalChatId = process.env.TELEGRAM_CHAT_ID;

    beforeEach(() => {
        process.env.TELEGRAM_BOT_TOKEN = 'test-bot-token';
        process.env.TELEGRAM_CHAT_ID = 'test-chat-id';

        global.fetch = jest.fn();
    });

    afterEach(() => {
        if (originalBotToken === undefined) {
            delete process.env.TELEGRAM_BOT_TOKEN;
        } else {
            process.env.TELEGRAM_BOT_TOKEN = originalBotToken;
        }

        if (originalChatId === undefined) {
            delete process.env.TELEGRAM_CHAT_ID;
        } else {
            process.env.TELEGRAM_CHAT_ID = originalChatId;
        }

        delete global.fetch;
        jest.restoreAllMocks();
    });

    test('return false when Telegram configuration is missing', async () => {
        delete process.env.TELEGRAM_BOT_TOKEN;
        delete process.env.TELEGRAM_CHAT_ID;

        const warningMock = jest 
            .spyOn(console, 'warn')
            .mockImplementation(() => {});

        const result = await sendTelegramMessage('Test message');

        expect(result).toBe(false);
        expect(global.fetch).not.toHaveBeenCalled();
        expect(warningMock).toHaveBeenCalledWith('Telegram notifications are not configured');
    });

    test('sends message to Telegram API', async () => {
        global.fetch.mockResolvedValue({
            ok: true
        });

        const result = await sendTelegramMessage('Sensor is active');

        expect(result).toBe(true);
        expect(global.fetch).toHaveBeenCalledTimes(1);
        expect(global.fetch).toHaveBeenCalledWith(
            'https://api.telegram.org/bottest-bot-token/sendMessage',
            {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({
                    chat_id: 'test-chat-id',
                    text: 'Sensor is active',
                    parse_mode: 'HTML',
                    disable_web_page_preview: true
                })
            }
        );
    });

    test('throws error when Telegram API returns an error', async () => {
        global.fetch.mockResolvedValue({
            ok: false,
            status: 401,
            text: jest.fn().mockResolvedValue('Unauthorized')
        });

        await expect(
            sendTelegramMessage('Test message')
        ).rejects.toThrow(
            'Telegram notification failed with status 401: Unauthorized'
        );
    });

    test('formats device registration message and escapes HTML', async () => {
        global.fetch.mockResolvedValue({
            ok: true
        });

        const device = {
            id: '550e8400-e29b-41d4-a716-446655440000',
            name: 'Sensor <Suhu>',
            type: 'DHT22 & MQTT',
            status: 'active'
        };

        const result = await sendDeviceRegisteredTelegramNotification(device);

        expect(result).toBe(true);
        expect(global.fetch).toHaveBeenCalledTimes(1);

        const [, requestOptions] = global.fetch.mock.calls[0];
        const requestBody = JSON.parse(requestOptions.body);

        expect(requestBody.text).toContain('<b>Device registered</b>');
        expect(requestBody.text).toContain('Sensor &lt;Suhu&gt;');
        expect(requestBody.text).toContain('DHT22 &amp; MQTT');
        expect(requestBody.text).toContain(device.id);
        expect(requestBody.text).toContain('active');
    });
});
