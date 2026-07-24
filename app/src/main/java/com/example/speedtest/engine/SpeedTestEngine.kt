package com.example.speedtest.engine

import com.example.speedtest.data.ServerConfig
import com.example.speedtest.data.ServerProtocol
import com.example.speedtest.data.TestPhase
import com.example.speedtest.data.TestState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * Движок замера скорости. Работает с несколькими типами серверов
 * (Cloudflare-совместимые и LibreSpeed) через HttpURLConnection + корутины.
 */
class SpeedTestEngine {

    private companion object {
        const val PING_COUNT = 8
        const val DOWN_STREAMS = 4
        const val UP_STREAMS = 3
        const val DOWN_BYTES_PER_REQ = 25_000_000   // 25 МБ (Cloudflare bytes=)
        const val DOWN_CKSIZE_MB = 100               // 100 МБ (LibreSpeed ckSize=)
        const val UP_BYTES_PER_REQ = 10_000_000
        const val MEASURE_MS = 8_000L
        const val CONNECT_TIMEOUT = 10_000
        const val READ_TIMEOUT = 15_000
        const val MAX_SAMPLES = 120                  // ограничение точек графика
    }

    suspend fun run(
        state: MutableStateFlow<TestState>,
        networkType: String,
        server: ServerConfig
    ) {
        try {
            state.value = state.value.copy(
                phase = TestPhase.PING, progress = 0f, liveMbps = 0.0,
                liveSamples = emptyList()
            )
            val (ping, jitter) = measurePing(state, server)
            state.value = state.value.copy(pingMs = ping, jitterMs = jitter, progress = 1f)

            state.value = state.value.copy(
                phase = TestPhase.DOWNLOAD, progress = 0f, liveMbps = 0.0,
                liveSamples = emptyList()
            )
            val down = measureTransfer(state, server, upload = false)
            state.value = state.value.copy(downloadMbps = down, liveMbps = down, progress = 1f)

            state.value = state.value.copy(
                phase = TestPhase.UPLOAD, progress = 0f, liveMbps = 0.0,
                liveSamples = emptyList()
            )
            val up = measureTransfer(state, server, upload = true)
            state.value = state.value.copy(uploadMbps = up, liveMbps = up, progress = 1f)

            state.value = state.value.copy(phase = TestPhase.DONE, liveMbps = 0.0)
        } catch (e: Exception) {
            state.value = state.value.copy(
                phase = TestPhase.ERROR,
                errorMessage = e.message ?: "Ошибка сети"
            )
        }
    }

    // ---- URL helpers -----------------------------------------------------

    private fun downloadUrl(server: ServerConfig): String = when (server.protocol) {
        ServerProtocol.CLOUDFLARE -> "${server.downloadUrl}?bytes=$DOWN_BYTES_PER_REQ"
        ServerProtocol.LIBRESPEED ->
            "${server.downloadUrl}?ckSize=$DOWN_CKSIZE_MB&r=${System.nanoTime()}"
    }

    private fun pingUrl(server: ServerConfig): String = when (server.protocol) {
        ServerProtocol.CLOUDFLARE -> "${server.downloadUrl}?bytes=0"
        ServerProtocol.LIBRESPEED -> "${server.uploadUrl}?r=${System.nanoTime()}"
    }

    // ---- PING ------------------------------------------------------------

    private suspend fun measurePing(
        state: MutableStateFlow<TestState>,
        server: ServerConfig
    ): Pair<Double, Double> {
        val samples = mutableListOf<Double>()
        withContext(Dispatchers.IO) {
            repeat(PING_COUNT) { i ->
                if (!coroutineContext.isActive) return@withContext
                val t = singlePing(server)
                if (t >= 0) samples.add(t)
                state.value = state.value.copy(progress = (i + 1).toFloat() / PING_COUNT)
            }
        }
        if (samples.isEmpty()) return 0.0 to 0.0
        samples.sort()
        val median = samples[samples.size / 2]
        var jitterSum = 0.0
        for (k in 1 until samples.size) jitterSum += abs(samples[k] - samples[k - 1])
        val jitter = if (samples.size > 1) jitterSum / (samples.size - 1) else 0.0
        return median to jitter
    }

    private fun singlePing(server: ServerConfig): Double {
        return try {
            val conn = URL(pingUrl(server)).openConnection() as HttpsURLConnection
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

    // ---- DOWNLOAD / UPLOAD ----------------------------------------------

    private suspend fun measureTransfer(
        state: MutableStateFlow<TestState>,
        server: ServerConfig,
        upload: Boolean
    ): Double {
        val totalBytes = AtomicLong(0)
        val streams = if (upload) UP_STREAMS else DOWN_STREAMS
        val startNs = System.nanoTime()
        val samples = ArrayList<Float>()

        coroutineScope {
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
                        if (samples.size < MAX_SAMPLES) samples.add(instMbps.toFloat())
                        state.value = state.value.copy(
                            liveMbps = instMbps,
                            liveSamples = ArrayList(samples),
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
                        if (upload) uploadChunk(server, totalBytes, startNs)
                        else downloadChunk(server, totalBytes, startNs)
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

    private fun downloadChunk(server: ServerConfig, counter: AtomicLong, startNs: Long) {
        try {
            val conn = URL(downloadUrl(server)).openConnection() as HttpsURLConnection
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

    private fun uploadChunk(server: ServerConfig, counter: AtomicLong, startNs: Long) {
        try {
            val conn = URL(server.uploadUrl).openConnection() as HttpsURLConnection
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
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {
        }
    }
}
