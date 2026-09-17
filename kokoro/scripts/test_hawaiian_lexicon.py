from hawaiian_lexicon import (
    PLACE_PHONEMES,
    fold_place_spellings,
    lexicon_entries,
    pronounce_places,
)
from speakable import speakable


def test_kilauea_and_hawaii_stay_as_names():
    spoken = speakable("Kīlauea on Hawaii island. Hilo and Kauai.")
    assert "kee-lah" not in spoken.lower()
    assert "hah-vye" not in spoken.lower()
    assert "Kilauea" in spoken
    assert "Hawaii" in spoken
    assert "Hilo" in spoken
    assert "Kauai" in spoken


def test_hilo_gold_is_hee_not_high():
    assert "I" not in PLACE_PHONEMES["Hilo"]
    assert PLACE_PHONEMES["Hilo"].startswith("hˈil")


def test_kilauea_gold_is_not_way():
    assert "A" not in PLACE_PHONEMES["Kilauea"]
    assert PLACE_PHONEMES["Kilauea"] == "kˌilɑuˈɛɑ"


def test_no_glottal_in_golds():
    for name, ps in PLACE_PHONEMES.items():
        assert "ʔ" not in ps, name


def test_km_mi_and_compass_are_spoken():
    from speakable import speakable

    out = speakable("14 km SSW of Pahala, Hawaii. 8 mi NW of Hilo.")
    assert "kilometers" in out
    assert "miles" in out
    assert "south-southwest" in out
    assert "northwest" in out
    assert "Hawaii" in out
    assert "Hilo" in out
    assert "SSW" not in out
    assert "NW" not in out
    assert "km" not in out.lower().replace("kilometers", "")
    assert "mi " not in out.lower()



def test_lexicon_overrides_stock_keys():
    extra = lexicon_entries()
    assert extra["Hilo"] == PLACE_PHONEMES["Hilo"]
    assert extra["Kilauea"] == PLACE_PHONEMES["Kilauea"]
    assert extra["Hawaii"] == PLACE_PHONEMES["Hawaii"]


def test_fold_okina():
    assert fold_place_spellings("Hawaiʻi") == "Hawaii"
    assert pronounce_places("Kīlauea") == "Kilauea"
    assert "Halemaumau" in fold_place_spellings("Halemaʻumaʻu")
