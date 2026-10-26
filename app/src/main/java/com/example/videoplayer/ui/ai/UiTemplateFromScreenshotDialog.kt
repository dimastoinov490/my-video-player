package com.example.videoplayer.ui.ai

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.videoplayer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UiTemplateFromScreenshotDialog(
    mainScreenshotUri: Uri? = null,
    playerScreenshotUri: Uri? = null,
    onRequestPickMain: () -> Unit = {},
    onRequestPickPlayer: () -> Unit = {},
    onClearMain: () -> Unit = {},
    onClearPlayer: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val activeTheme = LocalAppTheme.current

    var mainScreenBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var playerScreenBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Synchronize bitmaps from incoming URIs
    LaunchedEffect(mainScreenshotUri) {
        if (mainScreenshotUri != null) {
            mainScreenBitmap = loadBitmapFromUri(context, mainScreenshotUri)
        } else {
            mainScreenBitmap = null
        }
    }

    LaunchedEffect(playerScreenshotUri) {
        if (playerScreenshotUri != null) {
            playerScreenBitmap = loadBitmapFromUri(context, playerScreenshotUri)
        } else {
            playerScreenBitmap = null
        }
    }

    // Google Gemini in-app authorization state
    var isGeminiAuthorized by remember { mutableStateOf<Boolean>(ThemeManager.isGeminiAuthorized(context)) }
    var apiKeyInput by remember { mutableStateOf(ThemeManager.getSavedGeminiKey(context) ?: "") }
    var showAuthPanel by remember { mutableStateOf<Boolean>(!isGeminiAuthorized) }
    var isAuthorizing by remember { mutableStateOf(false) }
    var authFeedbackText by remember { mutableStateOf("") }

    var isAnalyzing by remember { mutableStateOf(false) }
    var analysisResult by remember { mutableStateOf<ScreenshotAnalysisResult?>(null) }
    var statusText by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = activeTheme.background,
            border = BorderStroke(1.5.dp, activeTheme.cardBorder),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(activeTheme.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = activeTheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                "UI Дизайн по скриншоту",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Точная подгонка макета плеера по образцу",
                                color = activeTheme.secondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.1f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        "Загрузите скриншоты желаемого интерфейса — механизм распознает цветовую палитру, расположение кнопок и скругления, и сделает плеер в точности как на картинке.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Screenshot 1: Main Screen
                    ScreenshotUploadCard(
                        title = "Скриншот 1: Главный экран",
                        subtitle = "Библиотека, каталог или карточки фильмов",
                        bitmap = mainScreenBitmap,
                        onPickClick = onRequestPickMain,
                        onClearClick = {
                            mainScreenBitmap = null
                            onClearMain()
                        },
                        theme = activeTheme
                    )

                    // Screenshot 2: Player Screen
                    ScreenshotUploadCard(
                        title = "Скриншот 2: Экран плеера",
                        subtitle = "Панель управления видео, таймлайн, кнопки воспроизведения и перемотки",
                        bitmap = playerScreenBitmap,
                        onPickClick = onRequestPickPlayer,
                        onClearClick = {
                            playerScreenBitmap = null
                            onClearPlayer()
                        },
                        theme = activeTheme
                    )

                    // Google Gemini in-app authorization card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = activeTheme.surface,
                        border = BorderStroke(
                            1.dp,
                            if (isGeminiAuthorized) Color(0xFF4CAF50).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.12f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
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
                                            .size(32.dp)
                                            .background(
                                                if (isGeminiAuthorized) Color(0xFF4CAF50).copy(alpha = 0.2f) else activeTheme.primary.copy(alpha = 0.15f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isGeminiAuthorized) Icons.Default.CheckCircle else Icons.Default.VpnKey,
                                            contentDescription = null,
                                            tint = if (isGeminiAuthorized) Color(0xFF4CAF50) else activeTheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            if (isGeminiAuthorized) "🟢 Google Gemini AI авторизован" else "Авторизация Google Gemini AI",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            if (isGeminiAuthorized) "Онлайн-нейросеть активна для анализа" else "Локальный ИИ активен • Нажмите для подключения Gemini",
                                            color = if (isGeminiAuthorized) Color(0xFF81C784) else Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Icon(
                                    imageVector = if (showAuthPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = Color.LightGray,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (showAuthPanel) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    "Авторизуйте Google Gemini в приложении, чтобы нейросеть анализировала скриншоты напрямую через официальный облачный AI:",
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    label = { Text("Ключ Google AI (Gemini API Key)", fontSize = 11.sp) },
                                    placeholder = { Text("AIzaSy...", color = Color.Gray, fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = activeTheme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                Spacer(modifier = Modifier.height(10.dp))

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
                                        colors = ButtonDefaults.buttonColors(containerColor = activeTheme.primary),
                                        shape = RoundedCornerShape(10.dp),
                                        enabled = !isAuthorizing
                                    ) {
                                        if (isAuthorizing) {
                                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Проверка...", color = Color.Black, fontSize = 11.sp)
                                        } else {
                                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Авторизовать в приложении", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        Text("Получить ключ", fontSize = 11.sp)
                                    }
                                }

                                if (authFeedbackText.isNotBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = authFeedbackText,
                                        color = if (authFeedbackText.startsWith("✅")) Color(0xFF81C784) else Color(0xFFFF8A80),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }

                    // Analysis Action Button
                    Button(
                        onClick = {
                            if (mainScreenBitmap == null && playerScreenBitmap == null) {
                                Toast.makeText(context, "Выберите хотя бы один скриншот", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isAnalyzing = true
                            statusText = if (isGeminiAuthorized) "Gemini Vision анализирует скриншоты..." else "Анализ скриншотов и извлечение палитры..."
                            coroutineScope.launch {
                                try {
                                    val result = ScreenshotThemeExtractor.extractThemeFromScreenshots(
                                        context = context,
                                        mainScreenBitmap = mainScreenBitmap,
                                        playerScreenBitmap = playerScreenBitmap,
                                        customApiKey = apiKeyInput.ifBlank { null }
                                    )
                                    analysisResult = result
                                    statusText = "Стиль «${result.name}» успешно распознан!"
                                } catch (e: Exception) {
                                    statusText = "Ошибка анализа: ${e.message}"
                                } finally {
                                    isAnalyzing = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = activeTheme.primary),
                        enabled = !isAnalyzing && (mainScreenBitmap != null || playerScreenBitmap != null)
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Распознавание макета...", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Анализировать макет по скриншоту",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }

                    if (statusText.isNotEmpty()) {
                        Text(
                            text = statusText,
                            color = activeTheme.secondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Result and Live Preview Card
                    analysisResult?.let { res ->
                        AnalysisPreviewCard(
                            result = res,
                            onApply = {
                                ThemeManager.saveAndApplyAiTheme(
                                    context = context,
                                    name = res.name,
                                    description = res.description,
                                    primary = res.theme.primary,
                                    secondary = res.theme.secondary,
                                    background = res.theme.background,
                                    surface = res.theme.surface,
                                    playerControlStyle = res.playerControlStyle,
                                    buttonSize = res.buttonSize,
                                    cornerRadiusDp = res.cornerRadiusDp,
                                    controlPanelBg = res.theme.controlPanelBg
                                )
                                Toast.makeText(context, "Оформление «${res.name}» успешно применено к плееру и экранам!", Toast.LENGTH_LONG).show()
                                onDismiss()
                            },
                            currentTheme = activeTheme
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenshotUploadCard(
    title: String,
    subtitle: String,
    bitmap: Bitmap?,
    onPickClick: () -> Unit,
    onClearClick: () -> Unit,
    theme: AppCustomTheme
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = theme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (bitmap != null) theme.primary else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(subtitle, color = Color.Gray, fontSize = 11.sp, maxLines = 2)
                }

                if (bitmap != null) {
                    IconButton(onClick = onClearClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Удалить",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onPickClick)
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.7f)
                    ) {
                        Text(
                            "Нажмите для замены",
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .clickable(onClick = onPickClick),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = theme.secondary,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Выбрать скриншот из галереи",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalysisPreviewCard(
    result: ScreenshotAnalysisResult,
    onApply: () -> Unit,
    currentTheme: AppCustomTheme
) {
    val theme = result.theme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = theme.background,
        border = BorderStroke(2.dp, theme.primary),
        shadowElevation = 12.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Source badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = theme.primary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f))
            ) {
                Text(
                    text = "ИСТОЧНИК: ${result.analysisSource.uppercase()}",
                    color = theme.primary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = result.name,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = result.description,
                color = Color.LightGray,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Color Swatches
            Text(
                "ИЗВЛЕЧЕННАЯ ЦВЕТОВАЯ ПАЛИТРА",
                color = theme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorPill(label = "Основной", color = theme.primary, hex = result.primaryHex, modifier = Modifier.weight(1f))
                ColorPill(label = "Второй", color = theme.secondary, hex = result.secondaryHex, modifier = Modifier.weight(1f))
                ColorPill(label = "Фон", color = theme.background, hex = result.backgroundHex, modifier = Modifier.weight(1f))
                ColorPill(label = "Панель", color = theme.surface, hex = result.surfaceHex, modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Detected layout properties
            Text(
                "ПАРАМЕТРЫ РАСПОЛОЖЕНИЯ И КНОПОК",
                color = theme.primary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Стиль плеера", color = Color.Gray, fontSize = 9.sp)
                        Text(
                            when (result.playerControlStyle) {
                                PlayerControlStyle.MODERN_FLOAT -> "Плавающая панель"
                                PlayerControlStyle.CLASSIC_VLC -> "Классика VLC"
                                PlayerControlStyle.MINIMAL_COMPACT -> "Минимализм"
                                PlayerControlStyle.CINEMATIC_FULL -> "Кинематографичный"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Кнопки", color = Color.Gray, fontSize = 9.sp)
                        Text(
                            when (result.buttonSize) {
                                ButtonSizeOption.LARGE -> "Крупные"
                                ButtonSizeOption.STANDARD -> "Стандарт"
                                ButtonSizeOption.COMPACT -> "Компакт"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.surface,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Скругление", color = Color.Gray, fontSize = 9.sp)
                        Text("${result.cornerRadiusDp} dp", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Mini player mockup preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(result.cornerRadiusDp.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Пример панели плеера", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("04:12 / 12:45", color = theme.secondary, fontSize = 10.sp)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Simulated seekbar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.35f)
                                .fillMaxHeight()
                                .background(theme.primary, CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(
                                    when (result.buttonSize) {
                                        ButtonSizeOption.LARGE -> 48.dp
                                        ButtonSizeOption.STANDARD -> 40.dp
                                        ButtonSizeOption.COMPACT -> 34.dp
                                    }
                                )
                                .background(theme.primary, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Apply button
            Button(
                onClick = onApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
            ) {
                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Применить ко всему приложению",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
private fun ColorPill(label: String, color: Color, hex: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF14171F),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, color = Color.Gray, fontSize = 9.sp)
            Text(hex, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private suspend fun loadBitmapFromUri(context: Context, uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
    try {
        var w = 0
        var h = 0
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(stream, null, options)
            w = options.outWidth
            h = options.outHeight
        }

        var sample = 1
        while ((w > 0 && w / sample > 1024) || (h > 0 && h / sample > 1024)) {
            sample *= 2
        }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            val options = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeStream(stream, null, options)
        }
    } catch (_: Throwable) {
        null
    }
}
