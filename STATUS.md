# STATUS

Дата проверки: 2026-11-07 (Europe/Moscow).

## Новый baseline диаризации

- [x] 2026-11-07: добавлен ручной эталон speaker intervals и полного текста для записи `07.11.2026 13.40.m4a` в `evaluation/diarization/07.11.2026-13.40.reference.json`. Аудио в репозиторий не копировалось.
- [ ] Runtime-прогон этого baseline на Android не выполнен: в текущей среде отсутствует `adb`; метрики DER/JER и сравнение с фактическими intervals ещё не рассчитаны.
- [x] 2026-11-07: добавлен `tools/evaluate_diarization.py` с оптимальным сопоставлением speaker labels, DER и диагностикой missed/false-alarm/confusion; добавлены Python unit tests.
- [x] 2026-11-07: native diarization parameters вынесены в `DiarizationConfig` (число кластеров, threshold и min-duration), добавлены проверки диапазонов; default profile сохранён прежним.
- [x] 2026-11-07: эксперимент с `windowShiftRatio=0.05` завершён без улучшения: runtime 233 с, 5 speakers и 17 raw-сегментов против baseline 4 speakers и 14 raw-сегментов.
- [ ] 2026-11-07: для следующего device-прогона выбран фиксированный `numClusters=4` при `windowShiftRatio=0.1`; влияние на DER ещё не подтверждено.
- [x] 2026-11-07: прогон `numClusters=4`, `threshold=0.75` дал 3 фактических speakers и `DER=0.2997`; фиксированное число кластеров не разделило смешанные голоса.
- [ ] 2026-11-07: для следующего device-прогона выбран `numClusters=4`, `threshold=0.5`, `windowShiftRatio=0.1`.
- [x] 2026-11-07: threshold `0.5` не изменил raw intervals: остались 3 speakers и те же 13 сегментов; проблема не объясняется clustering threshold.
- [ ] 2026-11-07: следующий device-прогон переключён на локальную WeSpeaker ResNet34 embedding-модель при `numClusters=4`.
- [x] 2026-11-07: WeSpeaker при `numClusters=4`, `threshold=0.5` дал 2 speakers и схлопнул интервалы; эксперимент отклонён.
- [x] 2026-11-07: runtime возвращён к лучшему подтверждённому baseline: ERes2Net, автоматическое число кластеров, threshold `0.75`, window shift `0.1`.
- [x] 2026-11-07: добавлен экспериментальный оконный embedding path: 1.5-секундные окна с шагом 0.5 секунды, отдельный sherpa embedding extractor и cosine k-means; старый diarizer оставлен fallback. APK собран, unit tests прошли и установлен на Poco X7; качество на записи ещё не измерено.
- [x] 2026-11-07: оконный embedding path проверен на Poco X7 на `07.11.2026 13.40.m4a`: 4 speakers, `DER=0.1057`, missed `0.00s`, false alarm `4.68s`, confusion `2.40s`. Это лучше baseline ERes2Net offline diarizer (`DER=0.1981`, confusion `8.59s`).
- [x] 2026-11-07: первый device baseline извлечён с Poco X7: 4 speakers, 14 raw-сегментов; interval evaluator дал `DER=0.1981` (missed `0.00s`, false alarm `4.68s`, confusion `8.59s`). Основная ошибка — пропущенные/сдвинутые границы turn.
- [x] 2026-11-07: APK с raw interval logging собран, unit tests прошли и APK повторно установлен на Poco X7.
- [x] 2026-11-07: debug APK собран с Android SDK/NDK и установлен через ADB на устройство `GAJZYLDMOVV8B6RS`; `com.prostovoice/.MainActivity` подтверждена как resumed. APK SHA-256: `600BFD64BBCF8C897FE3BA58A2EACBF1844E0DD022F6A21E4C70C63BC109B59A`.
- [x] 2026-11-07: `gradlew.bat test` повторно выполнен после добавления `DiarizationConfig`; debug/release unit tests прошли.

## Подтверждено

- [x] Git-репозиторий создан; текущая ветка `main` отслеживает `origin/main`.
- [x] В истории есть один коммит `Initial commit`.
- [x] В репозитории есть `.gitignore` и `README.md`.
- [x] `.gitignore` дополнен стандартными Android Studio, Gradle, native build и OS-исключениями.

## Не начато или не подтверждено

