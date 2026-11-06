"""Evaluate a ProstoVoice JSON transcript against a reference transcript.

Usage:
    python tools/evaluate_transcript.py reference.json hypothesis.json \
        --processing-seconds 12.4 --audio-duration-seconds 30

Both JSON files use the hackathon contract: {"transcript": [{"text": ...}]}.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


def load_words(path: Path) -> list[str]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    segments = payload.get("transcript")
    if not isinstance(segments, list):
        raise ValueError(f"{path}: expected transcript array")
    text = " ".join(str(item.get("text", "")) for item in segments)
    normalized = re.sub(r"[^\w\s]", " ", text.casefold(), flags=re.UNICODE)
    return normalized.split()


def edit_distance(reference: list[str], hypothesis: list[str]) -> int:
    previous = list(range(len(hypothesis) + 1))
    for ref_index, ref_word in enumerate(reference, start=1):
        current = [ref_index]
        for hyp_index, hyp_word in enumerate(hypothesis, start=1):
            cost = 0 if ref_word == hyp_word else 1
            current.append(
                min(
                    current[-1] + 1,
                    previous[hyp_index] + 1,
                    previous[hyp_index - 1] + cost,
                )
            )
        previous = current
    return previous[-1]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("reference", type=Path)
    parser.add_argument("hypothesis", type=Path)
    parser.add_argument("--processing-seconds", type=float, required=True)
    parser.add_argument("--audio-duration-seconds", type=float, required=True)
    args = parser.parse_args()

    if args.processing_seconds < 0 or args.audio_duration_seconds <= 0:
        parser.error("processing time must be non-negative and audio duration positive")

    reference = load_words(args.reference)
    hypothesis = load_words(args.hypothesis)
    errors = edit_distance(reference, hypothesis)
    wer = errors / len(reference) if reference else (0.0 if not hypothesis else 1.0)
    rtf = args.processing_seconds / args.audio_duration_seconds
    result = {
        "reference_words": len(reference),
        "hypothesis_words": len(hypothesis),
        "edit_distance": errors,
        "wer": wer,
        "rtf": rtf,
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
