package com.example.speedtest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedtest.data.HistoryStore
import com.example.speedtest.data.NetworkUtil
import com.example.speedtest.data.ServerConfig
import com.example.speedtest.data.Servers
import com.example.speedtest.data.TestPhase
import com.example.speedtest.data.TestResult
import com.example.speedtest.data.TestState
import com.example.speedtest.engine.SpeedTestEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SpeedTestViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = SpeedTestEngine()
    private val historyStore = HistoryStore(app)

    private val _state = MutableStateFlow(TestState())
    val state: StateFlow<TestState> = _state.asStateFlow()

    private val _history = MutableStateFlow(historyStore.load())
    val history: StateFlow<List<TestResult>> = _history.asStateFlow()

    // Выбранный сервер и введённый пользователем адрес «своего сервера»
    private val _server = MutableStateFlow(Servers.CLOUDFLARE)
    val server: StateFlow<ServerConfig> = _server.asStateFlow()

    private val _customUrl = MutableStateFlow("")
    val customUrl: StateFlow<String> = _customUrl.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean
        get() = _state.value.phase in setOf(
            TestPhase.PING, TestPhase.DOWNLOAD, TestPhase.UPLOAD
        )

    fun selectServer(server: ServerConfig) {
        if (!isRunning) _server.value = server
    }

    fun setCustomUrl(url: String) {
        _customUrl.value = url
    }

    /** Применить введённый адрес как «свой сервер». */
    fun applyCustomServer() {
        val url = _customUrl.value.trim()
        if (url.isNotEmpty() && !isRunning) {
            _server.value = Servers.custom(url)
        }
    }

    fun start() {
        if (isRunning) return
        val netType = NetworkUtil.currentType(getApplication())
        val srv = _server.value
        _state.value = TestState(
            phase = TestPhase.PING,
            networkType = netType,
            serverName = srv.name
        )
        job = viewModelScope.launch {
            engine.run(_state, netType, srv)
            if (_state.value.phase == TestPhase.DONE) {
                val s = _state.value
                val result = TestResult(
                    timestamp = System.currentTimeMillis(),
                    pingMs = s.pingMs,
                    jitterMs = s.jitterMs,
                    downloadMbps = s.downloadMbps,
                    uploadMbps = s.uploadMbps,
                    networkType = s.networkType
                )
                _history.value = historyStore.add(result)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = TestState(
            networkType = NetworkUtil.currentType(getApplication()),
            serverName = _server.value.name
        )
    }

    fun clearHistory() {
        historyStore.clear()
        _history.value = emptyList()
    }
}
