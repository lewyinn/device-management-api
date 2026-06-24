import html
import os

import httpx
from dotenv import load_dotenv

TELEGRAM_API_BASE_URL = "https://api.telegram.org"

load_dotenv()


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


async def send_device_registered_notification(device: dict):
    bot_token = os.getenv("TELEGRAM_BOT_TOKEN")
    chat_id = os.getenv("TELEGRAM_CHAT_ID")

    if not bot_token or not chat_id:
        print("Telegram notifications are not configured")
        return

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            response = await client.post(
                (
                    f"{TELEGRAM_API_BASE_URL}/bot"
                    f"{bot_token}/sendMessage"
                ),
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
