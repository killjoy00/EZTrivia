#!/usr/bin/env python3
"""Synthesise the three UI sounds the round screen plays.

Generated rather than sourced so the set stays consistent, stays tiny, and
carries no licensing question. Each cue is a short enveloped sine blend at
22.05 kHz mono, 16-bit -- a few kilobytes each, which matters because they are
decoded on the main thread the first time a round starts.

The design rule is that these play many times per round, so they have to be
unobtrusive on the twentieth hearing rather than striking on the first:
- correct  a rising major third, bright but brief
- wrong    a falling minor second, soft, no harshness
- complete a short major arpeggio, the only cue allowed to be pleased

Usage:  python3 Scripts/make_sounds.py [--out DIR]
"""

from __future__ import annotations

import argparse
import math
import struct
import wave
from pathlib import Path

RATE = 22050


def envelope(i: int, total: int, attack: float = 0.01, release: float = 0.35) -> float:
    """Attack-release shape.

    Without an attack ramp a sine starting mid-cycle produces an audible click,
    which is exactly the artefact that makes a UI sound feel cheap.
    """
    t = i / total
    a = min(1.0, t / attack) if attack > 0 else 1.0
    r = min(1.0, (1.0 - t) / release) if release > 0 else 1.0
    return a * r


def tone(freq: float, seconds: float, amp: float = 0.32, harmonic: float = 0.18) -> list[float]:
    """One note: a sine plus a quiet octave, which reads as warmer than a bare sine."""
    n = int(RATE * seconds)
    out = []
    for i in range(n):
        t = i / RATE
        s = math.sin(2 * math.pi * freq * t) + harmonic * math.sin(4 * math.pi * freq * t)
        out.append(s * amp * envelope(i, n))
    return out


def sequence(notes: list[tuple[float, float]], gap: float = 0.0) -> list[float]:
    """Notes played one after another, overlapped slightly so they run together."""
    out: list[float] = []
    for freq, seconds in notes:
        part = tone(freq, seconds)
        if gap < 0 and out:
            overlap = min(len(out), int(RATE * -gap))
            for i in range(overlap):
                out[len(out) - overlap + i] += part[i]
            out.extend(part[overlap:])
        else:
            out.extend([0.0] * int(RATE * gap))
            out.extend(part)
    return out


def write(path: Path, samples: list[float]) -> None:
    peak = max((abs(s) for s in samples), default=1.0) or 1.0
    # Normalise to a consistent headroom so no one cue is louder than the others.
    scale = 0.72 / peak
    frames = b"".join(
        struct.pack("<h", max(-32768, min(32767, int(s * scale * 32767))))
        for s in samples
    )
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(frames)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=Path("EZTriviaApp/Sounds"))
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)

    cues = {
        # E5 -> G#5, a major third. Short, so it never delays the next tap.
        "correct.wav": sequence([(659.25, 0.09), (830.61, 0.16)], gap=-0.02),
        # F4 -> E4, a semitone down. Quiet and rounded: getting one wrong is
        # already disappointing without the app editorialising about it.
        "wrong.wav": sequence([(349.23, 0.11), (329.63, 0.20)], gap=-0.03),
        # C5 - E5 - G5 - C6.
        "complete.wav": sequence(
            [(523.25, 0.10), (659.25, 0.10), (783.99, 0.10), (1046.50, 0.30)], gap=-0.03
        ),
    }

    for name, samples in cues.items():
        path = args.out / name
        write(path, samples)
        print(f"{path}  {len(samples) / RATE:.2f}s  {path.stat().st_size // 1024}KB")


if __name__ == "__main__":
    main()
