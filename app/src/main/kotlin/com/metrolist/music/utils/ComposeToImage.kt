/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import timber.log.Timber
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.ui.component.LyricsBackgroundStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

object ComposeToImage {

    fun shareLyricsImage(context: Context, uri: Uri) {
        val instagramIntent = Intent("com.instagram.share.ADD_TO_STORY").apply {
            setDataAndType(uri, "image/png")
            putExtra("interactive_asset_uri", uri)
            putExtra("source_image_uri", uri)
            putExtra("source_application", context.packageName)
            setPackage("com.instagram.android")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        try {
            context.grantUriPermission(
                "com.instagram.android",
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) {}

        try {
            context.startActivity(instagramIntent)
            return
        } catch (e: Exception) {
            Timber.d(e, "Direct Instagram Story intent launch failed, falling back to system chooser")
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_lyrics)))
    }

    suspend fun createLyricsImage(
        context: Context,
        coverArtUrl: String?,
        songTitle: String,
        artistName: String,
        lyrics: String,
        width: Int,
        height: Int,
        backgroundColor: Int? = null,
        backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.SOLID,
        textColor: Int? = null,
        secondaryTextColor: Int? = null,
        lyricsAlignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        showAppBranding: Boolean = true
    ): Bitmap = withContext(Dispatchers.Default) {
        val canvasWidth = 2160
        val scale = canvasWidth / 360f

        val defaultBackgroundColor = 0xFF121212.toInt()
        val defaultTextColor = 0xBFFFFFFF.toInt()
        val defaultSecondaryTextColor = 0x99FFFFFF.toInt()

        val bgColor = backgroundColor ?: defaultBackgroundColor
        val mainTextColor = textColor ?: defaultTextColor
        val secondaryTxtColor = secondaryTextColor ?: defaultSecondaryTextColor

        // Pre-load cover art
        var coverArtBitmap: Bitmap? = null
        if (coverArtUrl != null) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(coverArtUrl)
                    .size(1024) 
                    .allowHardware(false)
                    .build()
                val result = imageLoader.execute(request)
                coverArtBitmap = result.image?.toBitmap()
            } catch (_: Exception) {}
        }

        val cardWidth = canvasWidth * 0.65f
        val cardMarginX = (canvasWidth - cardWidth) / 2f
        val cardPadding = 16f * scale
        val coverArtSize = 36f * scale

        // Pre-measure lyrics height dynamically per line to apply paragraph spacing only between distinct lyric lines
        val lyricsWidth = cardWidth - (cardPadding * 2)
        val lyricsPaint = TextPaint().apply {
            color = mainTextColor
            textSize = 17f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            letterSpacing = 0.005f
        }

        val paragraphSpacing = 10f * scale
        val lineStrings = lyrics.split("\n")
        val lineLayouts = lineStrings.map { lineStr ->
            StaticLayout.Builder.obtain(lineStr, 0, lineStr.length, lyricsPaint, lyricsWidth.toInt())
                .setAlignment(lyricsAlignment)
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .build()
        }

        val lyricsContentHeight = lineLayouts.sumOf { it.height } + (if (lineLayouts.size > 1) (lineLayouts.size - 1) * paragraphSpacing else 0f)

        val headerBottomPadding = 10f * scale
        val dividerSpacing = 12f * scale
        val logoBoxSize = 16f * scale
        val footerSpacing = if (showAppBranding) 14f * scale else 4f * scale
        val footerHeight = if (showAppBranding) logoBoxSize else 0f

        val cardHeight = (
            cardPadding + 
            coverArtSize + 
            headerBottomPadding + 
            (1f * scale) + 
            dividerSpacing + 
            lyricsContentHeight + 
            footerSpacing + 
            footerHeight + 
            cardPadding
        )

        // 9:16 Portrait Canvas Dimensions (Ultra-HD 2160x3840 base ratio)
        val minCanvasHeight = canvasWidth * (16f / 9f)
        val requiredCanvasHeight = (cardHeight + (280f * scale)).coerceAtLeast(minCanvasHeight)
        val canvasHeightInt = requiredCanvasHeight.toInt()

        val bitmap = createBitmap(canvasWidth, canvasHeightInt)
        val canvas = Canvas(bitmap)

        // 1. Draw Outer 9:16 Full Canvas Background (Atmospheric Dark Backdrop)
        val fullCanvasRect = RectF(0f, 0f, canvasWidth.toFloat(), canvasHeightInt.toFloat())
        val fullBgPaint = Paint().apply { isAntiAlias = true }

        when (backgroundStyle) {
            LyricsBackgroundStyle.SOLID -> {
                // Darken solid background for the outer 9:16 backdrop unless already very dark
                val hsv = FloatArray(3)
                android.graphics.Color.colorToHSV(bgColor, hsv)
                val outerBgColor = if (hsv[2] > 0.25f) {
                    hsv[2] = (hsv[2] * 0.50f).coerceIn(0f, 1f)
                    android.graphics.Color.HSVToColor(android.graphics.Color.alpha(bgColor), hsv)
                } else {
                    bgColor
                }
                fullBgPaint.color = outerBgColor
                canvas.drawRect(fullCanvasRect, fullBgPaint)
            }
            LyricsBackgroundStyle.BLUR -> {
                fullBgPaint.color = 0xFF000000.toInt()
                canvas.drawRect(fullCanvasRect, fullBgPaint)

                if (coverArtBitmap != null) {
                    try {
                        val scaledBitmap = Bitmap.createScaledBitmap(coverArtBitmap, canvasWidth / 10, canvasHeightInt / 10, true)
                        val blurredBitmap = fastBlur(scaledBitmap, 1f, 25)
                        
                        if (blurredBitmap != null) {
                            canvas.drawBitmap(blurredBitmap, null, fullCanvasRect, null)
                            val overlayPaint = Paint().apply { color = 0x80000000.toInt() }
                            canvas.drawRect(fullCanvasRect, overlayPaint)
                        }
                    } catch (_: Exception) {
                        fullBgPaint.color = bgColor
                        canvas.drawRect(fullCanvasRect, fullBgPaint)
                    }
                } else {
                    fullBgPaint.color = bgColor
                    canvas.drawRect(fullCanvasRect, fullBgPaint)
                }
            }
            LyricsBackgroundStyle.GRADIENT -> {
                if (coverArtBitmap != null) {
                    val palette = Palette.from(coverArtBitmap).generate()
                    var vibrant = palette.getVibrantColor(bgColor)
                    var darkVibrant = palette.getDarkVibrantColor(bgColor)
                    
                    val hsv1 = FloatArray(3)
                    val hsv2 = FloatArray(3)
                    android.graphics.Color.colorToHSV(vibrant, hsv1)
                    android.graphics.Color.colorToHSV(darkVibrant, hsv2)
                    hsv1[2] = (hsv1[2] * 0.60f).coerceIn(0f, 1f)
                    hsv2[2] = (hsv2[2] * 0.60f).coerceIn(0f, 1f)
                    vibrant = android.graphics.Color.HSVToColor(android.graphics.Color.alpha(vibrant), hsv1)
                    darkVibrant = android.graphics.Color.HSVToColor(android.graphics.Color.alpha(darkVibrant), hsv2)

                    val gradient = LinearGradient(
                        0f, 0f, canvasWidth.toFloat(), canvasHeightInt.toFloat(),
                        intArrayOf(vibrant, darkVibrant),
                        null,
                        Shader.TileMode.CLAMP
                    )
                    fullBgPaint.shader = gradient
                    canvas.drawRect(fullCanvasRect, fullBgPaint)
                } else {
                    fullBgPaint.color = bgColor
                    canvas.drawRect(fullCanvasRect, fullBgPaint)
                }
            }
        }

        // 2. Draw Floating Inner Rounded Card
        val cardTop = (canvasHeightInt - cardHeight) / 2.2f
        val cardRect = RectF(cardMarginX, cardTop, cardMarginX + cardWidth, cardTop + cardHeight)
        val cardCornerRadius = 18f * scale

        // Card Container Background Surface (Solid uses exact bgColor, Blur/Gradient use translucent dark surface)
        val cardContainerPaint = Paint().apply {
            color = when (backgroundStyle) {
                LyricsBackgroundStyle.SOLID -> bgColor
                else -> 0x4D000000
            }
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardRect, cardCornerRadius, cardCornerRadius, cardContainerPaint)

        // Card Inner Border
        val borderPaint = Paint().apply {
            color = mainTextColor
            alpha = (255 * 0.12).toInt()
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * scale
            isAntiAlias = true
        }
        canvas.drawRoundRect(cardRect, cardCornerRadius, cardCornerRadius, borderPaint)

        // 3. Render Card Content (Header, Divider, Lyrics, Footer) inside cardRect
        val contentStartX = cardMarginX + cardPadding
        val cardContentTop = cardTop + cardPadding

        // Cover Art & Header
        val coverCornerRadius = 6f * scale
        coverArtBitmap?.let {
            val rect = RectF(contentStartX, cardContentTop, contentStartX + coverArtSize, cardContentTop + coverArtSize)
            val path = Path().apply {
                addRoundRect(rect, coverCornerRadius, coverCornerRadius, Path.Direction.CW)
            }
            val coverBorderPaint = Paint().apply {
                color = mainTextColor
                alpha = (255 * 0.16).toInt()
                style = Paint.Style.STROKE
                strokeWidth = 1f * scale
                isAntiAlias = true
            }

            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(it, null, rect, null)
            canvas.restore()
            canvas.drawRoundRect(rect, coverCornerRadius, coverCornerRadius, coverBorderPaint)
        }

        val textStartX = contentStartX + coverArtSize + (10f * scale)
        val textMaxWidth = cardWidth - (coverArtSize + (10f * scale) + cardPadding * 2)

        val titlePaint = TextPaint().apply {
            color = mainTextColor
            textSize = 14f * scale
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        
        val artistPaint = TextPaint().apply {
            color = secondaryTxtColor
            textSize = 11.5f * scale
            typeface = Typeface.DEFAULT
            isAntiAlias = true
        }

        val titleLayout = StaticLayout.Builder.obtain(songTitle, 0, songTitle.length, titlePaint, textMaxWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val artistLayout = StaticLayout.Builder.obtain(artistName, 0, artistName.length, artistPaint, textMaxWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .setMaxLines(1)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()

        val headerTextHeight = titleLayout.height + artistLayout.height + (1f * scale)
        val headerCenterY = cardContentTop + coverArtSize / 2f
        val titleY = headerCenterY - headerTextHeight / 2f
        
        canvas.save()
        canvas.translate(textStartX, titleY)
        titleLayout.draw(canvas)
        canvas.translate(0f, titleLayout.height.toFloat() + (1f * scale))
        artistLayout.draw(canvas)
        canvas.restore()

        // Horizontal Divider Line inside Card
        val headerBottomY = cardContentTop + coverArtSize + headerBottomPadding
        val dividerPaint = Paint().apply {
            color = mainTextColor
            alpha = (255 * 0.12).toInt()
            strokeWidth = 1f * scale
            isAntiAlias = true
        }
        canvas.drawLine(contentStartX, headerBottomY, cardMarginX + cardWidth - cardPadding, headerBottomY, dividerPaint)

        // Lyrics Section inside Card
        var currentLyricsY = headerBottomY + dividerSpacing
        lineLayouts.forEach { layout ->
            canvas.save()
            canvas.translate(contentStartX, currentLyricsY)
            layout.draw(canvas)
            canvas.restore()
            currentLyricsY += layout.height + paragraphSpacing
        }

        // Footer Section inside Card
        if (showAppBranding) {
            val footerY = cardTop + cardHeight - cardPadding - logoBoxSize
            val logoIconSize = 16f * scale
            val footerAlpha = (android.graphics.Color.alpha(secondaryTxtColor) * 0.45f).toInt()
            val logoBgPaint = Paint().apply {
                color = secondaryTxtColor
                alpha = footerAlpha
                isAntiAlias = true
            }
            val logoBoxRect = RectF(contentStartX, footerY, contentStartX + logoBoxSize, footerY + logoBoxSize)
            canvas.drawOval(logoBoxRect, logoBgPaint)
            
            val rawLogo = context.getDrawable(R.drawable.small_icon)?.toBitmap()
            rawLogo?.let {
                val logoPaint = Paint().apply {
                    colorFilter = PorterDuffColorFilter(bgColor, PorterDuff.Mode.SRC_IN)
                    isAntiAlias = true
                }
                
                val logoOffset = (logoBoxSize - logoIconSize) / 2f
                val logoRect = RectF(
                    contentStartX + logoOffset, 
                    footerY + logoOffset, 
                    contentStartX + logoBoxSize - logoOffset, 
                    footerY + logoBoxSize - logoOffset
                )
                canvas.drawBitmap(it, null, logoRect, logoPaint)
            }
            
            val appName = context.getString(R.string.app_name)
            val appNamePaint = TextPaint().apply {
                color = secondaryTxtColor
                alpha = footerAlpha
                textSize = 11.5f * scale
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }
            
            val appNameX = contentStartX + logoBoxSize + (8f * scale)
            val appNameY = footerY + logoBoxSize/2f - (appNamePaint.descent() + appNamePaint.ascent()) / 2f
            canvas.drawText(appName, appNameX, appNameY, appNamePaint)
        }

        return@withContext bitmap
    }

    // Stack Blur v1.0 from http://www.quasimondo.com/StackBlurForCanvas/StackBlurDemo.html
    // Java Author: Mario Klingemann <mario at quasimondo.com>
    // http://incubator.quasimondo.com
    //
    // created Feburary 29, 2004
    // Android port : Yahel Bouaziz <yahel at kayenko.com>
    // http://www.kayenko.com
    // ported to Kotlin and adapted
    private fun fastBlur(sentBitmap: Bitmap, scale: Float, radius: Int): Bitmap? {
        val width = (sentBitmap.width * scale).roundToInt()
        val height = (sentBitmap.height * scale).roundToInt()
        
        if (width <= 0 || height <= 0) return null
        
        val bitmap = Bitmap.createScaledBitmap(sentBitmap, width, height, false)
        val w = bitmap.width
        val h = bitmap.height
        val pix = IntArray(w * h)
        bitmap.getPixels(pix, 0, w, 0, 0, w, h)
        val wm = w - 1
        val hm = h - 1
        val wh = w * h
        val div = radius + radius + 1
        val r = IntArray(wh)
        val g = IntArray(wh)
        val b = IntArray(wh)
        var rsum: Int
        var gsum: Int
        var bsum: Int
        var x: Int
        var y: Int
        var i: Int
        var p: Int
        var yp: Int
        var yi: Int
        var yw: Int
        val vmin = IntArray(Math.max(w, h))
        var divsum = div + 1 shr 1
        divsum *= divsum
        val dv = IntArray(256 * divsum)
        i = 0
        while (i < 256 * divsum) {
            dv[i] = i / divsum
            i++
        }
        yw = 0
        yi = 0
        val stack = Array(div) { IntArray(3) }
        var stackpointer: Int
        var stackstart: Int
        var sir: IntArray
        var rbs: Int
        var r1 = radius + 1
        var routsum: Int
        var goutsum: Int
        var boutsum: Int
        var rinsum: Int
        var ginsum: Int
        var binsum: Int
        y = 0
        while (y < h) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            i = -radius
            while (i <= radius) {
                p = pix[yi + Math.min(wm, Math.max(i, 0))]
                sir = stack[i + radius]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                i++
            }
            stackpointer = radius
            x = 0
            while (x < w) {
                r[yi] = dv[rsum]
                g[yi] = dv[gsum]
                b[yi] = dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (y == 0) {
                    vmin[x] = Math.min(x + radius + 1, wm)
                }
                p = pix[yw + vmin[x]]
                sir[0] = p and 0xff0000 shr 16
                sir[1] = p and 0x00ff00 shr 8
                sir[2] = p and 0x0000ff
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi++
                x++
            }
            yw += w
            y++
        }
        x = 0
        while (x < w) {
            bsum = 0
            gsum = 0
            rsum = 0
            boutsum = 0
            goutsum = 0
            routsum = 0
            binsum = 0
            ginsum = 0
            rinsum = 0
            yp = -radius * w
            i = -radius
            while (i <= radius) {
                yi = Math.max(0, yp) + x
                sir = stack[i + radius]
                sir[0] = r[yi]
                sir[1] = g[yi]
                sir[2] = b[yi]
                rbs = r1 - Math.abs(i)
                rsum += sir[0] * rbs
                gsum += sir[1] * rbs
                bsum += sir[2] * rbs
                if (i > 0) {
                    rinsum += sir[0]
                    ginsum += sir[1]
                    binsum += sir[2]
                } else {
                    routsum += sir[0]
                    goutsum += sir[1]
                    boutsum += sir[2]
                }
                if (i < hm) {
                    yp += w
                }
                i++
            }
            yi = x
            stackpointer = radius
            y = 0
            while (y < h) {
                pix[yi] = -0x1000000 or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
                rsum -= routsum
                gsum -= goutsum
                bsum -= boutsum
                stackstart = stackpointer - radius + div
                sir = stack[stackstart % div]
                routsum -= sir[0]
                goutsum -= sir[1]
                boutsum -= sir[2]
                if (x == 0) {
                    vmin[y] = Math.min(y + r1, hm) * w
                }
                p = x + vmin[y]
                sir[0] = r[p]
                sir[1] = g[p]
                sir[2] = b[p]
                rinsum += sir[0]
                ginsum += sir[1]
                binsum += sir[2]
                rsum += rinsum
                gsum += ginsum
                bsum += binsum
                stackpointer = (stackpointer + 1) % div
                sir = stack[stackpointer % div]
                routsum += sir[0]
                goutsum += sir[1]
                boutsum += sir[2]
                rinsum -= sir[0]
                ginsum -= sir[1]
                binsum -= sir[2]
                yi += w
                y++
            }
            x++
        }
        bitmap.setPixels(pix, 0, w, 0, 0, w, h)
        return bitmap
    }

    fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "$fileName.png")
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Metrolist")
            }
            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: throw IllegalStateException("Failed to create new MediaStore record")

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            uri
        } else {
            val cachePath = File(context.cacheDir, "images")
            cachePath.mkdirs()
            val imageFile = File(cachePath, "$fileName.png")
            FileOutputStream(imageFile).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                imageFile
            )
        }
    }
}