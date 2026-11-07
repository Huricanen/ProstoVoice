import unittest

from evaluate_diarization import evaluate


class EvaluateDiarizationTest(unittest.TestCase):
    def test_ignores_arbitrary_speaker_ids_with_optimal_mapping(self):
        reference = [
            {"start_ms": 0, "end_ms": 1000, "speaker": "1"},
            {"start_ms": 1000, "end_ms": 2000, "speaker": "2"},
        ]
        hypothesis = [
            {"start_ms": 0, "end_ms": 1000, "speaker": "speaker_8"},
            {"start_ms": 1000, "end_ms": 2000, "speaker": "speaker_3"},
        ]
        result = evaluate(reference, hypothesis)
        self.assertEqual(result["der"], 0.0)
        self.assertEqual(result["speaker_label_changes"], 1)

    def test_counts_missed_and_false_speech(self):
        reference = [{"start_ms": 0, "end_ms": 1000, "speaker": "1"}]
        hypothesis = [{"start_ms": 500, "end_ms": 1500, "speaker": "x"}]
        result = evaluate(reference, hypothesis)
        self.assertEqual(result["missed_speech_seconds"], 0.5)
        self.assertEqual(result["false_alarm_seconds"], 0.5)


if __name__ == "__main__":
    unittest.main()
