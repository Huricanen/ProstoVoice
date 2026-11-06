# SYSTEM_CONTEXT

## Назначение

ProstoVoice — планируемое Android-приложение для локальной расшифровки аудиозаписей разговоров с временными интервалами и разделением реплик по говорящим.

## Подтверждено

- `README.md` описывает Android-приложение для transcription с timestamps и разными говорящими.
- README упоминает Whisper base и sherpa-onnx.
- Точная схема pipeline, форматы моделей, минимальная версия Android и способ фоновой обработки пока не подтверждены.
- Создан минимальный Gradle Kotlin DSL-каркас с модулем `app`.
- `MainActivity` использует Compose и SAF `OpenDocument` для выбора `audio/*`.
- `app/src/main/java/com/prostovoice/domain/Transcript.kt` содержит модели transcript и сегмента.
- `app/src/main/java/com/prostovoice/orchestration/TranscriptionOrchestrator.kt` задаёт границу будущего native pipeline и placeholder.
- `TranscriptionViewModel` связывает SAF-выбор файла с orchestration state, progress и error state.
- `app/src/main/java/com/prostovoice/export/TranscriptExport.kt` содержит TXT/JSON serialization.
- `WavPcmAudioDecoder` поддерживает потоковое чтение PCM 16-bit WAV mono/stereo, downmix и resampling в 16 kHz mono.
- `TranscriptionWorker` и `TranscriptionWorkRequest` задают offline WorkManager boundary для длительной обработки при свёрнутом приложении.

## Предварительная архитектура

Планируется цепочка: SAF URI → локальная копия → декодирование в PCM → Whisper ASR и diarization → alignment → UI/export. Это рабочая гипотеза до получения ТЗ, а не принятое решение.

## Предварительное уточнение сценариев

Материалы от 2026-11-06 указывают на пакетный импорт как основной сценарий. RealTime должен проектироваться как отдельный режим поверх общего audio/ASR слоя и не должен блокировать импорт готовых файлов. Обсуждается переход с Whisper base на Whisper Large при тестовом устройстве с примерно 4 ГБ RAM; модель, квантование и фактический memory budget ещё не подтверждены.

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

Репозиторий пока не содержит реальных Whisper/sherpa-onnx моделей, MP3/M4A decoder и CI. WorkManager пока вызывает placeholder orchestrator. Native build требует NDK и CMake; Java/Android build подтверждён на JDK 17.
