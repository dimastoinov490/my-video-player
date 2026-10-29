package com.example.videoplayer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Точные цвета из утверждённого дизайна player_ui.html
private val BottomNavBgTop = Color(0xFF0C0F14)
private val BottomNavBgBottom = Color(0xFF05070A)
private val HotOrange = Color(0xFFFF7A00)
private val HotOrangeLight = Color(0xFFFF9A30)
private val HotOrangeGlow = Color(0x99FF7A00)
private val MutedNavGray = Color(0xFF8A90A0)
private val NavBorderLine = Color(0x1EFFFFFF)
private val NavIndicatorBorder = Color(0x8CFF7A00)
private val NavIndicatorBgTop = Color(0x1EFF7A00)
private val NavIndicatorBgBottom = Color(0x05FFFFFF)
private val HomeBarColor = Color(0xFFF1F1F1)

data class NavTabItem(
    val title: String,
    val icon: ImageVector
)

val ApprovedNavTabs = listOf(
    NavTabItem("Видео", Icons.Default.Movie),
    NavTabItem("Аудио", Icons.Default.MusicNote),
    NavTabItem("Обзор", Icons.Default.Folder),
    NavTabItem("Плейлисты", Icons.AutoMirrored.Filled.PlaylistPlay),
    NavTabItem("Ещё", Icons.Default.MoreHoriz)
)

/**
 * 100% точная копия нижней навигационной панели из утверждённого дизайна player_ui.html.
 * Все вкладки (Видео, Аудио, Обзор, Плейлисты, Ещё) оформлены идентично:
 * - Полноэкранная ширина без зазоров по краям
 * - Градиентный глубокий тёмный фон (#0c0f14 -> #05070a)
 * - Верхняя светящаяся оранжевая линия (#ff7a00)
 * - Активная вкладка плавно подсвечивается горячим оранжевым (#ff7a00)
 * - Скользящая рамка индикатора с оранжевым контуром и неоновым нижним свечением
 * - Нижняя белая полоска Homebar
 */
@Composable
fun ApprovedExactBottomBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedTabRatio by animateFloatAsState(
        targetValue = selectedIndex.coerceIn(0, 4).toFloat(),
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "nav_active_indicator"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(BottomNavBgTop, BottomNavBgBottom)
                )
            )
    ) {
        // Верхний декоративный светящийся контур со скриншота/HTML
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
        ) {
            val width = size.width
            // Тонкая базовая линия
            drawLine(
                color = NavBorderLine,
                start = Offset(0f, 0f),
                end = Offset(width, 0f),
                strokeWidth = 1.dp.toPx()
            )
            // Светящийся оранжевый акцент
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        HotOrange.copy(alpha = 0.25f),
                        HotOrange,
                        HotOrangeLight,
                        HotOrange.copy(alpha = 0.35f),
                        Color.Transparent
                    )
                ),
                start = Offset(width * 0.15f, 0f),
                end = Offset(width * 0.95f, 0f),
                strokeWidth = 1.8.dp.toPx()
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            val totalWidth = maxWidth
            val tabWidth = totalWidth / 5

            // Скользящая рамка активной вкладки (.nav-active из HTML)
            val indicatorMarginHorizontal = 3.dp
            val indicatorWidth = tabWidth - (indicatorMarginHorizontal * 2)
            val indicatorOffset = (tabWidth * animatedTabRatio) + indicatorMarginHorizontal

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset, y = 4.dp)
                    .width(indicatorWidth)
                    .height(56.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 16.dp
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(NavIndicatorBgTop, NavIndicatorBgBottom)
                        )
                    )
                    .border(
                        width = 1.4.dp,
                        color = NavIndicatorBorder,
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = 14.dp,
                            bottomEnd = 16.dp
                        )
                    )
            ) {
                // Нижняя светящаяся полоса активного индикатора (.nav-active::after)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.92f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .shadow(
                            elevation = 8.dp,
                            spotColor = HotOrange,
                            ambientColor = HotOrange
                        )
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    HotOrange,
                                    HotOrangeLight,
                                    HotOrange.copy(alpha = 0.1f)
                                )
                            )
                        )
                )
            }

            // 5 кнопок навигации
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ApprovedNavTabs.forEachIndexed { index, tab ->
                    val isSelected = (index == selectedIndex)
                    val interactionSource = remember { MutableInteractionSource() }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                onTabSelected(index)
                            }
                            .padding(bottom = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            tint = if (isSelected) HotOrange else MutedNavGray,
                            modifier = Modifier
                                .size(if (isSelected) 24.dp else 22.dp)
                                .then(
                                    if (isSelected) {
                                        Modifier.shadow(
                                            elevation = 6.dp,
                                            spotColor = HotOrangeGlow,
                                            ambientColor = HotOrangeGlow
                                        )
                                    } else Modifier
                                )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = tab.title,
                            color = if (isSelected) HotOrange else MutedNavGray,
                            fontSize = if (isSelected) 11.5.sp else 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Homebar полоса внизу по центру со скриншота/HTML (.homebar)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp, top = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(66.dp)
                    .height(3.2.dp)
                    .clip(CircleShape)
                    .background(HomeBarColor)
            )
        }
    }
}
