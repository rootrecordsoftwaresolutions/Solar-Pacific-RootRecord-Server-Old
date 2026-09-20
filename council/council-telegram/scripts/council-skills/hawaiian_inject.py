"""Build a short Hawaiian inject block from desk facts + dictionary hits."""
from __future__ import annotations

from .hawaiian_dict import format_hits, lookup_in_text, message_looks_hawaiian
from .hawaiian_norm import fold_hawaiian

INJECT_CAP = 1000

RULES = (
    "Spelling facts only. Keep the reply in English. "
    "Do not write a full Hawaiian paragraph. "
    "Unknown words are not automatically Hawaiian — say you don’t know. "
    "Do not invent etymology, gods, or secret codes."
)

NOT_HAWAIIAN = (
    "Kiweh / Kileau / Kiwea are not Hawaiian; treat as a likely misspelling of Kīlauea. Say so."
)

# (folded aliases, fact line)
DESK: list[tuple[tuple[str, ...], str]] = [
    (("hawaii", "hawaiian"), "Hawaiʻi (hawaii): the islands; also Hawaiʻi island. Fern Forest is on Hawaiʻi island."),
    (("kilauea", "kiweh", "kileau", "kiwea"), "Kīlauea (kilauea): active volcano, Hawaiʻi island; Kīlauea Alerts branding. Not “Kiweh.”"),
    (("hilo",), "Hilo: district / town, Hawaiʻi island. Don’t invent boundaries."),
    (("puna",), "Puna: district, Hawaiʻi island. Don’t invent boundaries."),
    (("kau", "ka u"), "Kaʻū (kau): district, Hawaiʻi island. Don’t invent boundaries."),
    (("kona",), "Kona: district, Hawaiʻi island. Don’t invent boundaries."),
    (("mauna loa", "maunaloa"), "Mauna Loa: volcano, Hawaiʻi island."),
    (("mauna kea", "maunakea", "waakea", "wakea"), "Mauna Kea (Mauna a Wākea is the respectful name — note it, don’t lecture)."),
    (("oahu",), "Oʻahu (oahu)."),
    (("maui",), "Maui."),
    (("kauai",), "Kauaʻi (kauai)."),
    (("molokai",), "Molokaʻi (molokai)."),
    (("lanai",), "Lānaʻi (lanai)."),
    (("niihau", "ni ihau"), "Niʻihau (niihau)."),
    (("kahoolawe",), "Kahoʻolawe (kahoolawe)."),
    (("aloha",), "aloha: greeting / love / compassion — don’t over-explain."),
    (("mahalo",), "mahalo: thanks."),
    (("aina",), "ʻāina (aina): land, that which feeds. Bare “aina” is a different dictionary word."),
    (("kai",), "kai: sea."),
    (("mauna",), "mauna: mountain."),
    (("wahine",), "wahine: woman."),
    (("kane",), "kāne (kane): man."),
    (("ohana",), "ʻohana (ohana): family — don’t Disney-expand."),
    (("kamaaina",), "kamaʻāina (kamaaina): local to this place."),
    (("malihini",), "malihini: visitor."),
    (("olelo",), "ʻōlelo Hawaiʻi (olelo hawaii): Hawaiian language."),
    (("pau",), "pau: finished."),
    (("wikiwiki",), "wikiwiki: quick."),
    (("kokua",), "kōkua (kokua): help."),
    (("mauka",), "mauka: toward the mountain."),
    (("makai",), "makai: toward the sea."),
    (("fern forest", "fernforest"), "Fern Forest is on Hawaiʻi island."),
]


# Clock / branding English — not a request for place glossary lines.
_CLOCK_PLACE_PHRASES = (
    "hawaiian standard time",
    "hawaii standard time",
    "hawaii pacific solar",
    "hawaii pacific",
)


def _strip_clock_place(folded: str) -> str:
    out = folded or ""
    for phrase in _CLOCK_PLACE_PHRASES:
        out = out.replace(phrase, " ")
    return out


def _desk_lines(folded: str) -> list[str]:
    lines: list[str] = []
    for aliases, fact in DESK:
        if any(a and a in folded for a in aliases):
            lines.append("- " + fact)
    return lines


def format_hawaiian_block(text: str) -> str:
    folded = fold_hawaiian(text)
    desk_folded = _strip_clock_place(folded)
    parts: list[str] = [RULES]
    if any(x in desk_folded for x in ("kiweh", "kileau", "kiwea", "kilauea")):
        parts.append(NOT_HAWAIIAN)
    desk = _desk_lines(desk_folded)
    if not desk and message_looks_hawaiian(text):
        desk = [
            "- Hawaiʻi: we live here. Use glossary spelling. Unknown tokens are not automatically Hawaiian.",
        ]
    if desk:
        parts.append("Desk:\n" + "\n".join(desk))
    hits = lookup_in_text(text)
    extra = format_hits(hits, cap=max(200, INJECT_CAP - 80 - sum(len(p) for p in parts)))
    if extra:
        parts.append("Dictionary (Wiktionary Hawaiian, CC-BY-SA):\n" + extra)
    blob = "\n".join(parts)
    if len(blob) > INJECT_CAP:
        blob = blob[: INJECT_CAP - 1] + "…"
    return blob
