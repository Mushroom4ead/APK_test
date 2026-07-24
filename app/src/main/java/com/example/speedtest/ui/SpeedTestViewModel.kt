package com.example.speedtest.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.speedtest.data.HistoryStore
import com.example.speedtest.data.NetworkUtil
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

    private var job: Job? = null

    val isRunning: Boolean
        get() = _state.value.phase in setOf(
            TestPhase.PING, TestPhase.DOWNLOAD, TestPhase.UPLOAD
        )

    fun start() {
        if (isRunning) return
        val netType = NetworkUtil.currentType(getApplication())
        _state.value = TestState(phase = TestPhase.PING, networkType = netType)
        job = viewModelScope.launch {
            engine.run(_state, netType)
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
        _state.value = TestState(networkType = NetworkUtil.currentType(getApplication()))
    }

    fun reset() {
        _state.value = TestState(networkType = NetworkUtil.currentType(getApplication()))
    }

    fun clearHistory() {
        historyStore.clear()
        _history.value = emptyList()
    }

    fun refreshNetworkType() {
        if (!isRunning) {
            _state.value = _state.value.copy(
                networkType = NetworkUtil.currentType(getApplication())
            )
        }
    }
}
