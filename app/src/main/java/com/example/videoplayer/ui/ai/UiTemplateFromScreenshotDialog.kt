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

    // Direct, ultra-reliable in-dialog system image pickers (ActivityResultContracts.GetContent)
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

    // Synchronize bitmaps from incoming external URIs if provided
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
        try {
            directMainPickerLauncher.launch("image/*")
        } catch (e: Exception) {
            onRequestPickMain()
        }
    }

    fun triggerPickPlayer() {
        try {
            directPlayerPickerLauncher.launch("image/*")
        } catch (e: Exception) {
            onRequestPickPlayer()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = activeTheme.background,
            border = BorderStroke(1.5.dp, activeTheme.cardBorder),
            shadowElevation = 24.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
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
                                "Извлечение палитры через AndroidX Palette API",
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
                        "Загрузите скриншот желаемого интерфейса — механизм на базе AndroidX Palette API извлечет доминирующие цвета (фон, акцент кнопок, текст), определит геометрию панели управления и сформирует точный стиль плеера.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Screenshot 1: Main Screen
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

                    // Screenshot 2: Player Screen
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

                    // Analysis Action Button
                    Button(
                        onClick = {
                            val targetBitmap = playerScreenBitmap ?: mainScreenBitmap
                            if (targetBitmap == null) {
                                Toast.makeText(context, "Выберите хотя бы один скриншот", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            isAnalyzing = true
                            statusText = "AndroidX Palette извлекает цвета и параметры макета..."
                            coroutineScope.launch {
                                try {
                                    val (config, json) = withContext(Dispatchers.Default) {
                                        ScreenshotPaletteExtractor.extractUiConfigFromScreenshot(targetBitmap)
                                    }
                                    extractedConfig = config
                                    generatedJsonOutput = json
                                    statusText = "Палитра и стиль плеера успешно извлечены!"
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
                            Text("Анализ палитры...", color = Color.Black, fontWeight = FontWeight.Bold)
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

                    // Result Card with Live Preview and JSON output
                    extractedConfig?.let { cfg ->
                        val primaryColor = ThemeManager.parseHexColor(cfg.primaryAccentColorHex)
                        val secondaryColor = ThemeManager.parseHexColor(cfg.secondaryAccentColorHex)
                        val bgColor = ThemeManager.parseHexColor(cfg.backgroundColorHex)
                        val surfaceColor = ThemeManager.parseHexColor(cfg.surfaceColorHex)

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
                                            text = "ANDROIDX PALETTE EXTRACTOR",
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
                                    ColorPill(label = "Панель", color = surfaceColor, hex = cfg.surfaceColorHex, modifier = Modifier.weight(1f))
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Mini player mockup preview
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(cfg.cornerRadiusDp.dp),
                                    color = surfaceColor,
                                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Макет плеера по образцу", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(
                                                "${cfg.controlPanelPosition} • ${cfg.buttonSize}",
                                                color = secondaryColor,
                                                fontSize = 10.sp
                                            )
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
                                                    .fillMaxWidth(0.4f)
                                                    .fillMaxHeight()
                                                    .background(primaryColor, CircleShape)
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))

                                        val playBtnSize = when (btnSize) {
                                            ButtonSizeOption.LARGE -> 44.dp
                                            ButtonSizeOption.STANDARD -> 36.dp
                                            ButtonSizeOption.COMPACT -> 30.dp
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Replay10, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(playBtnSize)
                                                    .background(primaryColor, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            Icon(Icons.Default.Forward10, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                // Apply button
                                Button(
                                    onClick = {
                                        ThemeManager.saveAndApplyAiTheme(
                                            context = context,
                                            name = "Макет по скриншоту (Palette)",
                                            description = "Адаптация палитры ${cfg.primaryAccentColorHex} и стиля ${cfg.controlPanelPosition}",
                                            primary = primaryColor,
                                            secondary = secondaryColor,
                                            background = bgColor,
                                            surface = surfaceColor,
                                            playerControlStyle = ctrlStyle,
                                            buttonSize = btnSize,
                                            cornerRadiusDp = cfg.cornerRadiusDp,
                                            controlPanelBg = surfaceColor
                                        )
                                        Toast.makeText(context, "Оформление по скриншоту успешно применено!", Toast.LENGTH_LONG).show()
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
