import html
import os

import httpx


TELEGRAM_API_BASE_URL = "https://api.telegram.org"


def is_telegram_configured() -> bool:
    return bool(os.getenv("TELEGRAM_BOT_TOKEN")) and bool(os.getenv("TELEGRAM_CHAT_ID"))


def format_device_registered_message(device: dict) -> str:
    return "\n".join(
        [
            "<b>Device registered</b>",
            "",
            f"<b>ID:</b> <code>{html.escape(str(device['id']))}</code>",
            f"<b>Name:</b> {html.escape(str(device['name']))}",
            f"<b>Type:</b> {html.escape(str(device['type']))}",
            f"<b>Status:</b> {html.escape(str(device['status']))}",
        ]
    )


async def send_device_registered_telegram_notification(device: dict):
    if not is_telegram_configured():
        print("Telegram notifications are not configured")
        return

    bot_token = os.getenv("TELEGRAM_BOT_TOKEN")
    chat_id = os.getenv("TELEGRAM_CHAT_ID")
    url = f"{TELEGRAM_API_BASE_URL}/bot{bot_token}/sendMessage"

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.post(
                url,
                json={
                    "chat_id": chat_id,
                    "text": format_device_registered_message(device),
                    "parse_mode": "HTML",
                    "disable_web_page_preview": True,
                },
            )
            response.raise_for_status()
    except httpx.HTTPError as error:
        print(f"Telegram device registration notification failed: {error}")
