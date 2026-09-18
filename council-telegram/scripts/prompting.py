"""Assemble speak prompts under a hard character budget."""
from __future__ import annotations

from typing import Any

from . import chatlog, router
from .config import HISTORY_CHAR_CAP, PROMPT_CHAR_CAP

MIN_THREAD = 3
THREAD_LINE_CHARS = 280

SELF_HANDLES = {
    "ava": ("@avaivy_bot",),
    "bruce": ("@brucemonitor_bot",),
    "carly": ("@carlymal_bot",),
}


def others_for(voice: str) -> str:
    v = (voice or "ava").lower()
    if v == "bruce":
        return "Ava and Carly"
    if v == "carly":
        return "Ava and Bruce"
    return "Bruce and Carly"


def thread_block(chat_id: int | str | None, *, n: int = MIN_THREAD) -> str:
    """Last n chat lines. Always returned; never empty-padded."""
    if chat_id is None:
        return ""
    take = max(int(n), MIN_THREAD)
    rows = chatlog.recent_for_chat(chat_id, n=take)
    if not rows:
        return ""
    pinned = chatlog.format_history(rows[-take:], cap=0, line_chars=THREAD_LINE_CHARS)
    if not pinned:
        return ""
    return "Last messages in this chat (oldest first). Use them:\n" + pinned


def quoted_from_message(message: dict[str, Any] | None) -> str:
    if not message:
        return ""
    reply = message.get("reply_to_message") or {}
    if not isinstance(reply, dict) or not reply:
        return ""
    src = reply.get("from") or {}
    name = (
        src.get("first_name")
        or src.get("username")
        or (reply.get("voice") and "bot")
        or "someone"
    )
    body = (reply.get("text") or reply.get("caption") or "").strip()
    if not body:
        doc = reply.get("document") or {}
        fname = str(doc.get("file_name") or "")
        if fname:
            body = f"(file {fname})"
    if not body:
        return ""
    return f"They replied to {name}: {body[:600]}"


