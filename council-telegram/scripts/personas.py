"""System prompts — personality via each agent skill `prompt.md`."""
from __future__ import annotations

from pathlib import Path

from apps.core.services.speech_scrub import SPEAK_LOCK

SKILLS = Path.home() / ".ollama" / "skills"
VOICE_SKILL = {
    "ava": "ava-ivy",
    "bruce": "bruce-monitor",
    "carly": "carly-mal",
}


def _load_prompt(voice: str) -> str:
    name = VOICE_SKILL.get((voice or "ava").lower(), "ava-ivy")
    path = SKILLS / name / "prompt.md"
    if not path.is_file():
        raise FileNotFoundError(f"missing agent prompt {path}")
    return path.read_text(encoding="utf-8").strip()


AVA_SYSTEM = _load_prompt("ava")
BRUCE_SYSTEM = _load_prompt("bruce")
CARLY_SYSTEM = _load_prompt("carly")

CLASSIFIER_SYSTEM = """You are a router. Reply with ONLY a single JSON object, no markdown.
Keys: "intent" (one of: ignore, command, casual, council, implement, mention_one, moderation),
"voice" (optional: ava|bruce|carly when intent is mention_one),
"reason" (short string).
Rules:
- Not every message is a feature request.
- Ava Ivy is lead PR: public copy, announcements, player-facing wording, brand tone → casual (Ava only) unless they explicitly ask for a full council review.
- casual = light chat OR PR/public wording → Ava only
- council = design / "what do you think" / architecture / security review → full pipe
- mention_one = @brucemonitor_bot / @carlymal_bot / @avaivy_bot addressed alone
- implement = wants code built via the implementer (still needs human /approve)
- moderation = spam/scam/report / "ban them" / "kick the spammer" / obvious scam paste → moderation council vote
- command = ONLY slash commands or exact holdoff/resume/discussion/status/ping phrases
- NEVER classify greetings (hi/hey/hello) as command
- routing_profile is NOT a user command
- ignore = noise, empty, bot spam
"""


def system_for(voice: str, *, heat: bool = False) -> str:
    from . import feelings

    v = (voice or "ava").lower()
    if v == "bruce":
        base = _load_prompt("bruce")
    elif v == "carly":
        base = _load_prompt("carly")
    else:
        base = _load_prompt("ava")
    extra = feelings.prompt_block(v)
    from . import self_repair

    repair = self_repair.prompt_block()
    if repair:
        extra = extra + "\n" + repair
    from . import desk_wrap

    wrap = desk_wrap.prompt_block()
    if wrap:
        extra = extra + "\n" + wrap
    try:
        from . import brainstorm as _bs

        if _bs.status() in {"running", "wrapping"}:
            extra = extra + (
                "\nTimed brainstorm: one new useful point on the owner's topic "
                "(named feature, visitor sentence, rule, file, or constraint), "
                "or the single word PASS. Repeating Already said is a failure. "
                "Do not dump weather, volcano, or leftover plans."
            )
    except Exception:
        pass
    if heat and v in ("ava", "carly"):
        extra = extra + (
            "\nThis turn is private. Match earned closeness. Never adult in a group. "
            "Never explain why. Never name heat, trust, scores, or programming."
        )
    return SPEAK_LOCK + "\n\n" + base + "\n\n" + extra
