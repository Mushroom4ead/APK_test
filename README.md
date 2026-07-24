# SpeedTest — Android-приложение для замера скорости интернета

Нативное Android-приложение (Kotlin + Jetpack Compose), измеряет скорость
загрузки/отдачи, пинг и джиттер — по типу Speedtest. Замеры выполняются через
публичный эндпоинт **Cloudflare** (`speed.cloudflare.com`), поэтому свой сервер
разворачивать не нужно.

## Возможности

- Замер **скорости загрузки** (download) в несколько параллельных потоков
- Замер **скорости отдачи** (upload)
- **Пинг** (медиана) и **джиттер**
- Определение **типа сети** (Wi-Fi / мобильная / Ethernet / VPN)
- Анимированный **спидометр** со стрелкой (логарифмическая шкала до 1 Гбит/с)
- **Выбор сервера**: Cloudflare (глобально), LibreSpeed (Лондон/Нью-Йорк) или свой сервер
- **График скорости в реальном времени** во время загрузки и отдачи
- **История** последних 50 замеров (хранится локально на устройстве)
- Тёмная тема

Минимальная версия Android: **7.0 (API 24)**.

---

## Как получить APK

Собрать APK локально в этой среде было нельзя (нет доступа к серверам Google/
Android SDK), поэтому есть два удобных пути.

### Вариант A — без установки чего-либо: сборка на GitHub (рекомендуется)

В проект уже добавлен workflow `.github/workflows/build-apk.yml`, который
собирает APK на серверах GitHub.

1. Создайте новый репозиторий на GitHub (можно приватный).
2. Загрузите туда содержимое этой папки. Через веб-интерфейс: кнопка
   **Add file → Upload files**, перетащите все файлы. Либо через терминал:
   ```bash
   cd SpeedTestApp
   git init
   git add .
   git commit -m "SpeedTest app"
   git branch -M main
   git remote add origin https://github.com/ВАШ_ЛОГИН/ИМЯ_РЕПО.git
   git push -u origin main
   ```
3. Откройте вкладку **Actions** в репозитории. Сборка запустится
   автоматически (2–4 минуты). Если нет — нажмите **Build APK → Run workflow**.
4. Когда сборка станет зелёной, откройте её и внизу в разделе **Artifacts**
   скачайте `speedtest-debug-apk`. Внутри — файл `app-debug.apk`.

### Вариант B — сборка в Android Studio

1. Установите [Android Studio](https://developer.android.com/studio).
2. **File → Open** → выберите папку `SpeedTestApp`. Дождитесь синхронизации
   Gradle (скачает SDK и зависимости автоматически).
3. Меню **Build → Build App Bundle(s)/APK(s) → Build APK(s)**.
4. Готовый файл: `app/build/outputs/apk/debug/app-debug.apk`.

### Вариант C — сборка из командной строки

Нужны JDK 17 и Android SDK (переменная `ANDROID_HOME` или файл
`local.properties` со строкой `sdk.dir=/путь/к/Android/Sdk`).

```bash
cd SpeedTestApp
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Установка APK на телефон

1. Перекиньте `app-debug.apk` на телефон (кабель, Telegram себе, облако и т.п.).
2. Откройте файл в проводнике телефона.
3. Android попросит разрешить установку из этого источника — разрешите.
4. Установите и запустите. При первом тесте приложение запросит доступ в
   интернет (обычно ничего дополнительно подтверждать не нужно).

> Это **debug**-сборка — она для личного использования и тестирования.
> Для публикации в Google Play нужна подписанная **release**-сборка
> (Build → Generate Signed Bundle / APK в Android Studio).

---

## Структура проекта

```
SpeedTestApp/
├─ app/
│  ├─ build.gradle.kts            # зависимости и конфиг модуля
│  └─ src/main/
│     ├─ AndroidManifest.xml
│     ├─ java/com/example/speedtest/
│     │  ├─ MainActivity.kt        # точка входа, тема
│     │  ├─ data/
│     │  │  ├─ Models.kt           # модели состояния и результата
│     │  │  ├─ NetworkUtil.kt      # определение типа сети
│     │  │  └─ HistoryStore.kt     # локальная история (SharedPreferences)
│     │  ├─ engine/
│     │  │  └─ SpeedTestEngine.kt  # логика замеров (Cloudflare)
│     │  └─ ui/
│     │     ├─ SpeedTestViewModel.kt
│     │     ├─ SpeedTestScreen.kt  # главный экран
│     │     └─ Gauge.kt            # спидометр (Canvas)
│     └─ res/…
├─ .github/workflows/build-apk.yml # авто-сборка APK на GitHub
├─ build.gradle.kts / settings.gradle.kts / gradle.properties
└─ gradlew, gradle/wrapper/…       # Gradle wrapper
```

## Как работают замеры

- **Пинг** — 8 коротких запросов к `__down?bytes=0`, берётся медиана времени
  отклика; джиттер = средняя разница между соседними замерами.
- **Download** — 4 параллельных потока в течение ~8 секунд качают чанки по
  25 МБ; суммарные байты / время = скорость в Мбит/с.
- **Upload** — 3 параллельных потока отправляют данные на `__up`.

Значения можно поменять в начале файла `SpeedTestEngine.kt`
(константы `DOWN_STREAMS`, `MEASURE_MS` и т.д.).

## Возможные доработки

- Выбор сервера (сейчас всегда Cloudflare)
- Экспорт истории в CSV
- Виджет на рабочий стол
- График скорости в реальном времени
