"""Kokoro US phonemes for Hawaiian names. Do not respell into hyphen English.

Misaki US: i=ee, ɪ=ih, ɑ=ah, A=ay, I=eye, O=oh, Q=ow, u=oo, ɛ=eh, ə=uh.
Do not put ʔ in golds — Kokoro turns the glottal into a T (Hawaii → “hah-vye-tee”).
Stock golds misread several of these (Hilo as HIGH-loh, Maui as MOW-ee,
Kilauea as kill-uh-WAY-uh). Keep the real spelling in speakable text so
these golds apply on Heart, Echo, and Nova.
"""

# kee-lah-oo-EH-ah (not kill-uh-WAY-uh, not Kiweh)
KILAUEA = "kˌilɑuˈɛɑ"

PLACE_PHONEMES = {
    "Hawaii": "hɑˈvIi",
    "Hawaiian": "hɑˈvIjən",
    "Kilauea": KILAUEA,
    "Hilo": "hˈilO",
    "Honolulu": "hˌonoˈlulu",
    "Puna": "pˈunɑ",
    "Kona": "kˈonɑ",
    "Kau": "kɑˈu",
    "Mauna": "mˈɑunɑ",
    "Loa": "lˈoɑ",
    "Kea": "kˈeɑ",
    "Oahu": "oˈɑhu",
    "Maui": "mˈɑui",
    "Kauai": "kɑˈwɑi",
    "Molokai": "moloˈkɑi",
    "Lanai": "lɑˈnɑi",
    "Niihau": "niiˈhɑu",
    "Kahoolawe": "kɑhooˈlɑve",
    "Lihue": "liˈhuɛ",
    "Pahala": "pɑˈhɑlɑ",
    "Pahoa": "pɑˈhoɑ",
    "Honaunau": "honɑuˈnɑu",
    "Napoopoo": "nɑpooˈpoo",
    "Waimea": "wɑiˈmeɑ",
    "Wahiawa": "wɑhiˈɑvɑ",
    "Kaneohe": "kɑneˈohe",
    "Kailua": "kɑiˈluɑ",
    "Kapoho": "kɑˈpoho",
    "Kalapana": "kɑlɑˈpɑnɑ",
    "Kahului": "kɑhuˈlui",
    "Wailuku": "wɑiˈluku",
    "Lahaina": "lɑˈhɑinɑ",
    "Kihei": "kiˈhei",
    "Hana": "hˈɑnɑ",
    "Haleakala": "hɑleɑkɑˈlɑ",
    "Waikiki": "wɑikiˈki",
    "Halemaumau": "hɑlɛmɑˈumɑu",
    "Puuoo": "puuˈoo",
    "Keauhou": "kɛɑuˈho",
    "Kealakekua": "kɛɑlɑkɛˈkuɑ",
    "Waikoloa": "wɑikoˈloɑ",
    "Kohala": "koˈhɑlɑ",
    "Hamakua": "hɑmɑˈkuɑ",
    "Honokaa": "honoˈkɑɑ",
    "Waipio": "wɑiˈpiO",
    "Naalehu": "nɑɑˈlehu",
    "Waianae": "wɑiɑˈnɑɛ",
    "Ewa": "ˈɛvɑ",
    "Koolau": "kooˈlɑu",
    "Olomana": "oloˈmɑnɑ",
    "Kalawao": "kɑlɑˈvɑo",
    "Kipahulu": "kipɑˈhulu",
    "Pele": "pˈɛlɛ",
    "Aloha": "ɑˈlohɑ",
    "Mahalo": "mɑˈhɑlo",
    "Aina": "ˈɑinɑ",
    "Ohana": "oˈhɑnɑ",
    "Mauka": "mˈɑukɑ",
    "Makai": "mɑˈkɑi",
    "Kamaaina": "kɑmɑˈɑinɑ",
    "Malihini": "mɑliˈhini",
    "Kokua": "koˈkuɑ",
    "Tonga": "tˈɑŋɡə",
    "Vanuatu": "vˌɑnuˈɑtu",
    "Fiji": "fˈidʒi",
    "Kermadec": "kɜɹmˈædɛk",
    "Honshu": "hˈɑnʃu",
    "Hokkaido": "hokˈIdO",
    "Mindanao": "mɪndəˈnɑO",
    "Luzon": "luzˈɑn",
    "Sumatra": "səˈmɑtrə",
    "Java": "dʒˈɑvə",
    "Sulawesi": "sulɑwˈesi",
    "Kamchatka": "kæmˈʧætkə",
    "Kuril": "kˈʊɹəl",
    "Aleutian": "əlˈuʃən",
    "Guam": "ɡwˈɑm",
    "Samoa": "səˈmoə",
    "Tahiti": "təˈhiti",
    "Oaxaca": "wəˈhɑkə",
    "Guerrero": "ɡəˈɹɛrO",
    "Michoacan": "miʧoˈɑkɑn",
    "Andreanof": "ændriˈænɑf",
    "Unimak": "ˈunɪmæk",
    "Kodiak": "kˈodiæk",
}

