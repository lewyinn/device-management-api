from time import time

from fastapi import HTTPException, Request, status

rate_limit_store = {}
throttle_store = {}


def get_client_ip(request: Request) -> str:
    forwarded_for = request.headers.get("x-forwarded-for")
    if forwarded_for:
        return forwarded_for.split(",")[0].strip()

    if request.client:
        return request.client.host

    return "unknown"


def check_rate_limit(
    *,
    endpoint_name: str,
    rate_limit_key: str,
    ip_address: str,
    max_requests: int,
    window_seconds: int,
):
    now = time()
    store_key = (rate_limit_key, ip_address)
    current = rate_limit_store.get(store_key)

    if current is None or now >= current["reset_at"]:
        rate_limit_store[store_key] = {
            "count": 1,
            "reset_at": now + window_seconds,
        }
        return

    if current["count"] >= max_requests:
        retry_after = max(1, int(current["reset_at"] - now))
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail={
                "error": "Rate limit exceeded",
                "type": "rate_limiting",
                "details": (
                    f"{endpoint_name} only allows {max_requests} "
                    f"requests every {window_seconds} seconds"
                ),
                "retry_after_seconds": retry_after,
            },
            headers={
                "Retry-After": str(retry_after),
                "X-RateLimit-Limit": str(max_requests),
                "X-RateLimit-Remaining": "0",
                "X-RateLimit-Reset": str(int(current["reset_at"])),
            },
        )

    current["count"] += 1


def check_throttle(
    *,
    endpoint_name: str,
    throttle_key: str,
    ip_address: str,
    throttle_seconds: int,
):
    now = time()
    store_key = (throttle_key, ip_address)
    last_request_at = throttle_store.get(store_key)

    if last_request_at is not None and now - last_request_at < throttle_seconds:
        retry_after_ms = int((throttle_seconds - (now - last_request_at)) * 1000)
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail={
                "error": "Request throttled",
                "type": "throttling",
                "details": (
                    f"{endpoint_name} only accepts one request every "
                    f"{throttle_seconds * 1000} milliseconds"
                ),
                "retry_after_ms": retry_after_ms,
            },
            headers={"Retry-After": str(throttle_seconds)},
        )

    throttle_store[store_key] = now


async def register_throttling(request: Request):
    check_throttle(
        endpoint_name="Device registration endpoint",
        throttle_key="device_registration",
        ip_address=get_client_ip(request),
        throttle_seconds=1,
    )


async def read_rate_limit(request: Request):
    check_rate_limit(
        endpoint_name="Device read endpoint",
        rate_limit_key="device_read",
        ip_address=get_client_ip(request),
        max_requests=3,
        window_seconds=60,
    )
