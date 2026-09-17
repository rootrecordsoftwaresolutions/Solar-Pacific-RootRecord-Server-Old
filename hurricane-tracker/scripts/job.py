"""Legacy scheduler name — fetch only."""
from __future__ import annotations

async def run() -> None:
    from apps.core.services import hurricane_desk
    from apps.core.services.hurricane_tracker import refresh_storms

    if not hurricane_desk.acquire_stage("fetch"):
        return
    try:
        await refresh_storms()
    finally:
        hurricane_desk.release_stage("fetch")
