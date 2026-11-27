# Журнал решений

## 2026-11-27 · 0.1.0

- Один Android-модуль с логическими пакетами. Room, Coil, Media3, OkHttp и WorkManager подключаются с соответствующими функциями.
- Минимум Android 8.0 (API 26). Сборка и целевое поведение привязаны к Android 16 (API 36). JDK 17 и Gradle 8.13 соответствуют таблице AGP 8.13.2. Kotlin и Compose compiler используют общую версию 2.3.10. Библиотеки Compose согласованы через BOM. [AGP](https://developer.android.com/build/releases/agp-8-13-0-release-notes) · [Kotlin](https://kotlinlang.org/docs/gradle-configure-project.html) · [Compose](https://developer.android.com/develop/ui/compose/bom)
- Локальные настройки содержат режим темы. Системный режим используется по умолчанию. Акцент индиго и значок предварительные.
- Запуск приложения не запрашивает хранение, сеть или доступ к аккаунту. Автоматическое резервное копирование отключено. Для Android 12 и выше указаны исключения из облачного копирования и переноса данных между устройствами. Чувствительные настройки и ключи подписи исключены из репозитория.
- Gradle Wrapper использует опубликованные SHA-256 дистрибутива и JAR. Workflow работает с правом чтения кода и закреплёнными SHA действий. [Контрольные суммы Gradle](https://gradle.org/release-checksums/)

## 2026-11-27 · 0.2.0

- Медиатека показывает одну выбранную папку и читает вложенные папки при переходе. Доступ через SAF сохраняется только на чтение. Избранное привязано к дереву документов.
- Добавлены Room 2.8.5 со схемой версии 1, KSP 2.3.10, Coil 3.3.0 и Media3 1.9.4. [Room](https://developer.android.com/jetpack/androidx/releases/room) · [Media3](https://developer.android.com/jetpack/androidx/releases/media3) · [Coil](https://coil-kt.github.io/coil/videos/)
