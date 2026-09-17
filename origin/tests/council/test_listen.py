from apps.council.listen import dm_route, is_private_chat, should_handle


def test_dm_route_is_that_bot_only():
    for voice in ("ava", "bruce", "carly"):
        addr = dm_route(voice)
        assert addr["voices"] == [voice]
        assert addr["reason"] == "dm"
        assert addr["round"] is False
        assert addr["group"] is False


def test_should_handle_skips_group_on_bruce():
    group = {"message": {"chat": {"id": -100, "type": "supergroup"}, "text": "hi"}}
    dm = {"message": {"chat": {"id": 9, "type": "private"}, "text": "hi"}}
    assert should_handle(group, listen_voice="bruce", private_only=True) is False
    assert should_handle(dm, listen_voice="bruce", private_only=True) is True
    assert should_handle(group, listen_voice="ava", private_only=False) is True


def test_is_private_chat():
    assert is_private_chat({"type": "private"})
    assert not is_private_chat({"type": "supergroup"})
