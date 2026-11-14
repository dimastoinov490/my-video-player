package com.example.videoplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.player.DiagnosticManager
import com.example.videoplayer.ui.theme.GrayText
import com.example.videoplayer.ui.theme.LocalAppTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val coroutineScope = rememberCoroutineScope()

    var diagnosticResults by remember { mutableStateOf<List<DiagnosticManager.DiagnosticItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Запускаем диагностику автоматически при первом открытии экрана
    LaunchedEffect(Unit) {
        isScanning = true
        diagnosticResults = DiagnosticManager.runFullDiagnostics(context)
        isScanning = false
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Системная диагностика",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("diagnostics_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = theme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!isScanning) {
                                coroutineScope.launch {
                                    isScanning = true
                                    diagnosticResults = emptyList()
                                    diagnosticResults = DiagnosticManager.runFullDiagnostics(context)
                                    isScanning = false
                                }
                            }
                        },
                        modifier = Modifier.testTag("diagnostics_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Повторить",
                            tint = theme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background)
            )
        },
        containerColor = theme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Кнопка принудительного сканирования
            Button(
                onClick = {
                    coroutineScope.launch {
                        isScanning = true
                        diagnosticResults = emptyList()
                        diagnosticResults = DiagnosticManager.runFullDiagnostics(context)
                        isScanning = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("diagnostics_scan_button"),
                enabled = !isScanning,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сканирование компонентов...", color = Color.White)
                } else {
                    Text("Запустить полную диагностику", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Вывод результатов проверки
            diagnosticResults.forEach { item ->
                DiagnosticCard(item)
            }
        }
    }
}

@Composable
fun DiagnosticCard(item: DiagnosticManager.DiagnosticItem) {
    val theme = LocalAppTheme.current
    val (icon, color, statusText) = when (item.status) {
        DiagnosticManager.DiagnosticItem.Status.SUCCESS -> Triple(Icons.Default.CheckCircle, Color(0xFF4CAF50), "Норма")
        DiagnosticManager.DiagnosticItem.Status.WARNING -> Triple(Icons.Default.Warning, Color(0xFFFF9800), "Предупреждение")
        DiagnosticManager.DiagnosticItem.Status.ERROR -> Triple(Icons.Default.Error, Color(0xFFF44336), "Ошибка")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostic_card_${item.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = statusText,
                tint = color,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayText
                )
            }
        }
    }
}

@Composable
fun DiagnosticsEntryCard(
    onNavigateToDiagnostics: () -> Unit
) {
    val theme = LocalAppTheme.current
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more_diagnostics_card")
            .clickable(onClick = onNavigateToDiagnostics),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = theme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Healing,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Диагностика",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Проверка ошибок движков, RAM, CPU и сети",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayText
                )
            }
        }
    }
}