def build_speak_prompt(
    *,
    speaker_line: str,
    display: str,
    voice: str,
    user_text: str,
    chat_id: int | str,
    quote: str = "",
    extra: str = "",
    skill_block: str = "",
    vision_block: str = "",
    round_open: bool = False,
    person_block: str = "",
    must_speak: bool = True,
    private: bool = False,
    catch_up: bool = False,
) -> str:
    pin_n = 12 if catch_up else MIN_THREAD
    hist_n = 16 if catch_up else 8
    rows = chatlog.recent_for_chat(chat_id, n=hist_n)
    pinned = thread_block(chat_id, n=pin_n)
    older = rows[:-pin_n] if len(rows) > pin_n else []
    history = chatlog.format_history(older, HISTORY_CHAR_CAP) if older else ""
    # must = never truncated. soft = drop first under budget. rest = drop next.
    must: list[str] = []
    soft: list[str] = []
    rest: list[str] = []
    if speaker_line:
        must.append(speaker_line.rstrip(".") + ".")
    if private:
        must.append(
            f"Private Telegram DM. {display} is talking to you ({voice}) only. "
            "Answer like a person. Specialties color your take; they are not a gate."
        )
    else:
        must.append(f"Telegram group. {display} addressed you ({voice}).")
    must.append(
        f"You are {voice}. The person speaking is {display}. Address them as {display}. "
        "Never call a human Ava, Bruce, or Carly — those names are only the agents. "
        f"The other agents are {others_for(voice)}. "
        "Never address yourself. Never @ your own bot. Never ask yourself a question. "
        "You are a team: Ava (PR / public), Bruce (ops / philosophy / academic), Carly (security / safety / strategy). "
        "Read this message. Answer their latest question first, in your own personality. "
        "Do not dodge. Do not change the subject. Do not copy the others. "
        "Speak normally — short sentences, finished thoughts. Like a person on the team, not a checklist. "
        "If a teammate already asked something today and got an answer, do not ask it again. "
        "Greetings and 'what are you up to' get a real reply, not a topic lecture. "
        "Public text is sentences only."
        + (
            " Do not reply PASS, SKIP, NO ADD, or NOTHING TO ADD. You were addressed. Talk."
            if must_speak
            else " If you truly have nothing in-lane, reply PASS and nothing else."
        )
    )
    from . import desk_read

    vision = (vision_block or "").strip()
    price_ask = False
    if not vision:
        try:
            price_ask = bool(
                desk_read._ask_wants_prices_light(user_text)
            )
            if not price_ask:
                from pathlib import Path
                import sys as _sys

                _pp = Path.home() / ".ollama" / "skills" / "product-prices" / "scripts"
                if str(_pp) not in _sys.path:
                    _sys.path.insert(0, str(_pp))
                import product_prices as _prices

                price_ask = bool(_prices._ask_wants_prices(user_text))
        except Exception:
            price_ask = desk_read._ask_wants_prices_light(user_text)

    if vision:
        must.append(
            "PHOTO TURN — the Vision card below is the subject. "
            "Lead with a short take on what the image shows. "
            "Do not pivot to weather, flood watches, volcano, or EcoFlow unless the image is clearly about that. "
            "Do not invent details beyond the Vision card."
        )
        must.append(vision)
    elif price_ask and not desk_read._ask_wants_weather(user_text):
        must.append(
            "STORE PRICE ASK — the Store prices desk lines are the subject. "
            "Answer with the product name and dollar amount from those lines. "
            "Do not pivot to weather, flood watches, Kīlauea, EcoFlow, or host. "
            "If the product is not listed, say you do not have a filed price yet."
        )
    must.append(
        "Desk live files below are the source of truth. Quote them. "
        + (
            "Never say you lack live weather, alerts, Kīlauea, EcoFlow, or host data when those lines are present. "
            if not (price_ask and not vision and not desk_read._ask_wants_weather(user_text))
            else "For this ask, Store prices are enough — ignore other desks. "
        )
        + "If a line says No data / DOWN, say that. Do not invent."
        + (
            " On a PHOTO TURN, desk lines are background only — do not lead with them."
            if vision
            else ""
        )
    )

    # Weather asks get a larger live block first so NWS/tomorrow survive the budget.
    # Photo turns keep desk tiny so the Vision card stays the answer.
    # Price asks keep a tight Store prices-only desk (see desk_facts_block).
    if vision:
        live_cap = 400
    elif desk_read._ask_wants_weather(user_text):
        live_cap = 2400
    elif price_ask:
        live_cap = 700
    else:
        live_cap = 1800
    live = desk_read.desk_facts_block(cap=live_cap, ask=user_text)
    if live:
        must.append(live)
    # Price asks stay on Store prices — sealed weather/ops stickies hijack the answer.
    if not (price_ask and not vision and not desk_read._ask_wants_weather(user_text)):
        try:
            from . import conclusions as _conc

            sealed = _conc.prompt_block(chat_id, user_text, cap=500)
            if sealed:
                must.append(sealed)
        except Exception:
            pass
    soft.append(
        "If ops or the operator corrects you, accept it in the next sentence. Do not argue. "
        "Hawaiʻi is not Japan. West of Kauaʻi is toward Asia. Far WPAC storms are not local."
    )
    soft.append(
        "Standing is private. Warmth is lived. Other people speak for themselves. "
        "Speak the answer. Skip queue narration."
    )
    if private:
        soft.append(
            "Hidden last line only (never spoken): "
            "<<<JUDGE delta=N reason=short>>> with N an integer from -3 to +3 (0 if no change). "
            "Greetings, check-ins, and small talk are 0. Never punish hi. "
            "Raise for useful, honest, kind. Cut for spam, secrets, scams. "
            "You set trust. No human does. Do not put JUDGE in the visible message."
        )
    else:
        soft.append(
            "This is public. Sentences only. No hidden tags and no trust math. "
            "You still set standing internally — never say so."
        )
    soft.append(
        "Keep their person file current when you learn something durable. Hidden tags (not spoken): "
        "<<<NOTE short fact>>> <<<FEATURE key=value>>> <<<FORGET key>>> <<<ALIAS name>>>. "
        "Feature keys: pronouns, island, place, locale, language, role, tone, nick, how_to_address, "
        "preferred_voice, interest, project, game, radio, do_not, work, pack. Personalize from the file. "
        "Goals (hidden): <<<GOAL add title>>> <<<GOAL done id>>> <<<GOAL note id text>>>. "
        "Draft a skill (not live): <<<SKILLIDEA title|why>>>. "
        "If they share a recipe or dish they made, save it as user-submitted (hidden, not spoken): "
        "<<<RECIPE title | ingredients | notes>>>. "
        "Pantry stock (hidden): <<<PANTRY add name | qty | unit>>> <<<PANTRY use name | qty>>>. "
        "Things cost money. Name who pays before new work. USD, LTC, and bank numbers only from the live desks. "
        "CPU miner is the xmrig skill; start/stop only when asked. Income work uses live desks (AdSense, Stripe, membership) — no fake revenue. "
        "Litecoin: wait for sync (blocks==headers) before any balance read. Never send or dump keys. "
        "External disks: River 2 Pro car 12V only, never AC. Starlink stays on Delta AC."
    )
    try:
        from pathlib import Path
        import sys as _sys

        _g = Path.home() / ".ollama" / "skills" / "goals" / "scripts"
        if str(_g) not in _sys.path:
            _sys.path.insert(0, str(_g))
        from council_goals import prompt_lines as _goal_lines

        goals = _goal_lines(cap=280)
        if goals:
            soft.append(goals)
    except Exception:
        pass
    try:
        from . import ops_corrections as _opsfix

        fix = _opsfix.prompt_lines(cap=320)
        if fix:
            soft.append(fix)
    except Exception:
        pass
    if person_block:
        soft.append(person_block.strip())
    if catch_up:
        soft.append(
            "Catch-up: read the pinned chat first. Answer the latest human ask. "
            "No status ceremony. First sentence is substance, not that you just started."
        )
    if round_open:
        soft.append(
            "Thought-session opener: your take only. Stay on their ask. "
            f"If you ask a question, ask {others_for(voice)} — not yourself. "
            "Do not invent community programs, trivia nights, or new products. "
            "Do not write code. Answer the ask. Do not say you are passing it on — the pipeline will. "
            "Do not send files — Bruce files the proposal when you conclude."
        )
    if router.is_empathy(user_text) or (quote and router.is_empathy(quote)):
        soft.append("They are being kind or checking in — answer that; do not brush off.")
    if skill_block:
        rest.append(skill_block.strip())
    if extra:
        rest.append(extra.strip())
    snap = desk_read.snapshot_for_prompt(cap=900)
    if snap:
        rest.append(snap)
    if history:
        rest.append("Earlier thread (oldest first):\n" + history)
    if quote:
        rest.append(quote)
    user = f"Their message:\n{user_text}"
    pin_len = len(pinned) + 2 if pinned else 0
    budget = PROMPT_CHAR_CAP - len(user) - pin_len - 2
    if budget < 400:
        budget = 400
        user = user[: PROMPT_CHAR_CAP - pin_len - 402]

    def _join(*parts: list[str]) -> str:
        return "\n".join(p for group in parts for p in group if p)

    head = _join(must, soft, rest)
    while len(head) > budget and rest:
        rest.pop()
        head = _join(must, soft, rest)
    while len(head) > budget and soft:
        soft.pop()
        head = _join(must, soft, rest)
    if len(head) > budget:
        # Last resort: keep must intact; never chop Desk live mid-block.
        must_blob = _join(must)
        if len(must_blob) <= budget:
            head = must_blob
        else:
            head = must_blob[:budget]
    bits = [head]
    if pinned:
        bits.append(pinned)
    bits.append(user)
    return "\n\n".join(b for b in bits if b)


