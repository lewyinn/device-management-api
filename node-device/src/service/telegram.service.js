const TELEGRAM_API_BASE_URL = 'https://api.telegram.org';

const isTelegramConfigured = () => (
    Boolean(process.env.TELEGRAM_BOT_TOKEN) &&
    Boolean(process.env.TELEGRAM_CHAT_ID)
);

const escapeHtml = (value) => String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');

const formatDeviceRegisteredMessage = (device) => [
    '<b>Device registered</b>',
    '',
    `<b>ID:</b> <code>${escapeHtml(device.id)}</code>`,
    `<b>Name:</b> ${escapeHtml(device.name)}`,
    `<b>Type:</b> ${escapeHtml(device.type)}`,
    `<b>Status:</b> ${escapeHtml(device.status)}`
].join('\n');

export const sendTelegramMessage = async (message) => {
    if (!isTelegramConfigured()) {
        console.warn('Telegram notifications are not configured');
        return false;
    }

    const url = `${TELEGRAM_API_BASE_URL}/bot${process.env.TELEGRAM_BOT_TOKEN}/sendMessage`;

    const response = await fetch(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            chat_id: process.env.TELEGRAM_CHAT_ID,
            text: message,
            parse_mode: 'HTML',
            disable_web_page_preview: true
        })
    });

    if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(`Telegram notification failed with status ${response.status}: ${errorBody}`);
    }

    return true;
};

export const sendDeviceRegisteredTelegramNotification = async (device) => {
    await sendTelegramMessage(formatDeviceRegisteredMessage(device));
};
