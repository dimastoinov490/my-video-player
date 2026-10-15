package com.example.videoplayer.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE) }
    var showBuffering by remember { mutableStateOf(prefs.getBoolean("show_buffering_status", false)) }

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = { Text("Настройки", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF121212))
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Показывать статус воспроизведения (Buffering и т.п.)",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = showBuffering,
                    onCheckedChange = { newValue ->
                        showBuffering = newValue
                        prefs.edit().putBoolean("show_buffering_status", newValue).apply()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFF8800),
                        checkedTrackColor = Color(0xFFFF8800).copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
}
