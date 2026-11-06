# Local model manifest

Model files are provisioned outside Git and are ignored by `.gitignore`.

## Whisper

- File: `models/whisper/ggml-large-v3-q5_0.bin`
- Source: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin`
- Format: Whisper.cpp GGML
- Size: 1,081,140,203 bytes
- SHA-256: `D75795ECFF3F83B5FAA89D1900604AD8C780ABD5739FAE406DE19F23ECD98AD1`
- Status: downloaded and hash-checked on 2026-11-06

The APK does not package this file yet. Native integration and a model-path provisioner are required before inference.

## Diarization

Segmentation and speaker embedding models are not provisioned yet. Candidate repositories must be checked for ONNX input/output compatibility and licenses before download.