- [x] Минимальный Gradle/Android-проект и AndroidManifest созданы.
- [x] Базовый Compose UI с SAF-пикером и placeholder orchestration создан.
- [x] ViewModel, progress/error состояния и TXT/JSON export через SAF созданы.
- [x] Native boundary и fallback stub созданы.
- [x] Alignment helper и JUnit-тесты добавлены.
- [x] Gradle wrapper 8.7 создан.
- [x] 2026-11-06: `gradlew.bat assembleDebug` успешно выполнен на Temurin JDK 17 с локальным Android SDK; APK создан размером 11 528 022 байта (`app/build/outputs/apk/debug/app-debug.apk`). Gradle предупредил, что AGP 8.5.2 тестировался до compileSdk 34 при `compileSdk = 35`.
- [x] 2026-11-06: `gradlew.bat test` успешно выполнен; debug/release unit test tasks прошли.
- [x] 2026-11-06: после подключения codec decoder, native boundary и JSON schema повторно выполнены `gradlew.bat test assembleDebug`; BUILD SUCCESSFUL, 69 задач, APK 11 528 022 байта.
- [x] PCM 16-bit WAV decoder, mono downmix и 16 kHz resampling добавлены; unit tests проходят.
- [x] Offline WorkManager request/worker с progress и fallback marker добавлен; unit tests проходят.
- [x] 2026-11-06: Whisper.cpp source integrated as tracked third-party dependency; arm64 CMake build succeeds with `GGML_NATIVE=OFF`.
- [x] 2026-11-06: local Whisper Large-v3 Q5 model downloaded and hash-checked; APK does not package it yet.
- [x] 2026-11-06: Whisper GGML model is packaged as an uncompressed APK asset and extracted atomically on first use; debug APK is 1,099,232,103 bytes.
- [x] 2026-11-06: phone smoke logs showed correct 8.91 s MP3 decode but full Large-v3 Q5 remained in `whisper_full` after 13 minutes; runtime default switched to Large-v3 Turbo Q5, clean APK size 592,133,109 bytes.
- [x] sherpa-onnx diarization runtime and arm64 native build integrated; diarization model compatibility was validated statically with the required ONNX metadata.
- [x] 2026-11-06: native boundary усилена валидацией входов и offline status JSON; реальный runtime ещё не подключён.
- [x] 2026-11-06: MP3/M4A/AAC decoder добавлен через MediaExtractor/MediaCodec; device codec validation ещё не выполнена.
- [x] 2026-11-06: JSON export приведён к корневому `transcript` контракту из ТЗ; unit tests проходят.
- [x] 2026-11-06: placeholder orchestration теперь возвращает явную ошибку отсутствующего local backend и не создаёт ложный успешный transcript.
- [x] 2026-11-06: добавлен `tools/evaluate_transcript.py` для WER/RTF по hackathon JSON; `py_compile` и CLI help проходят.
- [x] 2026-11-06: APK установлен на AVD `Medium_Phone_API_37.0` через ADB и приложение запущено; `com.prostovoice` имеет активный процесс и resumed `MainActivity`. Сделан снимок стартового экрана для визуальной smoke-проверки.
- [x] 2026-11-06: APK установлен на физический Poco X7 (`2412DPC0AG`, Android 16) через ADB; приложение запущено, `MainActivity` подтверждена как resumed.
- [x] 2026-11-06: после падения при первом запуске diarization заменена несовместимая pyannote segmentation-модель на официальный sherpa-onnx export; добавлены native runtime logs и serialisation вызовов diarization. Новый debug APK собирается (271 646 364 байта, SHA-256 `CDB23EFF1F00E1FEC4AA3196AA32CE8A5AF0CF20A4EA8A46B6C77F4E2BECCF39`), но ещё не установлен на телефон и не проверен воспроизведением.
- [x] 2026-11-06: свежий debug APK после `assembleDebug` (592 133 109 байт, SHA-256 `C3D5ABF994C3CED000D7F0BBEE6A782F1A23FB18DC3DEEC258A9EA7AF62E51E4`) установлен на Poco X7 и запущен.
- [x] 2026-11-06: презентация хакатона и присланные изображения приняты как рабочее ТЗ базового уровня.
- [x] 2026-11-06: arm64-v8a native library builds with installed NDK 27.2.12479018 and CMake 3.22.1.
- [ ] Проверка MediaExtractor/MediaCodec на реальных MP3/M4A/AAC файлах на Android.
- [x] 2026-11-06: UI orchestrator now decodes local audio and calls Whisper.cpp when the model is provisioned in app files.
- [x] 2026-11-07: WorkManager подключён к реальному `WhisperTranscriptionOrchestrator`; worker публикует progress/stage и сохраняет полный transcript через `TranscriptStore`, а ViewModel наблюдает состояние задачи и восстанавливает результат после завершения.
- [ ] Выполнить WER/RTF на трёх предоставленных экспертами записях.
- [ ] Instrumented/Compose tests на эмуляторе или телефоне.
- [ ] Повторить обработку на Poco X7 с новым APK и собрать logcat по тегу `ProstoVoiceDiarization`; реальный runtime diarization пока не подтверждён.
- [x] 2026-11-07: добавлено сохранение последнего transcript в app-private `files/last_transcript.json` с восстановлением после перезапуска; запись выполняется атомарно через временный файл.
- [x] 2026-11-07: длинные записи разбиваются для Whisper на последовательные 30-секундные окна с 2-секундным overlap; timestamps переводятся в абсолютное время, дубли overlap удаляются. Diarization пока выполняется на полном PCM для сохранения согласованных speaker labels.
- [x] 2026-11-07: UI показывает elapsed timer, текущий этап и determinate progress по этапам decode/Whisper chunks/diarization/finalisation.
- [x] 2026-11-07: alignment для короткой финальной ASR-реплики учитывает speaker-сегмент в последних 500 мс, чтобы растянутые Whisper timestamps не назначали хвост предыдущему speaker; добавлен unit test.
- [x] 2026-11-07: alignment теперь делит ASR-текст по diarization boundaries, если speaker меняется внутри одного Whisper-сегмента и в тексте достаточно слов; добавлен unit test для двух speaker внутри одной ASR-реплики.
- [x] 2026-11-07: короткие gaps между diarization-сегментами (до 1 секунды) теперь заполняются ближайшим speaker; `UNKNOWN` сохраняется для более длинных неподтверждённых участков.
- [x] 2026-11-07: кластеризация настроена на threshold `0.75`, `min_duration_on=0.4s`, `min_duration_off=0.3s`; default alignment smoothing увеличен до 1.2s. Unit tests и debug APK повторно собраны успешно. Новый APK ещё не установлен на телефон.
- [x] 2026-11-07: WorkManager-пайплайн собран, unit-тесты и `assembleDebug` прошли; APK установлен на Poco X7, приложение запущено без FATAL EXCEPTION в проверенном фрагменте logcat. Полная фоновая обработка на устройстве ещё не подтверждена.
- [x] 2026-11-07: Worker переведён в foreground-режим с notification channel и `dataSync` service type; итоговое время обработки сохраняется в `last_transcript.json` и отображается рядом с результатом. `testDebugUnitTest`/`assembleDebug` прошли, APK повторно установлен и запущен на Poco X7.
- [x] 2026-11-07: добавлено отдельное уведомление о завершении или ошибке обработки; успешное уведомление содержит итоговое время.
- [x] 2026-11-07: добавлено runtime-разрешение `POST_NOTIFICATIONS`, запрос при запуске и кнопка перехода в настройки уведомлений. На Poco X7 зафиксировано старое состояние `POST_NOTIFICATION: ignore`; обновление APK без очистки данных его не сбрасывает.
- [x] 2026-11-07: для diarization добавлена нормализация разреженных native speaker IDs по порядку появления и адаптивное сглаживание для 4+ обнаруженных speakers (`<=600 ms`); добавлен unit test, APK установлен на Poco X7. Повторная обработка предоставленной M4A ещё не выполнена.
- [ ] Попытка word-level alignment через Whisper token timestamps отозвана после регрессии качества на новых записях; текущая рабочая ветка использует segment-level alignment с нормализацией labels.
- [x] 2026-11-07: progress updates worker сериализованы через периодический monitor; исправлена гонка `setProgressAsync()` при завершении worker.
- [x] 2026-11-07: embedding-модель заменена с ERes2Net 3D-Speaker на официальный `wespeaker_en_voxceleb_resnet34.onnx`; metadata проверены, APK пересобран и установлен на Poco X7. Качество на пользовательских записях ещё требует повторного сравнения.
- [x] 2026-11-07: WeSpeaker откатирован после device-теста: при текущем FastClustering threshold результат схлопнулся в один speaker. ERes2Net возвращён как рабочий baseline; APK после отката требует сборки и установки.

