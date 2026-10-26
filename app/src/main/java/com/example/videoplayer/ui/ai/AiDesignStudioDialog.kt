package com.example.videoplayer.ui.ai

import android.content.Intent
import android.net.Uri
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
import com.example.videoplayer.ui.theme.*
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

    // Google Gemini in-app authorization state
    var isGeminiAuthorized by remember { mutableStateOf<Boolean>(ThemeManager.isGeminiAuthorized(context)) }
    var apiKeyInput by remember { mutableStateOf(ThemeManager.getSavedGeminiKey(context) ?: "") }
    var showAuthPanel by remember { mutableStateOf<Boolean>(!isGeminiAuthorized) }
    var isAuthorizing by remember { mutableStateOf(false) }
    var authFeedbackText by remember { mutableStateOf("") }

    var isGenerating by remember { mutableStateOf(false) }
    var generatedTheme by remember { mutableStateOf<AppCustomTheme?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    // Dynamic customization states for live preview & adjustment
    var activeControlStyle by remember { mutableStateOf(ThemeManager.activeTheme.playerControlStyle) }
    var activeButtonSize by remember { mutableStateOf(ThemeManager.activeTheme.buttonSize) }
    var activeCornerRadius by remember { mutableStateOf(ThemeManager.activeTheme.cornerRadiusDp.toFloat()) }

    val promptSuggestions = listOf(
        "Сакура аниме нежно-розовый",
        "Киберпанк неоновый бирюзовый",
        "Стимпанк винтажная медь и латунь",
        "Синтвейв 80-х ретро-пурпур",
        "Матрица кислотно-зеленый лайм",
        "Глубокий космос индиго и туманность",
        "Королевское золото и обсидиан",
        "Ледяной арктический сапфир",
        "Огненная лава и рубин",
        "OLED абсолютный черный монохром"
    )

    val activePreviewTheme = generatedTheme ?: selectedPreset

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(currentAppTheme.primary.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = currentAppTheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "ИИ Дизайн Студия",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Генерация оформления и кастомизация плеера",
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
                // Interactive Live Preview Mockup Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(activeCornerRadius.toInt().dp),
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
                                "ПРЕВЬЮ: ${activePreviewTheme.name.uppercase()}",
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

                        // Mini player mockup reflecting activeControlStyle & activeButtonSize
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(activeCornerRadius.toInt().dp),
                            color = activePreviewTheme.controlPanelBg ?: activePreviewTheme.surface,
                            border = BorderStroke(1.dp, activePreviewTheme.primary.copy(alpha = 0.35f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(activePreviewTheme.primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Default.PlayCircle,
                                                contentDescription = null,
                                                tint = activePreviewTheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Nexora Player", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${activeControlStyle.name} • ${activeButtonSize.name}",
                                                color = activePreviewTheme.secondary,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = activePreviewTheme.primary
                                    ) {
                                        Text(
                                            "4K HDR",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Seekbar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.45f)
                                            .fillMaxHeight()
                                            .background(activePreviewTheme.primary, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Control buttons according to size
                                val playBtnDp = when (activeButtonSize) {
                                    ButtonSizeOption.LARGE -> 44.dp
                                    ButtonSizeOption.STANDARD -> 36.dp
                                    ButtonSizeOption.COMPACT -> 30.dp
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(playBtnDp)
                                            .background(activePreviewTheme.primary, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }

                // Gemini Authorization Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = currentAppTheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (isGeminiAuthorized) Color(0xFF4CAF50).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAuthPanel = !showAuthPanel },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(
                                            if (isGeminiAuthorized) Color(0xFF4CAF50).copy(alpha = 0.2f) else currentAppTheme.primary.copy(alpha = 0.15f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isGeminiAuthorized) Icons.Default.CheckCircle else Icons.Default.VpnKey,
                                        contentDescription = null,
                                        tint = if (isGeminiAuthorized) Color(0xFF4CAF50) else currentAppTheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        if (isGeminiAuthorized) "🟢 Google Gemini AI авторизован" else "Авторизация Google Gemini AI",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (isGeminiAuthorized) "Онлайн-нейросеть генерирует стили" else "Встроенный оффлайн-ИИ активен",
                                        color = if (isGeminiAuthorized) Color(0xFF81C784) else Color.Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = if (showAuthPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = Color.LightGray,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (showAuthPanel) {
                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                "Авторизуйте Google Gemini в приложении для онлайн генерации уникальных макетов:",
                                color = Color.LightGray,
                                fontSize = 11.sp
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("Ключ Google AI (Gemini API Key)", fontSize = 11.sp) },
                                placeholder = { Text("AIzaSy...", color = Color.Gray, fontSize = 11.sp) },
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

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (apiKeyInput.isBlank()) {
                                            authFeedbackText = "Введите ключ API для авторизации"
                                            return@Button
                                        }
                                        isAuthorizing = true
                                        authFeedbackText = "Проверка ключа в Google AI..."
                                        coroutineScope.launch {
                                            val (success, message) = ThemeManager.verifyAndAuthorizeGemini(context, apiKeyInput.trim())
                                            isAuthorizing = false
                                            if (success) {
                                                isGeminiAuthorized = true
                                                authFeedbackText = "✅ $message"
                                                Toast.makeText(context, "Google Gemini успешно авторизован!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                isGeminiAuthorized = false
                                                authFeedbackText = "❌ $message"
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = currentAppTheme.primary),
                                    shape = RoundedCornerShape(10.dp),
                                    enabled = !isAuthorizing
                                ) {
                                    if (isAuthorizing) {
                                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Проверка...", color = Color.Black, fontSize = 10.sp)
                                    } else {
                                        Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Авторизовать в приложении", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/app/apikey"))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Не удалось открыть браузер: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                                ) {
                                    Text("Получить ключ", fontSize = 10.sp)
                                }
                            }

                            if (authFeedbackText.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = authFeedbackText,
                                    color = if (authFeedbackText.startsWith("✅")) Color(0xFF81C784) else Color(0xFFFF8A80),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // AI Generator Section
                Text(
                    "ГЕНЕРАЦИЯ СТИЛЯ ПО ОПИСАНИЮ",
                    color = currentAppTheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                OutlinedTextField(
                    value = aiPrompt,
                    onValueChange = { aiPrompt = it },
                    placeholder = { Text("Опишите желаемый стиль (например: Сакура аниме нежно-розовый)", color = Color.Gray, fontSize = 12.sp) },
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
                        statusMessage = if (isGeminiAuthorized) "Gemini генерирует оформление..." else "ИИ синтезирует уникальную палитру..."
                        coroutineScope.launch {
                            try {
                                val generated = ThemeManager.generateAiDesign(
                                    context = context,
                                    prompt = aiPrompt,
                                    customKey = apiKeyInput.ifBlank { null }
                                )
                                generatedTheme = generated
                                activeControlStyle = generated.playerControlStyle
                                activeButtonSize = generated.buttonSize
                                activeCornerRadius = generated.cornerRadiusDp.toFloat()
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
                        Text("ИИ генерирует стиль...", color = Color.Black, fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Сгенерировать стиль через ИИ", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }

                if (statusMessage.isNotBlank()) {
                    Text(statusMessage, color = currentAppTheme.secondary, fontSize = 12.sp)
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Manual Layout & Controls Customization
                Text(
                    "НАСТРОЙКА ПАНЕЛИ И КНОПОК ПЛЕЕРА",
                    color = currentAppTheme.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                // Style selector
                Text("Стиль панели управления:", color = Color.LightGray, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PlayerControlStyle.values().forEach { style ->
                        val isSelected = activeControlStyle == style
                        Surface(
                            onClick = { activeControlStyle = style },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) activePreviewTheme.primary else currentAppTheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) activePreviewTheme.primary else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = when (style) {
                                    PlayerControlStyle.MODERN_FLOAT -> "Плавающая"
                                    PlayerControlStyle.CINEMATIC_FULL -> "Кино"
                                    PlayerControlStyle.CLASSIC_VLC -> "Классика"
                                    PlayerControlStyle.MINIMAL_COMPACT -> "Мини"
                                },
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Button size selector
                Text("Размер кнопок воспроизведения:", color = Color.LightGray, fontSize = 11.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ButtonSizeOption.values().forEach { sizeOpt ->
                        val isSelected = activeButtonSize == sizeOpt
                        Surface(
                            onClick = { activeButtonSize = sizeOpt },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) activePreviewTheme.primary else currentAppTheme.surface,
                            border = BorderStroke(1.dp, if (isSelected) activePreviewTheme.primary else Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = when (sizeOpt) {
                                    ButtonSizeOption.COMPACT -> "Компактные"
                                    ButtonSizeOption.STANDARD -> "Стандарт"
                                    ButtonSizeOption.LARGE -> "Крупные"
                                },
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(vertical = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                // Corner radius slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Скругление углов карточек:", color = Color.LightGray, fontSize = 11.sp)
                    Text("${activeCornerRadius.toInt()} dp", color = activePreviewTheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = activeCornerRadius,
                    onValueChange = { activeCornerRadius = it },
                    valueRange = 0f..32f,
                    steps = 15,
                    colors = SliderDefaults.colors(
                        thumbColor = activePreviewTheme.primary,
                        activeTrackColor = activePreviewTheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )

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
                                activeControlStyle = preset.playerControlStyle
                                activeButtonSize = preset.buttonSize
                                activeCornerRadius = preset.cornerRadiusDp.toFloat()
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
                    val finalTheme = activePreviewTheme
                    ThemeManager.saveAndApplyAiTheme(
                        context = context,
                        name = finalTheme.name,
                        description = finalTheme.description,
                        primary = finalTheme.primary,
                        secondary = finalTheme.secondary,
                        background = finalTheme.background,
                        surface = finalTheme.surface,
                        playerControlStyle = activeControlStyle,
                        buttonSize = activeButtonSize,
                        cornerRadiusDp = activeCornerRadius.toInt(),
                        controlPanelBg = finalTheme.controlPanelBg
                    )
                    Toast.makeText(context, "Оформление «${finalTheme.name}» применено!", Toast.LENGTH_SHORT).show()
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
