package com.example.speedtest.data

/** Фаза замера — для отображения в UI. */
enum class TestPhase {
    IDLE,       // ожидание
    PING,       // измеряем задержку
    DOWNLOAD,   // измеряем загрузку
    UPLOAD,     // измеряем отдачу
    DONE,       // завершено
    ERROR       // ошибка
}

/** Текущее состояние теста (наблюдается из UI через StateFlow). */
data class TestState(
    val phase: TestPhase = TestPhase.IDLE,
    val pingMs: Double = 0.0,
    val jitterMs: Double = 0.0,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    // Мгновенная скорость текущей фазы (для стрелки спидометра), Мбит/с
    val liveMbps: Double = 0.0,
    val progress: Float = 0f,          // 0..1 внутри текущей фазы
    val networkType: String = "—",
    val errorMessage: String? = null
)

/** Одна запись истории замеров. */
data class TestResult(
    val timestamp: Long,
    val pingMs: Double,
    val jitterMs: Double,
    val downloadMbps: Double,
    val uploadMbps: Double,
    val networkType: String
)
