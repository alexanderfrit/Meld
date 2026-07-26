/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.text.Layout
import android.widget.Toast
import kotlin.math.roundToInt
import timber.log.Timber
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.lyrics.LyricsTranslationHelper
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.ui.screens.settings.LyricsPosition
import com.metrolist.music.utils.ComposeToImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun LyricsTranslationHeader(
    status: LyricsTranslationHelper.TranslationStatus,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = status !is LyricsTranslationHelper.TranslationStatus.Idle,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        when (status) {
            is LyricsTranslationHelper.TranslationStatus.Translating -> {
                TranslationCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = stringResource(R.string.ai_translating_lyrics),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            is LyricsTranslationHelper.TranslationStatus.Error -> {
                TranslationCard(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.error),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = status.message,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            is LyricsTranslationHelper.TranslationStatus.Success -> {
                TranslationCard(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.ai_lyrics_translated),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
            else -> {}
        }
    }
}

@Composable
private fun TranslationCard(
    containerColor: Color,
    contentColor: Color,
    content: @Composable RowScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColor),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
internal fun LyricsActionOverlay(
    isAutoScrollEnabled: Boolean,
    isSynced: Boolean,
    isSelectionModeActive: Boolean,
    selectedCount: Int,
    onSyncClick: () -> Unit,
    onCancelSelection: () -> Unit,
    onShareSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    Box(
        modifier = modifier.padding(bottom = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = !isAutoScrollEnabled && isSynced && !isSelectionModeActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 6.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSyncClick()
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = stringResource(R.string.auto_scroll),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.auto_scroll),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        AnimatedVisibility(
            visible = isSelectionModeActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onCancelSelection()
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = if (selectedCount > 0) "$selectedCount selected" else stringResource(R.string.share_lyrics),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )

                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onShareSelection()
                        },
                        enabled = selectedCount > 0,
                        shape = CircleShape
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = stringResource(R.string.share_selected),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.share),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsShareDialog(
    txt: String,
    title: String,
    arts: String,
    songId: String,
    onDismiss: () -> Unit,
    onShareAsImage: () -> Unit
) {
    val context = LocalContext.current
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            shape = MaterialTheme.shapes.medium,
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp).fillMaxWidth(0.85f)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(stringResource(R.string.share_lyrics), fontWeight = FontWeight.Normal, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "\"$txt\"\n\n$title - $arts\nhttps://music.youtube.com/watch?v=$songId")
                        }
                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_lyrics)))
                        onDismiss()
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.share), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.share_as_text), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        onShareAsImage()
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.share), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.share_as_image), fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onDismiss() }.padding(vertical = 8.dp, horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LyricsColorPickerDialog(
    txt: String,
    title: String,
    arts: String,
    thumbnailUrl: String?,
    lyricsTextPosition: LyricsPosition = LyricsPosition.LEFT,
    onDismiss: () -> Unit,
    onShare: (
        backgroundColor: Color,
        textColor: Color,
        secondaryTextColor: Color,
        style: LyricsBackgroundStyle,
        alignment: TextAlign,
        showAppBranding: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val pal = remember { mutableStateListOf<Color>() }
    var bgStyle by remember { mutableStateOf(LyricsBackgroundStyle.SOLID) }
    var previewBackgroundColor by remember { mutableStateOf(Color(0xFF242424)) }
    var previewTextColor by remember { mutableStateOf(Color.White.copy(alpha = 0.75f)) }
    var previewSecondaryTextColor by remember { mutableStateOf(Color.White.copy(alpha = 0.6f)) }
    
    val initialAlign = when (lyricsTextPosition) {
        LyricsPosition.LEFT -> TextAlign.Start
        LyricsPosition.CENTER -> TextAlign.Center
        LyricsPosition.RIGHT -> TextAlign.End
    }
    var selectedAlignment by remember { mutableStateOf(initialAlign) }
    var showAppBranding by remember { mutableStateOf(true) }
    
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl != null) {
            withContext(Dispatchers.IO) {
                try {
                    val res = ImageLoader(context).execute(ImageRequest.Builder(context).data(thumbnailUrl).allowHardware(false).build())
                    val bmp = res.image?.toBitmap()
                    if (bmp != null) {
                        val swatches = Palette.from(bmp).generate().swatches.sortedByDescending { it.population }
                        pal.clear()
                        pal.addAll(swatches.map { Color(it.rgb) }.filter { 
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(it.toArgb(), hsv)
                            hsv[1] > 0.2f
                        }.take(5))
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to extract palette colors")
                }
            }
        }
    }

    var activeCustomColorTarget by remember { mutableStateOf<String?>(null) }
    var customPickerInitialColor by remember { mutableStateOf(Color.White) }

    if (activeCustomColorTarget != null) {
        CustomColorPickerDialog(
            initialColor = customPickerInitialColor,
            onDismiss = { activeCustomColorTarget = null },
            onColorPicked = { pickedColor ->
                when (activeCustomColorTarget) {
                    "bg" -> previewBackgroundColor = pickedColor
                    "text" -> previewTextColor = pickedColor
                    "secondary" -> previewSecondaryTextColor = pickedColor
                }
                activeCustomColorTarget = null
            }
        )
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .padding(vertical = 8.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // Header with Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.customize_colors),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.cancel)
                        )
                    }
                }
                
                Spacer(Modifier.height(12.dp))
                
                // Live Dynamic Card Preview Surface Container
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight()
                            .padding(6.dp)
                    ) {
                        LyricsImageCard(
                            lyricText = txt,
                            mediaMetadata = MediaMetadata(
                                id = "",
                                title = title,
                                artists = listOf(MediaMetadata.Artist(name = arts, id = null)),
                                thumbnailUrl = thumbnailUrl,
                                duration = 0
                            ),
                            darkBackground = true,
                            backgroundColor = previewBackgroundColor,
                            backgroundStyle = bgStyle,
                            textColor = previewTextColor,
                            secondaryTextColor = previewSecondaryTextColor,
                            textAlign = selectedAlignment,
                            showAppBranding = showAppBranding
                        )
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                // Grouped Options Section Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Background Style Section
                        Column {
                            Text(
                                stringResource(R.string.player_background_style),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                LyricsBackgroundStyle.entries.forEach { style ->
                                    val label = when(style) {
                                        LyricsBackgroundStyle.SOLID -> stringResource(R.string.player_background_solid)
                                        LyricsBackgroundStyle.BLUR -> stringResource(R.string.player_background_blur)
                                        else -> stringResource(R.string.gradient)
                                    }
                                    FilterChip(
                                        selected = bgStyle == style,
                                        onClick = { bgStyle = style },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }

                        // Text Alignment Section
                        Column {
                            Text(
                                stringResource(R.string.alignment),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = selectedAlignment == TextAlign.Start || selectedAlignment == TextAlign.Left,
                                    onClick = { selectedAlignment = TextAlign.Start },
                                    label = { Text(stringResource(R.string.align_left)) }
                                )
                                FilterChip(
                                    selected = selectedAlignment == TextAlign.Center,
                                    onClick = { selectedAlignment = TextAlign.Center },
                                    label = { Text(stringResource(R.string.align_center)) }
                                )
                                FilterChip(
                                    selected = selectedAlignment == TextAlign.End || selectedAlignment == TextAlign.Right,
                                    onClick = { selectedAlignment = TextAlign.End },
                                    label = { Text(stringResource(R.string.align_right)) }
                                )
                            }
                        }

                        // Show App Name Toggle Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.show_app_name),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Switch(
                                checked = showAppBranding,
                                onCheckedChange = { showAppBranding = it }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Grouped Color Pickers Section Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val backgroundOptions = (pal + listOf(Color(0xFF242424), Color(0xFF121212), Color.White, Color.Black, Color(0xFFF5F5F5))).distinct().take(8)
                        ColorPickerRow(
                            title = stringResource(R.string.background_color),
                            selectedColor = previewBackgroundColor,
                            colorList = backgroundOptions,
                            onColorSelected = { previewBackgroundColor = it },
                            onCustomColorRequested = {
                                customPickerInitialColor = previewBackgroundColor
                                activeCustomColorTarget = "bg"
                            }
                        )

                        val textOptions = (pal + listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.9f), Color.White, Color.Black, Color(0xFF1DB954))).distinct().take(8)
                        ColorPickerRow(
                            title = stringResource(R.string.text_color),
                            selectedColor = previewTextColor,
                            colorList = textOptions,
                            onColorSelected = { previewTextColor = it },
                            onCustomColorRequested = {
                                customPickerInitialColor = previewTextColor
                                activeCustomColorTarget = "text"
                            }
                        )

                        val secondaryTextOptions = (pal.map { it.copy(alpha = 0.6f) } + listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.4f), Color.Black.copy(alpha = 0.6f), Color(0xFF1DB954))).distinct().take(8)
                        ColorPickerRow(
                            title = stringResource(R.string.secondary_text_color),
                            selectedColor = previewSecondaryTextColor,
                            colorList = secondaryTextOptions,
                            onColorSelected = { previewSecondaryTextColor = it },
                            onCustomColorRequested = {
                                customPickerInitialColor = previewSecondaryTextColor
                                activeCustomColorTarget = "secondary"
                            }
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Share Button with Icon
                Button(
                    onClick = {
                        onShare(previewBackgroundColor, previewTextColor, previewSecondaryTextColor, bgStyle, selectedAlignment, showAppBranding)
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.share), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ColorPickerRow(
    title: String,
    selectedColor: Color,
    colorList: List<Color>,
    onColorSelected: (Color) -> Unit,
    onCustomColorRequested: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp)
        ) {
            colorList.forEach { color ->
                val isSelected = selectedColor == color
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.25f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(color) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Custom Color Wheel / Plus button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), CircleShape)
                    .clickable { onCustomColorRequested() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorPicked: (Color) -> Unit
) {
    val hsv = remember {
        FloatArray(3).also {
            android.graphics.Color.colorToHSV(initialColor.toArgb(), it)
        }
    }
    var hue by remember { mutableStateOf(hsv[0]) }
    var saturation by remember { mutableStateOf(hsv[1]) }
    var value by remember { mutableStateOf(hsv[2]) }
    var alpha by remember { mutableStateOf(initialColor.alpha) }

    val currentColor = remember(hue, saturation, value, alpha) {
        val argb = android.graphics.Color.HSVToColor((alpha * 255).roundToInt(), floatArrayOf(hue, saturation, value))
        Color(argb)
    }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Custom Color & Transparency",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(14.dp))

                // Color Preview Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(currentColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#${Integer.toHexString(currentColor.toArgb()).uppercase()}",
                        color = if (currentColor.luminance() > 0.5f) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(Modifier.height(14.dp))

                // Hue Slider
                Text("Hue: ${hue.toInt()}°", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..360f)

                // Saturation Slider
                Text("Saturation: ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                Slider(value = saturation, onValueChange = { saturation = it }, valueRange = 0f..1f)

                // Brightness / Value Slider
                Text("Brightness: ${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                Slider(value = value, onValueChange = { value = it }, valueRange = 0f..1f)

                // Alpha / Transparency Slider
                Text("Opacity: ${(alpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
                Slider(value = alpha, onValueChange = { alpha = it }, valueRange = 0f..1f)

                Spacer(Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onColorPicked(currentColor); onDismiss() }) {
                        Text("Apply")
                    }
                }
            }
        }
    }
}

// Helper for coroutine scope
typealias CoroutineScope = kotlinx.coroutines.CoroutineScope
