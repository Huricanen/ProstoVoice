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

## Предварительная архитектура

Планируется цепочка: SAF URI → локальная копия → декодирование в PCM → Whisper ASR и diarization → alignment → UI/export. Это рабочая гипотеза до получения ТЗ, а не принятое решение.

## Ограничения

Репозиторий пока не содержит JNI-реализации, моделей, тестов или CI. Сборка не подтверждена: Gradle wrapper, JDK 17+, NDK и CMake недоступны в текущем окружении.