# Human stress guide only. Never feed these hyphen strings to Kokoro.
SPEAK_AS = {
    "Hawaii": "hah-VYE-ee",
    "Hawaiian": "hah-VYE-uhn",
    "Kilauea": "kee-lah-oo-EH-ah",
    "Hilo": "HEE-loh",
    "Honolulu": "hoh-noh-LOO-loo",
    "Puna": "POO-nah",
    "Kona": "KOH-nah",
    "Kau": "kah-OO",
    "Mauna Loa": "MAH-oo-nah LOH-ah",
    "Mauna Kea": "MAH-oo-nah KAY-ah",
    "Mauna": "MAH-oo-nah",
    "Loa": "LOH-ah",
    "Kea": "KAY-ah",
    "Oahu": "oh-AH-hoo",
    "Maui": "MAH-oo-ee",
    "Kauai": "kah-WAH-ee",
    "Molokai": "moh-loh-KAH-ee",
    "Lanai": "lah-NAH-ee",
    "Niihau": "NEE-ee-how",
    "Kahoolawe": "kah-hoh-oh-LAH-vay",
    "Lihue": "LEE-hoo-eh",
    "Pahala": "pah-HAH-lah",
    "Pahoa": "pah-HOH-ah",
    "Honaunau": "hoh-NOW-now",
    "Napoopoo": "nah-POH-oh-POH-oh",
    "Waimea": "wy-MAY-ah",
    "Wahiawa": "wah-hee-ah-VAH",
    "Kaneohe": "kah-neh-OH-heh",
    "Kailua": "kye-LOO-ah",
    "Kapoho": "kah-POH-hoh",
    "Kalapana": "kah-lah-PAH-nah",
    "Kahului": "kah-hoo-LOO-ee",
    "Wailuku": "wy-LOO-koo",
    "Lahaina": "lah-HY-nah",
    "Kihei": "KEE-hay",
    "Hana": "HAH-nah",
    "Haleakala": "hah-leh-ah-kah-LAH",
    "Waikiki": "wy-kee-KEE",
    "Halemaumau": "hah-leh-mah-OO-mow",
    "Pele": "PEH-leh",
    "Aloha": "ah-LOH-hah",
    "Mahalo": "mah-HAH-loh",
}

