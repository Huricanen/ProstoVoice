# Тесты и результаты

## Автоматические проверки

Проверено 8 ноября 2026 года в локальном checkout:

- `./gradlew.bat testDebugUnitTest` — успешно.
- `./gradlew.bat assembleDebug` — успешно; native-сборка выполнена для `arm64-v8a` и `x86_64`.
- `git diff --check` — без ошибок перед последними коммитами.

Unit-тесты покрывают AudioChunker, PCM/WAV decoding contracts, diarization clustering, windowed embeddings, transcript alignment, export и JSON-схему.

## Device-проверки

- APK устанавливался через ADB на Poco X7 (`GAJZYLDMOVV8B6RS`, Android 16, arm64-v8a).
- На пользовательских записях подтверждены локальная обработка Whisper, фоновый WorkManager, уведомления и diarization.
- Voice changer на записи `06112026_234534.mp3` был успешно разделён от основного голоса после настройки windowed clustering.
- Ручной режим одного speaker добавлен и исправно работает.
- После локализации исходников `kaldi-native-fbank` debug APK успешно собран с двумя ABI; запуск на Android Studio эмуляторе ещё требует отдельной runtime-проверки.
- Плеер, библиотека, удаление истории и повторное открытие записи требуют дальнейшей ручной UI-проверки на телефоне.

## Известные пробелы

- Нет полноценного instrumented/Compose UI набора.
- Не выполнено повторяемое WER/RTF-сравнение на трёх экспертных эталонах.
- Качество diarization зависит от записи; результаты на одном телефоне не являются гарантией для всех моделей и акустических условий.
