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

- Speaker embedding: `models/diarization/wespeaker-en-voxceleb-resnet34.onnx`
- Source: `https://github.com/k2-fsa/sherpa-onnx/releases/download/speaker-recongition-models/wespeaker_en_voxceleb_resnet34.onnx`
- Size: 26,534,365 bytes
- SHA-256: `5EF208A9DA1453335308A6B6F4E6DFBD7E183A38B604DE0A57664F45D257FE94`
- License/provenance: official sherpa-onnx speaker-recognition release; model metadata identifies framework `wespeaker`, language `English`, and sample rate `16000`.
- Status: downloaded on 2026-11-07; Android runtime validation pending.

The speaker embedding model and runtime remain subject to on-device validation.