## Зафиксированные входы от команды и ТЗ

Дата фиксации: 2026-11-06. Презентация хакатона и присланные изображения используются как рабочее ТЗ базового уровня.

- Основной пользовательский путь — импорт готовой аудиозаписи из памяти устройства и пакетная расшифровка.
- RealTime рассматривается как дополнительная опция; обязательность и границы режима не определены.
- Ограничение размера решения пока не задано; обсуждается Whisper Large вместо ранее упомянутой Whisper base.
- Тестовый телефон имеет около 4 ГБ оперативной памяти; пригодность Whisper Large и необходимость квантования должны быть измерены.
- Обязательные функции из слайда: Android, локальная LLM/модель, импорт записи, ASR, автоматическое разделение говорящих, временные метки, отображение и экспорт результата.
- Предварительный JSON-контракт: корневой `transcript` — массив объектов с `speaker_id` (string), `start_ms` (integer), `end_ms` (integer), `text` (string).
- Предварительная проверка: три заранее подготовленных диалога котиков, экспорт результатов, сравнение с эталонами, расчёт WER и RTF на одинаковых данных.
- Предварительные материалы также перечисляют дополнительные улучшения: предобработка, фильтрация пауз/неречевых фрагментов, устойчивость speaker labels, пунктуация, прогресс и переход к реплике при воспроизведении.
- Ожидаемые материалы сдачи: APK, исходный код, инструкция установки/использования, описание архитектуры и моделей, ограничения, собственные тесты и видео демонстрации.

## Ограничения, которые остаются уточняемыми

- Какая именно версия и квантование Whisper Large допустимы на 4 ГБ RAM?
- Какие форматы и максимальная длительность аудио проверяются?
- Обязательна ли diarization для успешного результата и сколько speakers в тестах?
- Что именно означает RealTime: микрофон, задержка, частичный текст и допустимый RTF?
- Как считаются WER и RTF, включая пунктуацию, регистр, паузы и speaker labels?
- Нужны ли TXT и другие форматы помимо указанного JSON?
- Какие Android-версии и ABI входят в проверку?
