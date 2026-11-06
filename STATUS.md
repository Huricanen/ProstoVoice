# STATUS

Дата проверки: 2026-11-06 (Europe/Moscow).

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
- [ ] Whisper.cpp и sherpa-onnx.
- [ ] Модели и лицензии на их распространение.
- [ ] Тесты, CI, сборка APK и проверки устройства.
- [ ] Точное ТЗ и окончательные продуктовые ограничения.
- [ ] Проверка Gradle build: отсутствуют Gradle wrapper и JDK 17+.
- [ ] Native toolchain: NDK и CMake не найдены.
- [ ] Запуск JUnit/Compose tests.

## Ожидаемое событие

ТЗ ожидается примерно через 4–5 часов от момента планирования. До его получения подготовка должна оставаться обратимой и не привязывать проект к неподтверждённой архитектуре.
