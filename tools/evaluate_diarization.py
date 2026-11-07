"""Evaluate diarization speaker intervals against a manual reference.

Usage:
    python tools/evaluate_diarization.py reference.json hypothesis.json

Both files use the ProstoVoice ``{"transcript": [...]}`` contract. Metrics are
computed on a 10 ms grid after optimal speaker-label permutation. ``UNKNOWN``
and missing intervals are treated as non-speech in the hypothesis.
"""

from __future__ import annotations

import argparse
import itertools
import json
import sys
from pathlib import Path


FRAME_MS = 10


def load_segments(path: Path) -> list[dict]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    segments = payload.get("transcript")
    if not isinstance(segments, list):
        raise ValueError(f"{path}: expected transcript array")
    result = []
    for index, item in enumerate(segments):
        if not isinstance(item, dict):
            raise ValueError(f"{path}: transcript[{index}] must be an object")
        start = int(item.get("start_ms", -1))
        end = int(item.get("end_ms", -1))
        speaker = str(item.get("speaker_id", item.get("speaker", "")))
        if start < 0 or end <= start or not speaker:
            raise ValueError(f"{path}: invalid interval at transcript[{index}]")
        result.append({"start_ms": start, "end_ms": end, "speaker": speaker})
    return result


def label_frames(segments: list[dict], frame_count: int) -> list[str | None]:
    labels: list[str | None] = [None] * frame_count
    for segment in segments:
        start = max(0, segment["start_ms"] // FRAME_MS)
        end = min(frame_count, (segment["end_ms"] + FRAME_MS - 1) // FRAME_MS)
        for frame in range(start, end):
            labels[frame] = segment["speaker"]
    return labels


def best_mapping(reference: list[str | None], hypothesis: list[str | None]) -> dict[str, str]:
    ref_speakers = sorted({label for label in reference if label is not None})
    hyp_speakers = sorted({label for label in hypothesis if label not in (None, "UNKNOWN")})
    if not ref_speakers or not hyp_speakers:
        return {}
    best_score = -1
    best: dict[str, str] = {}
    for selected in itertools.permutations(ref_speakers, min(len(ref_speakers), len(hyp_speakers))):
        mapping = dict(zip(hyp_speakers, selected))
        score = sum(
            1
            for ref, hyp in zip(reference, hypothesis)
            if ref is not None and mapping.get(hyp) == ref
        )
        if score > best_score:
            best_score = score
            best = mapping
    return best


def evaluate(reference: list[dict], hypothesis: list[dict], collar_ms: int = 0) -> dict:
    if collar_ms < 0:
        raise ValueError("collar_ms must be non-negative")
    max_end = max((item["end_ms"] for item in reference + hypothesis), default=0) + collar_ms
    frame_count = (max_end + FRAME_MS - 1) // FRAME_MS
    ref = label_frames(reference, frame_count)
    hyp = label_frames(hypothesis, frame_count)
    if collar_ms:
        collar_frames = (collar_ms + FRAME_MS - 1) // FRAME_MS
        for index, label in enumerate(ref):
            if label is not None:
                for frame in range(max(0, index - collar_frames), min(frame_count, index + collar_frames + 1)):
                    ref[frame] = label
    mapping = best_mapping(ref, hyp)
    mapped = [mapping.get(label) if label is not None else None for label in hyp]
    speech = sum(label is not None for label in ref)
    missed = sum(ref_label is not None and hyp_label is None for ref_label, hyp_label in zip(ref, mapped))
    false_alarm = sum(ref_label is None and hyp_label is not None for ref_label, hyp_label in zip(ref, mapped))
    confusion = sum(
        ref_label is not None and hyp_label is not None and ref_label != hyp_label
        for ref_label, hyp_label in zip(ref, mapped)
    )
    changes = sum(
        left is not None and right is not None and left != right
        for left, right in zip(mapped, mapped[1:])
    )
    return {
        "reference_speakers": len({label for label in ref if label is not None}),
        "hypothesis_speakers": len({label for label in hyp if label not in (None, "UNKNOWN")}),
        "mapping": mapping,
        "speech_seconds": round(speech * FRAME_MS / 1000, 3),
        "missed_speech_seconds": round(missed * FRAME_MS / 1000, 3),
        "false_alarm_seconds": round(false_alarm * FRAME_MS / 1000, 3),
        "confusion_seconds": round(confusion * FRAME_MS / 1000, 3),
        "der": (missed + false_alarm + confusion) / speech if speech else 0.0,
        "speaker_label_changes": changes,
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("reference", type=Path)
    parser.add_argument("hypothesis", type=Path)
    parser.add_argument("--collar-ms", type=int, default=0)
    args = parser.parse_args()
    result = evaluate(load_segments(args.reference), load_segments(args.hypothesis), args.collar_ms)
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
