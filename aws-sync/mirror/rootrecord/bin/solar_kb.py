"""Compat shim — prefer automation_kb (etc/automation-kb.json)."""
from automation_kb import *  # noqa: F403
from automation_kb import update_solar_cam as update_from_poll  # noqa: E402
