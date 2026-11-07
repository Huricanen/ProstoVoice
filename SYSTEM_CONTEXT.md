# SYSTEM_CONTEXT

## Назначение

ProstoVoice — планируемое Android-приложение для локальной расшифровки аудиозаписей разговоров с временными интервалами и разделением реплик по говорящим.

## Подтверждено

- `README.md` описывает Android-приложение для transcription с timestamps и разными говорящими.
- README упоминает Whisper base и sherpa-onnx.
- Точная версия моделей, минимальная версия Android и целевые ABI пока не подтверждены.
- Создан минимальный Gradle Kotlin DSL-каркас с модулем `app`.
- `MainActivity` использует Compose и SAF `OpenDocument` для выбора `audio/*`.
- `app/src/main/java/com/prostovoice/domain/Transcript.kt` содержит модели transcript и сегмента.
- `app/src/main/java/com/prostovoice/orchestration/TranscriptionOrchestrator.kt` задаёт orchestration boundary; `WhisperTranscriptionOrchestrator.kt` декодирует URI и вызывает native Whisper.
- `TranscriptionViewModel` связывает SAF-выбор файла с orchestration state, progress и error state.
- `app/src/main/java/com/prostovoice/export/TranscriptExport.kt` содержит TXT serialization и JSON export с корневым `transcript`.
- `WavPcmAudioDecoder` поддерживает потоковое чтение PCM 16-bit WAV mono/stereo, downmix и resampling в 16 kHz mono.
- `TranscriptionWorker` и `TranscriptionWorkRequest` задают offline WorkManager boundary для длительной обработки при свёрнутом приложении. Worker вызывает `WhisperTranscriptionOrchestrator`, публикует progress/stage и сохраняет полный результат через `TranscriptStore`; ViewModel наблюдает `WorkInfo`.
- Worker использует foreground notification (`dataSync`) для защиты длительной CPU-обработки от обычных фоновых ограничений Android. `Transcript.processingTimeMs` сохраняется вместе с результатом и показывается в UI.
- После завершения worker публикует отдельное auto-cancel уведомление об успехе или ошибке, поскольку foreground-уведомление удаляется системой вместе с завершением worker.
- На Android 13+ UI запрашивает `POST_NOTIFICATIONS` и предоставляет прямой переход в системные настройки уведомлений приложения.
- Перед alignment native speaker IDs нормализуются по первому появлению; для результатов с 4+ speakers применяется более короткий порог сглаживания, чтобы не удалять быстрые переключения.
- `tools/evaluate_transcript.py` сравнивает два hackathon JSON и вычисляет WER/RTF.

## Архитектура базового уровня

Рабочая цепочка базового уровня: SAF URI → локальный декодер → 16 kHz mono PCM → Whisper ASR и diarization → alignment → UI/export. Все вычисления и модели должны находиться на Android-устройстве.

## Сценарии и уровни результата

Материалы от 2026-11-06 закрепляют пакетный импорт как основной сценарий. RealTime и запись из интерфейса остаются после базового уровня. Командой предложен Whisper Large при тестовом устройстве с примерно 4 ГБ RAM; модель и квантование проверяются отдельным benchmark gate.

Предварительный внешний контракт JSON:

```json
{
  "transcript": [
    {
      "speaker_id": "speaker_1",
      "start_ms": 1250,
      "end_ms": 4380,
      "text": "Добрый день!"
    }
  ]
}
```

Предварительный evaluation flow: три одинаковых тестовых диалога, экспорт результата, сравнение с эталоном, расчёт WER и RTF. Точная нормализация текста и формула метрик ещё не заданы.

## Ограничения

Whisper.cpp source уже встроен в native build. Для телефонного runtime в APK включается GGML Whisper Large-v3 Turbo Q5 из `models/whisper`, а полный Large-v3 Q5 хранится отдельно в `models/optional`; asset извлекается в `files/models` при первом использовании. На подключённом телефоне MP3 8,91 с корректно декодировался, но полный Large-v3 Q5 не завершил `whisper_full` за 13 минут, поэтому Turbo выбран как практический fallback. Sherpa-onnx runtime подключён для arm64-v8a, а segmentation-модель заменена на официальный export с обязательными metadata после несовместимого candidate. Реальный diarization runtime после замены ещё не подтверждён. Последний transcript сохраняется в app-private `files/last_transcript.json`; Whisper обрабатывает длинный PCM последовательными 30-секундными окнами с 2-секундным overlap, а diarization остаётся на полном PCM для согласованных speaker labels. Во время обработки UI показывает текущий этап, elapsed timer и progress по стадиям pipeline. MP3/M4A путь требует дальнейшей device validation. Native build подтверждён на NDK/CMake; Java/Android build — на JDK 17.