SPELLING_FOLDS = (
    ("Hawaiʻi", "Hawaii"),
    ("Hawai'i", "Hawaii"),
    ("Kīlauea", "Kilauea"),
    ("Kilauea", "Kilauea"),
    ("Halemaʻumaʻu", "Halemaumau"),
    ("Halema'uma'u", "Halemaumau"),
    ("Halemaumau", "Halemaumau"),
    ("Puʻuʻōʻō", "Puuoo"),
    ("Pu'u'o'o", "Puuoo"),
    ("Kaʻū", "Kau"),
    ("Ka'u", "Kau"),
    ("Oʻahu", "Oahu"),
    ("O'ahu", "Oahu"),
    ("Kauaʻi", "Kauai"),
    ("Kaua'i", "Kauai"),
    ("Molokaʻi", "Molokai"),
    ("Moloka'i", "Molokai"),
    ("Lānaʻi", "Lanai"),
    ("Lana'i", "Lanai"),
    ("Niʻihau", "Niihau"),
    ("Ni'ihau", "Niihau"),
    ("Kahoʻolawe", "Kahoolawe"),
    ("Kaho'olawe", "Kahoolawe"),
    ("Līhuʻe", "Lihue"),
    ("Lihu'e", "Lihue"),
    ("Pāhala", "Pahala"),
    ("Pāhoa", "Pahoa"),
    ("Hōnaunau", "Honaunau"),
    ("Nāpoʻopoʻo", "Napoopoo"),
    ("Napo'opo'o", "Napoopoo"),
    ("Kāneʻohe", "Kaneohe"),
    ("Kane'ohe", "Kaneohe"),
    ("Haleakalā", "Haleakala"),
    ("Waiʻanae", "Waianae"),
    ("Wai'anae", "Waianae"),
    ("ʻEwa", "Ewa"),
    ("'Ewa", "Ewa"),
    ("Koʻolau", "Koolau"),
    ("Ko'olau", "Koolau"),
    ("Hāmākua", "Hamakua"),
    ("Honokaʻa", "Honokaa"),
    ("Honoka'a", "Honokaa"),
    ("Waipiʻo", "Waipio"),
    ("Waipi'o", "Waipio"),
    ("Nāʻālehu", "Naalehu"),
    ("Na'alehu", "Naalehu"),
    ("Kīpahulu", "Kipahulu"),
    ("ʻāina", "Aina"),
    ("'aina", "Aina"),
    ("ʻohana", "Ohana"),
    ("kamaʻāina", "Kamaaina"),
    ("kama'aina", "Kamaaina"),
    ("kōkua", "Kokua"),
    ("Mauna Loa", "Mauna Loa"),
    ("Mauna Kea", "Mauna Kea"),
    ("Honaunau-Napoopoo", "Honaunau Napoopoo"),
    ("Honaunau-Napo'opo'o", "Honaunau Napoopoo"),
)


def fold_place_spellings(text: str) -> str:
    out = text or ""
    for src, dest in SPELLING_FOLDS:
        out = out.replace(src, dest)
        out = out.replace(src.lower(), dest)
        out = out.replace(src.upper(), dest)
    return out


def pronounce_places(text: str) -> str:
    """Fold okina spellings onto lexicon keys. Do not hyphen-respell."""
    return fold_place_spellings(text or "")


def lexicon_entries() -> dict[str, str]:
    extra: dict[str, str] = {}
    for name, ps in PLACE_PHONEMES.items():
        ps = ps.replace("ʔ", "")
        extra[name] = ps
        extra[name.lower()] = ps
        extra[name.upper()] = ps
        if not name.endswith("s"):
            extra[name + "'s"] = ps + "z"
            extra[name.lower() + "'s"] = ps + "z"
    extra["Kīlauea"] = KILAUEA
    extra["kīlauea"] = KILAUEA
    extra["HI"] = PLACE_PHONEMES["Hawaii"]
    extra["H.I."] = PLACE_PHONEMES["Hawaii"]
    extra["H.I"] = PLACE_PHONEMES["Hawaii"]
    return extra


DIAGNOSE_NAMES = (
    "Hawaii",
    "Hawaiian",
    "Kilauea",
    "Hilo",
    "Honolulu",
    "Puna",
    "Kona",
    "Kau",
    "Mauna Loa",
    "Mauna Kea",
    "Oahu",
    "Maui",
    "Kauai",
    "Molokai",
    "Lanai",
    "Niihau",
    "Kahoolawe",
    "Lihue",
    "Pahoa",
    "Halemaumau",
    "Waikiki",
    "Lahaina",
    "Haleakala",
    "Kaneohe",
    "Waianae",
    "Ewa",
    "Kohala",
    "Kalawao",
    "Aloha",
    "Mahalo",
    "Pele",
)
