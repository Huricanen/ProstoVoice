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
- `TranscriptionWorker` и `TranscriptionWorkRequest` задают offline WorkManager boundary для длительной обработки при свёрнутом приложении; worker пока требует отдельного подключения real orchestrator.
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

Whisper.cpp source уже встроен в native build. Для телефонного runtime в APK включается GGML Whisper Large-v3 Turbo Q5 из `models/whisper`, а полный Large-v3 Q5 хранится отдельно в `models/optional`; asset извлекается в `files/models` при первом использовании. На подключённом телефоне MP3 8,91 с корректно декодировался, но полный Large-v3 Q5 не завершил `whisper_full` за 13 минут, поэтому Turbo выбран как практический fallback. sherpa-onnx runtime/models ещё не подключены. MP3/M4A путь требует дальнейшей device validation. Native build подтверждён для arm64-v8a на NDK/CMake; Java/Android build — на JDK 17.

## Архитектура model manager (частично реализована)

В приложении добавлена начальная централизованная модельная слойность:

- `app/src/main/java/com/prostovoice/modelmanager/ModelCatalog.kt` содержит семейства моделей (`Whisper`, `Parakeet`, `GigaAM v3`, `GigaAM v3 Multilingual`), статусы и словарь доступных вариантов.
- `ModelManager.kt` хранит локальный реестр моделей в `files/models/registry/models.json`, обеспечивает служебные методы для сохранения модели, безопасного удаления, проверки пути и фиксирует встроенную Whisper-модель как базовый готовый вариант.
- `ModelSelector.kt` выбирает предпочтительную готовую модель и возвращается к встроенному Whisper, если резервная или активная модель недоступна.
- `WhisperTranscriptionOrchestrator.kt` больше не рассчитывает путь к модели напрямую в месте вызова; он получает рабочую конфигурацию через `ModelManager`/`ModelSelector` и сохраняет fallback на встроенный Whisper.

Это не полноценный TDD-реализация каталога загрузки/импорта/удаления моделей из пункта 4–7, но это уже добавляет центральную точку для будущих расширений и защищает начальный сценарий от жёстко зашитых путей.

## Текущий статус сценария

Основная цепочка теперь оформлена так: SAF URI → локальный декодер → 16 kHz mono PCM → выбранная рабочая модель Whisper → native backend → alignment → UI/export. Встроенная Whisper-модель остаётся офлайн-доступной и является базовой гарантией работы при первом запуске.

Проверка и инициализация дополнительных моделей, каталога, импортов, удаления и real backend-функций для Parakeet/GigaAM остаются на ближайшем этапе реализации и не считаются завершёнными.
