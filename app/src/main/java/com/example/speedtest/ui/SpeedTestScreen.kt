package com.example.speedtest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.speedtest.data.ServerConfig
import com.example.speedtest.data.Servers
import com.example.speedtest.data.TestPhase
import com.example.speedtest.data.TestResult
import com.example.speedtest.data.TestState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AccentDownload = Color(0xFF4F8CFF)
private val AccentUpload = Color(0xFF34D399)
private val AccentPing = Color(0xFFFBBF24)
private val BgColor = Color(0xFF14171D)
private val CardColor = Color(0xFF1D222B)

@Composable
fun SpeedTestScreen(viewModel: SpeedTestViewModel) {
    val state by viewModel.state.collectAsState()
    val history by viewModel.history.collectAsState()
    val server by viewModel.server.collectAsState()
    val customUrl by viewModel.customUrl.collectAsState()
    val autoMode by viewModel.autoMode.collectAsState()

    val running = state.phase in setOf(
        TestPhase.SELECTING, TestPhase.PING, TestPhase.DOWNLOAD, TestPhase.UPLOAD
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgColor)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { Header(state.networkType) }
            item {
                ServerSelector(
                    label = if (autoMode) "Авто — ближайший по пингу" else server.name,
                    server = server,
                    autoMode = autoMode,
                    customUrl = customUrl,
                    enabled = !running,
                    onSelect = viewModel::selectServer,
                    onSelectAuto = viewModel::selectAuto,
                    onCustomChange = viewModel::setCustomUrl,
                    onApplyCustom = viewModel::applyCustomServer
                )
            }
            item { GaugeSection(state) }
            item { LiveGraphSection(state) }
            item { MetricsRow(state) }
            item { ControlButton(state, viewModel) }
            item {
                state.errorMessage?.let {
                    Text(
                        text = "Ошибка: $it",
                        color = Color(0xFFFF6B6B),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
            item { HistoryHeader(history.isNotEmpty(), viewModel) }
            items(history) { r -> HistoryItem(r) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Header(networkType: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Speed Test", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, null, tint = AccentDownload, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(networkType, fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerSelector(
    label: String,
    server: ServerConfig,
    autoMode: Boolean,
    customUrl: String,
    enabled: Boolean,
    onSelect: (ServerConfig) -> Unit,
    onSelectAuto: () -> Unit,
    onCustomChange: (String) -> Unit,
    onApplyCustom: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var showCustom by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { if (enabled) expanded = !expanded }
        ) {
            OutlinedTextField(
                value = label,
                onValueChange = {},
                readOnly = true,
                enabled = enabled,
                label = { Text("Сервер") },
                leadingIcon = { Icon(Icons.Filled.Dns, null, tint = AccentDownload) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("Авто — ближайший по пингу") },
                    onClick = { onSelectAuto(); showCustom = false; expanded = false }
                )
                Servers.presets.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.name) },
                        onClick = { onSelect(s); showCustom = false; expanded = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Свой сервер…") },
                    onClick = { showCustom = true; expanded = false }
                )
            }
        }

        if (showCustom || (server.custom && !autoMode)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customUrl,
                    onValueChange = onCustomChange,
                    enabled = enabled,
                    singleLine = true,
                    label = { Text("URL сервера (например https://my.host)") },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onApplyCustom, enabled = enabled) { Text("OK") }
            }
            Text(
                "Ожидается Cloudflare-совместимый сервер (пути /__down и /__up).",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun GaugeSection(state: TestState) {
    val (value, accent, caption) = when (state.phase) {
        TestPhase.DOWNLOAD -> Triple(state.liveMbps, AccentDownload, "Загрузка…")
        TestPhase.UPLOAD -> Triple(state.liveMbps, AccentUpload, "Отдача…")
        TestPhase.PING -> Triple(0.0, AccentPing, "Пинг…")
        TestPhase.SELECTING -> Triple(0.0, AccentPing, "Выбор сервера…")
        TestPhase.DONE -> Triple(state.downloadMbps, AccentDownload, "Готово")
        else -> Triple(0.0, AccentDownload, "")
    }
    SpeedGauge(
        value = value,
        accent = accent,
        caption = caption,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
    )
}

@Composable
private fun LiveGraphSection(state: TestState) {
    val active = state.phase == TestPhase.DOWNLOAD || state.phase == TestPhase.UPLOAD
    if (!active || state.liveSamples.size < 2) return
    val accent = if (state.phase == TestPhase.UPLOAD) AccentUpload else AccentDownload
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = if (state.phase == TestPhase.UPLOAD) "Отдача, Мбит/с" else "Загрузка, Мбит/с",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
            LiveGraph(
                samples = state.liveSamples,
                accent = accent,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
        }
    }
}

@Composable
private fun MetricsRow(state: TestState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MetricCard("Пинг", fmt(state.pingMs, "мс"), AccentPing, Modifier.weight(1f))
        MetricCard("Загрузка", fmt(state.downloadMbps, ""), AccentDownload, Modifier.weight(1f))
        MetricCard("Отдача", fmt(state.uploadMbps, ""), AccentUpload, Modifier.weight(1f))
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Джиттер: ${fmt(state.jitterMs, "мс")}",
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = accent)
        }
    }
}

@Composable
private fun ControlButton(state: TestState, viewModel: SpeedTestViewModel) {
    val running = state.phase in setOf(
        TestPhase.SELECTING, TestPhase.PING, TestPhase.DOWNLOAD, TestPhase.UPLOAD
    )
    Button(
        onClick = { if (running) viewModel.cancel() else viewModel.start() },
        modifier = Modifier.padding(top = 12.dp).size(width = 200.dp, height = 56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) Color(0xFF3A3F4B) else AccentDownload
        )
    ) {
        Text(
            text = if (running) "Остановить" else
                if (state.phase == TestPhase.DONE) "Повторить" else "Начать тест",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun HistoryHeader(hasItems: Boolean, viewModel: SpeedTestViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("История", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        if (hasItems) {
            OutlinedButton(onClick = { viewModel.clearHistory() }) {
                Icon(Icons.Filled.DeleteOutline, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Очистить", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun HistoryItem(r: TestResult) {
    val df = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = CardColor),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(df.format(Date(r.timestamp)), fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f))
                Text(r.networkType, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                MiniMetric("↓", String.format("%.1f", r.downloadMbps), AccentDownload)
                MiniMetric("↑", String.format("%.1f", r.uploadMbps), AccentUpload)
                MiniMetric("ms", String.format("%.0f", r.pingMs), AccentPing)
            }
        }
    }
}

@Composable
private fun MiniMetric(prefix: String, value: String, accent: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = accent)
        Text(prefix, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
    }
}

private fun fmt(v: Double, unit: String): String {
    if (v <= 0.0) return "—"
    return if (unit.isEmpty()) String.format("%.1f", v)
    else String.format("%.0f %s", v, unit)
}
