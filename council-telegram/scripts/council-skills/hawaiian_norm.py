"""Fold ʻōlelo Hawaiʻi for matching: kahakō and ʻokina are optional."""
from __future__ import annotations

import re
import unicodedata

OKINA = "\u02bb"
_STRIP_MARKS = {OKINA, "\u02bc", "'", "`", "\u2018", "\u2019"}
TOKEN_RE = re.compile(
    r"[A-Za-zĀāĒēĪīŌōŪū" + OKINA + r"'’]+",
    re.UNICODE,
)


def fold_hawaiian(text: str) -> str:
    s = unicodedata.normalize("NFC", text or "")
    for ch in _STRIP_MARKS:
        s = s.replace(ch, "")
    nfd = unicodedata.normalize("NFD", s)
    s = "".join(c for c in nfd if unicodedata.category(c) != "Mn")
    return s.casefold()


def tokens(text: str) -> list[str]:
    s = unicodedata.normalize("NFC", text or "")
    out: list[str] = []
    for m in TOKEN_RE.finditer(s):
        raw = m.group(0)
        if raw.startswith("@"):
            continue
        folded = fold_hawaiian(raw)
        if folded:
            out.append(raw)
    return out
