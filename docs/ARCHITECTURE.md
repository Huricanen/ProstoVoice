# Архитектура и модели

## Поток данных

`MainActivity` на Jetpack Compose принимает URI через Storage Access Framework. `TranscriptionViewModel` запускает уникальную WorkManager-задачу. `TranscriptionWorker` работает в foreground-режиме, публикует прогресс и вызывает `WhisperTranscriptionOrchestrator`.

Оркестратор:

1. декодирует MP3/M4A/AAC и другие поддержанные MediaCodec форматы в mono PCM 16 kHz;
2. делит длинную запись на Whisper-чанки с overlap;
3. вызывает локальный Whisper.cpp через JNI и объединяет ASR-сегменты;
4. извлекает оконные speaker embeddings через sherpa-onnx C API;
5. оценивает число кластеров и выполняет cosine k-means либо использует ручной режим одного speaker;
6. выравнивает speaker intervals и ASR-текст через `TranscriptAligner`;
7. сохраняет результат в `TranscriptStore` и `HistoryStore`.

## Модели

- Whisper runtime по умолчанию: `models/whisper/ggml-large-v3-turbo-q5_0.bin`.
- Speaker embedding runtime: `models/diarization/eres2net-base-3dspeaker-16k.onnx`.
- Offline segmentation fallback: `models/diarization/pyannote-segmentation-3.0-int8.onnx`.
- WeSpeaker и другие Whisper-квантизации находятся в `models/optional` и не являются текущим runtime baseline.

## Границы компонентов

- Kotlin/Compose: UI, состояние, история, экспорт и playback.
- WorkManager: устойчивый фоновой запуск и уведомления.
- JNI/C++ (`app/src/main/cpp`): whisper.cpp и sherpa-onnx native boundary.
- `Transcript`: JSON-контракт `speaker_id`, `start_ms`, `end_ms`, `text`.
- `HistoryStore`: локальный список URI и transcript; сеть в pipeline не используется.
