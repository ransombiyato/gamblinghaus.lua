"""Key decoding tests - no X server required."""

from __future__ import annotations

import pytest

from termview.input import CONTROL_KEYS, ESCAPE_SEQUENCES, XDOTOOL_KEYS, decode_key


@pytest.mark.parametrize(
    "raw,expected",
    [
        ("a", ["a"]),
        ("Z", ["Z"]),
        ("1", ["1"]),
        (" ", ["space"]),
        ("\r", ["enter"]),
        ("\n", ["enter"]),
        ("\t", ["tab"]),
        ("\x7f", ["backspace"]),
        ("\x08", ["backspace"]),
        ("\x03", ["ctrl-c"]),
        ("\x04", ["ctrl-d"]),
        ("\x1b", ["escape"]),
    ],
)
def test_single_keys(raw, expected):
    assert decode_key(raw) == expected


@pytest.mark.parametrize("seq,expected", list(ESCAPE_SEQUENCES.items()))
def test_escape_sequences(seq, expected):
    assert decode_key(seq) == [expected]


def test_arrow_sequences():
    assert decode_key("\x1b[A") == ["up"]
    assert decode_key("\x1b[B") == ["down"]
    assert decode_key("\x1b[C") == ["right"]
    assert decode_key("\x1b[D") == ["left"]


def test_multiple_keys_in_one_read():
    assert decode_key("wasd") == ["w", "a", "s", "d"]
    assert decode_key("hi\r") == ["h", "i", "enter"]
    assert decode_key("\x1b[A\x1b[B") == ["up", "down"]


def test_escape_then_char():
    # A lone ESC followed by a normal key should not swallow the key.
    assert decode_key("\x1bq") == ["escape", "q"]


def test_unknown_escape_is_escape():
    # ESC + something that is not a known sequence.
    assert decode_key("\x1b[9") == ["escape", "[", "9"]


def test_every_logical_key_has_a_mapping_or_is_literal():
    for logical in list(ESCAPE_SEQUENCES.values()) + list(CONTROL_KEYS.values()):
        assert logical in XDOTOOL_KEYS or len(logical) == 1 or logical.startswith("ctrl-")


def test_xdotool_letters_and_digits_present():
    for ch in "abcdefghijklmnopqrstuvwxyz0123456789":
        assert XDOTOOL_KEYS[ch] == ch
