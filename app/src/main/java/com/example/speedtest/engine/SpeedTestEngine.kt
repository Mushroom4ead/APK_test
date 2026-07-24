package com.example.speedtest.engine

import com.example.speedtest.data.TestPhase
import com.example.speedtest.data.TestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Движок замера скорости на базе публичного эндпоинта Cloudflare
 * (https://speed.cloudflare.com). Реализован на HttpURLConnection + корутинах,
 * без сторонних сетевых библиотек.
 *
 * Логика:
 *  - PING: несколько маленьких запросов, берём медиану задержки и джиттер.
 *  - DOWNLOAD: несколько параллельных потоков грузят большие чанки,
 *    суммарные байты за время дают скорость.
 *  - UPLOAD: несколько параллельных потоков отправляют данные на __up.
 */
class SpeedTestEngine {

    private companion object {
        const val DOWN_URL = "https://speed.cloudflare.com/__down"
        const val UP_URL = "https://speed.cloudflare.com/__up"

        const val PING_COUNT = 8
        const val DOWN_STREAMS = 4
        const val UP_STREAMS = 3
        const val DOWN_BYTES_PER_REQ = 25_000_000   // 25 МБ на запрос
        const val UP_BYTES_PER_REQ = 10_000_000      // 10 МБ на запрос
        const val MEASURE_MS = 8_000L                // длительность фазы download/upload
        const val CONNECT_TIMEOUT = 10_000
        const val READ_TIMEOUT = 15_000
    }

    /**
     * Запускает полный тест и публикует прогресс в переданный [state].
     * Должен вызываться из корутины; отмена корутины прерывает тест.
     */
    suspend fun run(state: MutableStateFlow<TestState>, networkType: String) {
        try {
            // ---- PING / JITTER ----
            state.value = state.value.copy(phase = TestPhase.PING, progress = 0f, liveMbps = 0.0)
            val (ping, jitter) = measurePing(state)
            state.value = state.value.copy(pingMs = ping, jitterMs = jitter, progress = 1f)

            // ---- DOWNLOAD ----
            state.value = state.value.copy(phase = TestPhase.DOWNLOAD, progress = 0f, liveMbps = 0.0)
            val down = measureTransfer(state, upload = false)
            state.value = state.value.copy(downloadMbps = down, liveMbps = down, progress = 1f)

            // ---- UPLOAD ----
            state.value = state.value.copy(phase = TestPhase.UPLOAD, progress = 0f, liveMbps = 0.0)
            val up = measureTransfer(state, upload = true)
            state.value = state.value.copy(uploadMbps = up, liveMbps = up, progress = 1f)

            state.value = state.value.copy(phase = TestPhase.DONE, liveMbps = 0.0)
        } catch (e: Exception) {
            state.value = state.value.copy(
                phase = TestPhase.ERROR,
                errorMessage = e.message ?: "Ошибка сети"
            )
        }
    }

    // ---------------------------------------------------------------------

    private suspend fun measurePing(state: MutableStateFlow<TestState>): Pair<Double, Double> {
        val samples = mutableListOf<Double>()
        withContext(Dispatchers.IO) {
            repeat(PING_COUNT) { i ->
                if (!coroutineContext.isActive) return@withContext
                val t = singlePing()
                if (t >= 0) samples.add(t)
                state.value = state.value.copy(
                    progress = (i + 1).toFloat() / PING_COUNT
                )
            }
        }
        if (samples.isEmpty()) return 0.0 to 0.0
        samples.sort()
        val median = samples[samples.size / 2]
        // джиттер — средняя разница между соседними замерами
        var jitterSum = 0.0
        for (k in 1 until samples.size) jitterSum += abs(samples[k] - samples[k - 1])
        val jitter = if (samples.size > 1) jitterSum / (samples.size - 1) else 0.0
        return median to jitter
    }

    private fun singlePing(): Double {
        return try {
            val url = URL("$DOWN_URL?bytes=0")
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.requestMethod = "GET"
            conn.useCaches = false
            val start = System.nanoTime()
            conn.connect()
            conn.inputStream.use { it.readBytes() }
            val elapsed = (System.nanoTime() - start) / 1_000_000.0
            conn.disconnect()
            elapsed
        } catch (e: Exception) {
            -1.0
        }
    }

    // ---------------------------------------------------------------------

    /** Общий метод для download/upload с несколькими параллельными потоками. */
    private suspend fun measureTransfer(
        state: MutableStateFlow<TestState>,
        upload: Boolean
    ): Double {
        val totalBytes = AtomicLong(0)
        val streams = if (upload) UP_STREAMS else DOWN_STREAMS
        val startNs = System.nanoTime()

        coroutineScope {
            // поток обновления UI (мгновенная скорость + прогресс)
            val ui = launch(Dispatchers.Default) {
                var lastBytes = 0L
                var lastNs = startNs
                while (isActive) {
                    val now = System.nanoTime()
                    val elapsedTotal = (now - startNs) / 1_000_000.0
                    if (elapsedTotal >= MEASURE_MS) break
                    val cur = totalBytes.get()
                    val dt = (now - lastNs) / 1_000_000_000.0
                    if (dt > 0.15) {
                        val instMbps = (cur - lastBytes) * 8.0 / 1_000_000.0 / dt
                        state.value = state.value.copy(
                            liveMbps = instMbps,
                            progress = (elapsedTotal / MEASURE_MS).toFloat().coerceIn(0f, 1f)
                        )
                        lastBytes = cur
                        lastNs = now
                    }
                    kotlinx.coroutines.delay(120)
                }
            }

            val workers = (0 until streams).map {
                launch(Dispatchers.IO) {
                    while (isActive &&
                        (System.nanoTime() - startNs) / 1_000_000.0 < MEASURE_MS
                    ) {
                        if (upload) uploadChunk(totalBytes, startNs)
                        else downloadChunk(totalBytes, startNs)
                    }
                }
            }
            workers.forEach { it.join() }
            ui.cancel()
        }

        val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
        val bytes = totalBytes.get()
        return if (elapsedSec > 0) bytes * 8.0 / 1_000_000.0 / elapsedSec else 0.0
    }

    private fun downloadChunk(counter: AtomicLong, startNs: Long) {
        try {
            val url = URL("$DOWN_URL?bytes=$DOWN_BYTES_PER_REQ")
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.requestMethod = "GET"
            conn.useCaches = false
            conn.inputStream.use { input ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if ((System.nanoTime() - startNs) / 1_000_000.0 >= MEASURE_MS) break
                    val n = input.read(buf)
                    if (n < 0) break
                    counter.addAndGet(n.toLong())
                }
            }
            conn.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun uploadChunk(counter: AtomicLong, startNs: Long) {
        try {
            val url = URL(UP_URL)
            val conn = url.openConnection() as HttpsURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.useCaches = false
            conn.setFixedLengthStreamingMode(UP_BYTES_PER_REQ)
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            val out: OutputStream = conn.outputStream
            val buf = ByteArray(64 * 1024)
            var sent = 0
            while (sent < UP_BYTES_PER_REQ) {
                if ((System.nanoTime() - startNs) / 1_000_000.0 >= MEASURE_MS) break
                val n = minOf(buf.size, UP_BYTES_PER_REQ - sent)
                out.write(buf, 0, n)
                sent += n
                counter.addAndGet(n.toLong())
            }
            out.flush()
            out.close()
            conn.responseCode // дождаться ответа
            conn.disconnect()
        } catch (_: Exception) {
        }
    }
}
