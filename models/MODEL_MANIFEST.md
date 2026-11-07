# Local model manifest

Model files are provisioned outside Git and are ignored by `.gitignore`.

## Whisper

- Optional file: `models/optional/ggml-large-v3-q5_0.bin`
- Source: `https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin`
- Format: Whisper.cpp GGML
- Size: 1,081,140,203 bytes
- SHA-256: `D75795ECFF3F83B5FAA89D1900604AD8C780ABD5739FAE406DE19F23ECD98AD1`
- Status: downloaded and hash-checked on 2026-11-06; included in debug APK assets and extracted on first use.

Runtime default: `models/whisper/ggml-small-q5_1.bin` (190,085,487 bytes,
SHA-256 `AE85E4A935D7A567BD102FE55AFC16BB595BDB618E11B2FC7591BC08120411BB`).
This quantized Small model is selected as the quality/speed compromise; full
precision Small, Base and Large variants remain under `models/optional`.

The Gradle build reads this ignored local file from `models/whisper`, packages it as
an uncompressed APK asset, and extracts it to app-private storage on first use.

## Diarization

- Segmentation export: `models/diarization/pyannote-segmentation-3.0-int8.onnx`
- Source: `https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-segmentation-models/sherpa-onnx-pyannote-segmentation-3-0.tar.bz2`
- Size: 1,542,304 bytes
- SHA-256: `D582F4B4C6B48205DE7E0643C57DF0DF5615A3C176189BE3FC461E9D18827B5D`
- Status: sherpa-onnx release export downloaded on 2026-11-06; includes the metadata required by the pyannote loader.

- Speaker embedding: `models/diarization/eres2net-base-3dspeaker-16k.onnx`
- Source: `https://huggingface.co/onemira/eres2net-base-3dspeaker-onnx`
- Size: 39,593,761 bytes
- SHA-256: `1A331345F04805BADBB495C775A6DDFFCDD1A732567D5EC8B3D5749E3C7A5E4B`
- License/provenance: Apache-2.0, sherpa-onnx distribution; LICENSE and NOTICE are retained by the source repository.
- Status: restored as runtime baseline on 2026-11-07 after WeSpeaker produced one cluster with the current threshold; alternative remains under `models/optional`.

The speaker embedding model and runtime remain subject to on-device validation.
