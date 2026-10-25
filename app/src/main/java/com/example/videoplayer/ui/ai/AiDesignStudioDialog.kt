package com.example.videoplayer.ui.ai

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.ui.theme.AppCustomTheme
import com.example.videoplayer.ui.theme.LocalAppTheme
import com.example.videoplayer.ui.theme.ThemeManager
import com.example.videoplayer.ui.theme.ThemePresets
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiDesignStudioDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentAppTheme = LocalAppTheme.current

    var selectedPreset by remember { mutableStateOf(ThemeManager.activeTheme) }
    var aiPrompt by remember { mutableStateOf("") }
    var apiKeyInput by remember { mutableStateOf("") }
    var showApiKeyInput by remember { mutableStateOf(false) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatedTheme by remember { mutableStateOf<AppCustomTheme?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    val promptSuggestions = listOf(
        "Киберпанк неон с бирюзовым",
        "Синтвейв 80-х фиолетовый",
        "Матрица кислотный лайм",
        "Глубокий космос индиго",
        "Королевское золото и обсидиан",
        "Ледяной арктический сапфир",
        "Огненная лава и рубин",
        "Минималистичный матовый графит"
    )

    val activePreviewTheme = generatedTheme ?: selectedPreset

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(currentAppTheme.primary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = currentAppTheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "ИИ Дизайн Студия",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Генерация оформления и тем плеера",
                        color = currentAppTheme.secondary,
                        fontSize = 11.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Live preview card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = activePreviewTheme.background,
                    border = BorderStroke(1.5.dp, activePreviewTheme.cardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ПРЕВЬЮ: ${activePreviewTheme.name}",
                                color = activePreviewTheme.primary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Box(modifier = Modifier.size(12.dp).background(activePreviewTheme.primary, CircleShape))
                                Box(modifier = Modifier.size(12.dp).background(activePreviewTheme.secondary, CircleShape))
                                Box(modifier = Modifier.size(12.dp).background(activePreviewTheme.surface, CircleShape))
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Mini player mockup
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = activePreviewTheme.surface,
                            border = BorderStroke(1.dp, activePreviewTheme.primary.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(activePreviewTheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = activePreviewTheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("Nexora Player UI", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                        Text("Активная цветовая схема", color = Color.Gray, fontSize = 10.sp)
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = activePreviewTheme.primary
                                ) {
                                    Text(
                                        "1080p",
                                        color = Color.Black,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // AI Generator Section
                Text(
                    "ГЕНЕРАЦИЯ С ПОМОЩЬЮ ИИ",
                    color = currentAppTheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = aiPrompt,
                    onValueChange = { aiPrompt = it },
                    placeholder = { Text("Опишите желаемый стиль (например: неоновый киберпанк фиолетовый)", color = Color.Gray, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = currentAppTheme.primary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    maxLines = 3
                )

                // Suggestion chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    promptSuggestions.forEach { suggestion ->
                        Surface(
                            onClick = { aiPrompt = suggestion },
                            shape = RoundedCornerShape(16.dp),
                            color = currentAppTheme.surface,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                        ) {
                            Text(
                                suggestion,
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                // AI Generation action button
                Button(
                    onClick = {
                        if (aiPrompt.isBlank()) {
                            statusMessage = "Введите описание для ИИ генерации"
                            return@Button
                        }
                        isGenerating = true
                        statusMessage = "Нейросеть генерирует цветовую палитру..."
                        coroutineScope.launch {
                            try {
                                val generated = ThemeManager.generateAiDesign(
                                    prompt = aiPrompt,
                                    apiKey = apiKeyInput.ifBlank { null }
                                )
                                generatedTheme = generated
                                statusMessage = "Стиль «${generated.name}» успешно сгенерирован!"
                            } catch (e: Exception) {
                                statusMessage = "Ошибка генерации: ${e.message}"
                            } finally {
                                isGenerating = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = currentAppTheme.primary),
                    enabled = !isGenerating
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ИИ генерирует...", color = Color.Black, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сгенерировать стиль через ИИ", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, color = currentAppTheme.secondary, fontSize = 12.sp)
                }

                // API Key Toggle for external models
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showApiKeyInput = !showApiKeyInput },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Подключить свой API ключ (Gemini / ChatGPT)",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = if (showApiKeyInput) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }

                if (showApiKeyInput) {
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("API Key (опционально, есть встроенная ИИ модель)", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = currentAppTheme.primary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Ready Presets Section
                Text(
                    "ГОТОВЫЕ ДИЗАЙНЕРСКИЕ ПРЕСЕТЫ",
                    color = currentAppTheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(ThemePresets.allPresets) { preset ->
                        val isSel = activePreviewTheme.id == preset.id && generatedTheme == null
                        Surface(
                            onClick = {
                                selectedPreset = preset
                                generatedTheme = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = preset.surface,
                            border = BorderStroke(
                                if (isSel) 2.dp else 1.dp,
                                if (isSel) preset.primary else Color.White.copy(alpha = 0.1f)
                            ),
                            modifier = Modifier.width(130.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(14.dp).background(preset.primary, CircleShape))
                                    if (isSel) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = preset.primary, modifier = Modifier.size(16.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(preset.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (generatedTheme != null) {
                        ThemeManager.saveAndApplyAiTheme(
                            context = context,
                            name = generatedTheme!!.name,
                            description = generatedTheme!!.description,
                            primary = generatedTheme!!.primary,
                            secondary = generatedTheme!!.secondary,
                            background = generatedTheme!!.background,
                            surface = generatedTheme!!.surface
                        )
                    } else {
                        ThemeManager.applyTheme(context, selectedPreset)
                    }
                    Toast.makeText(context, "Дизайн успешно применен!", Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = activePreviewTheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Применить оформление", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF181818),
        shape = RoundedCornerShape(22.dp)
    )
}
