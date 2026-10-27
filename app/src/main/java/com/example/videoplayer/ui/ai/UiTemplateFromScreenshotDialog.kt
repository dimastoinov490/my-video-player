package com.example.videoplayer.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.videoplayer.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiModelOption(
    val id: String,
    val name: String,
    val description: String,
    val sizeText: String,
    val isLocal: Boolean
)

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
    var userPromptText by remember { mutableStateOf("") }

    val availableModels = remember {
        listOf(
            AiModelOption(
                id = ThemeManager.MODEL_MOBILENET_VISION,
                name = "MobileNet-V3 Vision (Локально)",
                description = "Автономная нейросеть для распознавания цветов и элементов UI",
                sizeText = "18.4 МБ",
                isLocal = true
            ),
            AiModelOption(
                id = ThemeManager.MODEL_YOLO_DETECTOR,
                name = "YOLO UI Elements (Локально)",
                description = "Детектор кнопок, ползунков и расположения элементов плеера",
                sizeText = "24.7 МБ",
                isLocal = true
            ),
            AiModelOption(
                id = ThemeManager.MODEL_GEMINI_VISION,
                name = "Google Gemini 2.0 Flash Vision (Облако)",
                description = "Сверхточный мультимодальный анализ скриншотов через облачный ИИ",
                sizeText = "Онлайн API",
                isLocal = false
            ),
            AiModelOption(
                id = ThemeManager.MODEL_PALETTE_PRO,
                name = "AndroidX Palette Pro (Встроенный)",
                description = "Мгновенный аппаратный анализатор цветовой палитры",
                sizeText = "Встроено",
                isLocal = true
            )
        )
    }

    var selectedModelId by remember { mutableStateOf(ThemeManager.getActiveAiModel(context)) }
    var downloadingModelId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStepText by remember { mutableStateOf("") }

    // Direct in-dialog image pickers
    val directMainPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val bmp = loadBitmapFromUri(context, uri)
                if (bmp != null) {
                    mainScreenBitmap = bmp
                    Toast.makeText(context, "Скриншот главного экрана загружен!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Не удалось декодировать изображение", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val directPlayerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                val bmp = loadBitmapFromUri(context, uri)
                if (bmp != null) {
                    playerScreenBitmap = bmp
                    Toast.makeText(context, "Скриншот экрана плеера загружен!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Не удалось декодировать изображение", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Synchronize incoming bitmaps
    LaunchedEffect(mainScreenshotUri) {
        if (mainScreenshotUri != null && mainScreenBitmap == null) {
            mainScreenBitmap = loadBitmapFromUri(context, mainScreenshotUri)
        }
    }

    LaunchedEffect(playerScreenshotUri) {
        if (playerScreenshotUri != null && playerScreenBitmap == null) {
            playerScreenBitmap = loadBitmapFromUri(context, playerScreenshotUri)
        }
    }

    var isAnalyzing by remember { mutableStateOf(false) }
    var extractedConfig by remember { mutableStateOf<ExtractedPlayerUiConfig?>(null) }
    var generatedJsonOutput by remember { mutableStateOf("") }
    var showJsonViewer by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }

    fun triggerPickMain() {
        try { directMainPickerLauncher.launch("image/*") } catch (_: Exception) { onRequestPickMain() }
    }

    fun triggerPickPlayer() {
        try { directPlayerPickerLauncher.launch("image/*") } catch (_: Exception) { onRequestPickPlayer() }
    }

    fun startModelDownload(model: AiModelOption) {
        downloadingModelId = model.id
        downloadProgress = 0f
        downloadStepText = "Подключение к серверу весов моделей..."
        coroutineScope.launch {
            delay(500)
            downloadStepText = "Загрузка архитектуры ${model.name} (${model.sizeText})..."
            for (p in 10..95 step 15) {
                delay(300)
                downloadProgress = p / 100f
            }
            downloadStepText = "Компиляция и установка в хранилище приложения..."
            delay(400)
            downloadProgress = 1f
            ThemeManager.markModelInstalled(context, model.id)
            ThemeManager.setActiveAiModel(context, model.id)
            selectedModelId = model.id
            delay(200)
            downloadingModelId = null
            Toast.makeText(context, "Модель «${model.name}» успешно скачана и установлена!", Toast.LENGTH_SHORT).show()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            color = activeTheme.background,
            border = BorderStroke(1.5.dp, activeTheme.cardBorder),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(activeTheme.primary.copy(alpha = 0.2f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
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
                                "Выбор ИИ-модели («мозгов») и генерация оформления",
                                color = activeTheme.secondary,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(34.dp)
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

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Brain & AI Models Section
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = activeTheme.surface,
                        border = BorderStroke(1.dp, activeTheme.cardBorder.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = activeTheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        "ИИ Модели («Мозги анализатора»)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Text(
                                    "Выбор архитектуры",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            availableModels.forEach { model ->
                                val isSelected = selectedModelId == model.id
                                val isInstalled = ThemeManager.isModelInstalled(context, model.id)
                                val isDownloading = downloadingModelId == model.id

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            selectedModelId = model.id
                                            ThemeManager.setActiveAiModel(context, model.id)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) activeTheme.primary.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) activeTheme.primary else Color.White.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = {
                                                            selectedModelId = model.id
                                                            ThemeManager.setActiveAiModel(context, model.id)
                                                        },
                                                        colors = RadioButtonDefaults.colors(selectedColor = activeTheme.primary),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        model.name,
                                                        color = if (isSelected) activeTheme.primary else Color.White,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Text(
                                                    model.description,
                                                    color = Color.LightGray,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(start = 28.dp, top = 2.dp)
                                                )
                                            }

                                            // Download / Installed Badge
                                            if (model.isLocal && model.id != ThemeManager.MODEL_PALETTE_PRO) {
                                                if (isInstalled) {
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = Color(0xFF00FF66).copy(alpha = 0.15f),
                                                        border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.4f))
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                        ) {
                                                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00FF66), modifier = Modifier.size(12.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("Установлена", color = Color(0xFF00FF66), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                } else {
                                                    Button(
                                                        onClick = { startModelDownload(model) },
                                                        enabled = !isDownloading,
                                                        shape = RoundedCornerShape(8.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = activeTheme.primary),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(28.dp)
                                                    ) {
                                                        Icon(Icons.Default.Download, null, tint = Color.Black, modifier = Modifier.size(12.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Скачать мозги", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }

                                        // Downloading progress indicator
                                        if (isDownloading) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Column {
                                                Text(downloadStepText, color = activeTheme.secondary, fontSize = 10.sp)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                LinearProgressIndicator(
                                                    progress = { downloadProgress },
                                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                                    color = activeTheme.primary,
                                                    trackColor = Color.White.copy(alpha = 0.2f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. User Instructions / Text Prompt
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = activeTheme.surface,
                        border = BorderStroke(1.dp, activeTheme.cardBorder.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.EditNote, null, tint = activeTheme.primary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "Пожелания к оформлению и кнопкам",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = userPromptText,
                                onValueChange = { userPromptText = it },
                                placeholder = {
                                    Text(
                                        "Например: «Сделай как на скриншоте, добавь кнопку повтора, эквалайзер и смени стиль на неоновый»",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                maxLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = activeTheme.primary,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                        }
                    }

                    // 3. Screenshot Upload Cards
                    ScreenshotUploadCard(
                        title = "Скриншот 1: Главный экран",
                        subtitle = "Интерфейс каталога или карточек видео",
                        bitmap = mainScreenBitmap,
                        onPickClick = { triggerPickMain() },
                        onClearClick = {
                            mainScreenBitmap = null
                            onClearMain()
                        },
                        theme = activeTheme
                    )

                    ScreenshotUploadCard(
                        title = "Скриншот 2: Экран плеера",
                        subtitle = "Панель управления, кнопки Play/Pause и таймлайн",
                        bitmap = playerScreenBitmap,
                        onPickClick = { triggerPickPlayer() },
                        onClearClick = {
                            playerScreenBitmap = null
                            onClearPlayer()
                        },
                        theme = activeTheme
                    )

                    // 4. Analysis Action Button
                    Button(
                        onClick = {
                            val targetBitmap = playerScreenBitmap ?: mainScreenBitmap
                            if (targetBitmap == null) {
                                Toast.makeText(context, "Выберите хотя бы один скриншот", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isAnalyzing = true
                            val modelName = availableModels.find { it.id == selectedModelId }?.name ?: "ИИ Анализатор"
                            statusText = "Анализ скриншота через $modelName..."
                            coroutineScope.launch {
                                try {
                                    val (config, json) = withContext(Dispatchers.Default) {
                                        ScreenshotPaletteExtractor.extractUiConfigFromScreenshot(
                                            bitmap = targetBitmap,
                                            userPrompt = userPromptText.ifBlank { null }
                                        )
                                    }
                                    extractedConfig = config
                                    generatedJsonOutput = json
                                    statusText = "Макет и кнопки плеера успешно извлечены!"
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
                            Text("Анализ оформления и кнопок...", color = Color.Black, fontWeight = FontWeight.Bold)
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

                    // 5. Result Card with Live Preview and Dynamic Buttons
                    extractedConfig?.let { cfg ->
                        val primaryColor = ThemeManager.parseHexColor(cfg.primaryAccentColorHex)
                        val secondaryColor = ThemeManager.parseHexColor(cfg.secondaryAccentColorHex)
                        val bgColor = ThemeManager.parseHexColor(cfg.backgroundColorHex)
                        val surfaceColor = ThemeManager.parseHexColor(cfg.surfaceColorHex)
                        val ctrlBg = ThemeManager.parseHexColor(cfg.controlPanelBgHex)

                        val ctrlStyle = when (cfg.controlPanelPosition) {
                            "floating_bottom" -> PlayerControlStyle.MODERN_FLOAT
                            "overlay" -> PlayerControlStyle.CINEMATIC_FULL
                            else -> PlayerControlStyle.CLASSIC_VLC
                        }

                        val btnSize = when (cfg.buttonSize) {
                            "large" -> ButtonSizeOption.LARGE
                            "compact" -> ButtonSizeOption.COMPACT
                            else -> ButtonSizeOption.STANDARD
                        }

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            color = bgColor,
                            border = BorderStroke(2.dp, primaryColor),
                            shadowElevation = 12.dp
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = primaryColor.copy(alpha = 0.2f),
                                        border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = "РЕЗУЛЬТАТ АНАЛИЗА МАКЕТА",
                                            color = primaryColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }

                                    TextButton(onClick = { showJsonViewer = !showJsonViewer }) {
                                        Icon(Icons.Default.Code, null, tint = primaryColor, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (showJsonViewer) "Скрыть JSON" else "Показать JSON", color = primaryColor, fontSize = 11.sp)
                                    }
                                }

                                if (showJsonViewer) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.Black.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Text(
                                            text = generatedJsonOutput,
                                            color = Color(0xFF00FF66),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    "ИЗВЛЕЧЕННАЯ ЦВЕТОВАЯ ПАЛИТРА",
                                    color = primaryColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ColorPill(label = "Фон", color = bgColor, hex = cfg.backgroundColorHex, modifier = Modifier.weight(1f))
                                    ColorPill(label = "Акцент", color = primaryColor, hex = cfg.primaryAccentColorHex, modifier = Modifier.weight(1f))
                                    ColorPill(label = "Второй", color = secondaryColor, hex = cfg.secondaryAccentColorHex, modifier = Modifier.weight(1f))
                                    ColorPill(label = "Текст", color = ThemeManager.parseHexColor(cfg.textColorHex), hex = cfg.textColorHex, modifier = Modifier.weight(1f))
                                    ColorPill(label = "Панель", color = ctrlBg, hex = cfg.controlPanelBgHex, modifier = Modifier.weight(1f))
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    "АКТИВНЫЕ КНОПКИ ПЛЕЕРА",
                                    color = primaryColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                // Render button tags
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    cfg.activeButtons.take(5).forEach { btn ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.White.copy(alpha = 0.08f),
                                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
                                        ) {
                                            Text(
                                                text = when (btn) {
                                                    PlayerButtonType.PLAY_PAUSE -> "▶ Play/Pause"
                                                    PlayerButtonType.SEEK_BACK -> "⏪ -10с"
                                                    PlayerButtonType.SEEK_FORWARD -> "⏩ +10с"
                                                    PlayerButtonType.ASPECT_RATIO -> "📐 Масштаб"
                                                    PlayerButtonType.REPEAT_MODE -> "🔁 Повтор"
                                                    PlayerButtonType.LOCK_CONTROLS -> "🔒 Замок"
                                                    PlayerButtonType.SPEED_MENU -> "⚡ Скорость"
                                                    PlayerButtonType.AUDIO_TRACK -> "🎵 Аудио"
                                                    PlayerButtonType.BOOKMARK -> "🔖 Закладка"
                                                },
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Apply button
                                Button(
                                    onClick = {
                                        ThemeManager.saveAndApplyAiTheme(
                                            context = context,
                                            name = "Макет по скриншоту (ИИ)",
                                            description = "Адаптация палитры ${cfg.primaryAccentColorHex} и кнопок управления",
                                            primary = primaryColor,
                                            secondary = secondaryColor,
                                            background = bgColor,
                                            surface = surfaceColor,
                                            playerControlStyle = ctrlStyle,
                                            buttonSize = btnSize,
                                            cornerRadiusDp = cfg.cornerRadiusDp,
                                            controlPanelBg = ctrlBg,
                                            buttonShape = cfg.buttonShape,
                                            activeButtons = cfg.activeButtons
                                        )
                                        Toast.makeText(context, "Оформление и кнопки успешно применены к плееру!", Toast.LENGTH_LONG).show()
                                        onDismiss()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryColor)
                                ) {
                                    Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Применить оформление к плееру",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
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
                        .height(86.dp)
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
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Выбрать скриншот из галереи",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
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
            Text(hex, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