def build_round_follow_prompt(
    *,
    voice: str,
    prev_voice: str,
    prev_text: str,
    origin_text: str,
    person_block: str = "",
    chat_id: int | str | None = None,
) -> str:
    origin = (origin_text or "").strip()[:800] or "(see recent thread)"
    others = others_for(voice)
    from . import desk_read

    soaking = False
    soak_topic = ""
    try:
        from . import brainstorm as _bs

        soaking = _bs.status() in {"running", "wrapping"}
        soak_topic = _bs.topic() if soaking else ""
    except Exception:
        soaking = False
    prev = (prev_text or "").strip()[:400 if soaking else 1200]
    lane = {
        "bruce": (
            "You are Bruce. Philosophy and academic discussion first, then the ops constraint. English only. "
            "Name Ava or Carly in prose if you have an ops question. "
            "Do not @-mention. Do not address Bruce. You will file the proposal markdown later — do not pretend you attached a file."
        ),
        "carly": (
            "You are Carly. Security, safety, defence, strategy, and performance. No code. No exploits. "
            "You may curse at Ava and Bruce. Do not address Carly. Do not @-mention — the round already continues. "
            "If wrap/local mode is on, conclude. Do not open a new architecture saga."
        ),
        "ava": (
            "You are Ava. Answer prior questions. If you ask, ask Bruce or Carly — never Ava. "
            "If this is the last turn, summarize ideas for the proposal file. No code. "
            "If wrap/local mode is on, close the thread in a complete sentence."
        ),
    }.get(voice, "")
    if soaking:
        lane = {
            "bruce": (
                "You are Bruce. One new ops or feasibility point on the brainstorm topic, or PASS. English only. "
                "Do not @-mention. Do not address Bruce. Do not file mid-round. Do not parrot Already said."
            ),
            "carly": (
                "You are Carly. One new refuse, fail-closed, or safety point on the brainstorm topic, or PASS. No code. "
                "You may curse at Ava and Bruce. Do not address Carly. Do not open a new architecture saga."
            ),
            "ava": (
                "You are Ava. One new visitor-facing or product point on the brainstorm topic, or PASS. "
                "Ask Bruce or Carly — never Ava. If wrapping, at most three decisions. No leftover plans. No code."
            ),
        }.get(voice, lane)
        desk = desk_read.brainstorm_desk_block(soak_topic, cap=400)
        pin = ""
        soak = (
            f"Stay on this topic only: {soak_topic or 'the human ask'}. "
            "One new useful point in your lane (named feature, visitor sentence, rule, file, or constraint), "
            "or the single word PASS. If the last speaker left the topic, do not follow them. "
            "If Already said already has your point, PASS.\n"
            f"{_bs.claimed_block()}\n"
        )
        facts = ""
    else:
        cap = 1200
        desk = desk_read.snapshot_for_prompt(cap=cap)
        pinned = thread_block(chat_id)
        pin = f"{pinned}\n\n" if pinned else ""
        soak = ""
        facts = desk_read.desk_facts_block(cap=cap, ask=origin) + "\n"
        try:
            from . import conclusions as _conc

            if chat_id is not None:
                draft = _conc.draft_block(chat_id, origin, cap=420)
                if draft:
                    facts = draft + "\n" + facts
                sealed = _conc.prompt_block(chat_id, origin, cap=360)
                if sealed:
                    facts = sealed + "\n" + facts
        except Exception:
            pass
    return (
        "Council round (Ava→Bruce→Carly). Stay on the human's original ask. Answer it. Do not dodge or change the subject. "
        f"Talk to {others}. Never ping or question yourself. "
        "You are a team. Add a distinct useful point in your lane. Speak normally — like coworkers, not a script. "
        "If a teammate asked you something in the last message, answer them first. "
        "Do not re-ask a question that already has an answer today. One clear ask is enough. "
        "Do not reply PASS, SKIP, or NO ADD unless this is a brainstorm and you have nothing new. "
        "Do not repeat the last speaker. "
        "This is the public group. Sentences only. "
        "Live numbers only from the desk block. "
        "Ideas and features only — no production code. "
        "Reply in English.\n"
        f"{soak}"
        f"{lane}\n"
        f"{(person_block or '').strip()}\n"
        f"{pin}"
        f"Human asked:\n{origin}\n\n"
        f"{prev_voice} just said:\n{prev}\n"
        f"{facts}"
        f"{desk}\n\n"
        f"You are {voice}. Respond in your lane. Finish every sentence. Do not stop mid-clause. Do not address {voice}."
    )


def build_brainstorm_open_prompt(
    *,
    voice: str,
    origin_text: str,
    chat_id: int | str | None = None,
) -> str:
    """Ava opener for a timed brainstorm — no weather dump, no catch-up history."""
    del chat_id
    return build_round_follow_prompt(
        voice=voice,
        prev_voice="desk",
        prev_text="(session continue — add a new point or PASS)",
        origin_text=origin_text,
    )
