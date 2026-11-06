# Local model manifest

Model files are provisioned outside Git and are ignored by `.gitignore`.

## Whisper

- Optional file: `models/optional/ggml-large-v3-q5_0.bin`
- Source: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin`
- Format: Whisper.cpp GGML
- Size: 1,081,140,203 bytes
- SHA-256: `D75795ECFF3F83B5FAA89D1900604AD8C780ABD5739FAE406DE19F23ECD98AD1`
- Status: downloaded and hash-checked on 2026-11-06; included in debug APK assets and extracted on first use.

Runtime default: `models/whisper/ggml-large-v3-turbo-q5_0.bin` (574,041,195 bytes,
SHA-256 `394221709CD5AD1F40C46E6031CA61BCE88931E6E088C188294C6D5A55FFA7E2`).
The full Large-v3 Q5 file remains available as an alternative but was too slow
for the first 8-second phone smoke test.

The Gradle build reads this ignored local file from `models/whisper`, packages it as
an uncompressed APK asset, and extracts it to app-private storage on first use.

## Diarization

- Candidate segmentation export: `models/diarization/pyannote-segmentation-3.0-int8.onnx`
- Source: `https://huggingface.co/onnx-community/pyannote-segmentation-3.0`
- Size: 1,542,304 bytes
- SHA-256: `465D0975BF70FBF14FB77C0589A5D346A9C07C2170345F529CF774678446DB76`
- Status: downloaded on 2026-11-06 for compatibility inspection; not wired into runtime yet.

Speaker embedding model and sherpa-onnx runtime are still pending. Do not treat this
segmentation export as a complete diarization pipeline until its ONNX signature,
embedding model, clustering and licenses are verified together.
