package com.example.fixbid.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Helpers for the in-app photo editor (annotations + spotlight blur).
 *
 * The editor doesn't ship `androidx.exifinterface` to avoid extra dependency
 * weight; we read EXIF orientation directly from the input stream when
 * possible so the loaded bitmap matches what the user saw in the gallery.
 */
object BitmapUtils {

    /** Maximum edge length the editor downsamples large photos to, in pixels. */
    private const val MAX_EDIT_DIMENSION = 2048

    /**
     * Loads a Uri into an upright bitmap, downsampled if the source is huge
     * so the editor stays responsive. Returns `null` if the Uri can't be
     * read (deleted file, permission revoked, etc.).
     *
     * To stay robust on emulators where reopening a `content://` Uri after
     * the first read can return null (PhotoPicker sometimes refuses a second
     * `openInputStream`), we slurp the bytes into memory once and decode
     * from that buffer for both the bounds pass and the real pass.
     */
    fun loadOriented(context: Context, uri: Uri): Bitmap? {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null

        // First pass — bounds only — to compute a power-of-two sample size.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val sampled = if (bounds.outWidth > 0 && bounds.outHeight > 0) {
            val sample = run {
                var s = 1
                val maxSide = max(bounds.outWidth, bounds.outHeight)
                while (maxSide / s > MAX_EDIT_DIMENSION) s *= 2
                s
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        } else null

        // Fallback path: full decode then scale down if necessary.
        val raw = sampled ?: BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null

        val maxSide = max(raw.width, raw.height)
        val downsized = if (maxSide > MAX_EDIT_DIMENSION) {
            val scale = MAX_EDIT_DIMENSION.toFloat() / maxSide
            val targetW = max(1, (raw.width * scale).toInt())
            val targetH = max(1, (raw.height * scale).toInt())
            Bitmap.createScaledBitmap(raw, targetW, targetH, true).also {
                if (it !== raw) raw.recycle()
            }
        } else raw

        // Read EXIF orientation directly from the in-memory buffer so we
        // don't have to reopen the Uri a third time.
        val rotation = readOrientationDegrees(bytes)
        return if (rotation == 0) downsized else rotate(downsized, rotation.toFloat()).also {
            if (it !== downsized) downsized.recycle()
        }
    }

    private fun readOrientationDegrees(bytes: ByteArray): Int = runCatching {
        java.io.ByteArrayInputStream(bytes).use {
            val exif = ExifInterface(it)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }
    }.getOrDefault(0)

    private fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees == 0f) return src
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * Renders an edit list (annotation strokes + an optional spotlight) on
     * top of the provided bitmap and returns a new ARGB_8888 bitmap. The
     * input is not mutated.
     */
    fun renderEdits(
        source: Bitmap,
        strokes: List<EditStroke>,
        spotlight: SpotlightShape?
    ): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        if (spotlight != null) {
            // Bake spotlight first: blurred copy underneath, sharp cutout on top
            // following the spotlight shape.
            drawSpotlight(canvas, source, spotlight)
        } else {
            canvas.drawBitmap(source, 0f, 0f, null)
        }

        // Strokes are painted on top so highlights stay visible after the
        // spotlight has been baked.
        if (strokes.isNotEmpty()) {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            strokes.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                val path = Path().apply {
                    val first = stroke.points.first()
                    moveTo(first.x, first.y)
                    for (i in 1 until stroke.points.size) {
                        val p = stroke.points[i]
                        lineTo(p.x, p.y)
                    }
                }
                strokePaint.color = stroke.color
                strokePaint.strokeWidth = stroke.widthPx
                canvas.drawPath(path, strokePaint)
            }
        }

        return output
    }

    private fun drawSpotlight(
        canvas: Canvas,
        source: Bitmap,
        spotlight: SpotlightShape
    ) {
        // Step 1: blurred backdrop covering the whole image.
        val blurred = blur(source, radius = spotlight.blurRadiusPx)
        canvas.drawBitmap(blurred, 0f, 0f, null)
        if (blurred !== source) blurred.recycle()

        // Step 2: punch a sharp window through with the original pixels.
        val saveCount = canvas.saveLayer(null, null)
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (spotlight) {
            is SpotlightShape.Oval -> canvas.drawOval(spotlight.rect, maskPaint)
            is SpotlightShape.Rect -> canvas.drawRoundRect(spotlight.rect, spotlight.cornerRadius, spotlight.cornerRadius, maskPaint)
        }
        maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, maskPaint)
        canvas.restoreToCount(saveCount)

        // Step 3: dim the blurred background so the spotlight reads stronger.
        val dimPaint = Paint().apply {
            color = Color.argb(70, 0, 0, 0)
        }
        val dimSave = canvas.saveLayer(null, null)
        canvas.drawRect(0f, 0f, source.width.toFloat(), source.height.toFloat(), dimPaint)
        // Cut the dim back out of the spotlight area so it stays bright.
        val cutoutPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            color = Color.WHITE
        }
        when (spotlight) {
            is SpotlightShape.Oval -> canvas.drawOval(spotlight.rect, cutoutPaint)
            is SpotlightShape.Rect -> canvas.drawRoundRect(spotlight.rect, spotlight.cornerRadius, spotlight.cornerRadius, cutoutPaint)
        }
        canvas.restoreToCount(dimSave)
    }

    /**
     * Software box-blur with two passes. We avoid `RenderEffect` because it
     * requires API 31; the box approach runs purely on the CPU and works
     * down to minSdk 26 without extra dependencies. For a chunky photo this
     * runs in well under 100ms on midrange devices because the bitmap is
     * already downsampled to ≤2048px on its longest side.
     */
    fun blur(source: Bitmap, radius: Int): Bitmap {
        if (radius <= 0) return source
        val r = radius.coerceIn(1, 25)

        // Operate on a downsampled copy first so the blur is dramatic without
        // hammering the CPU on huge images, then upscale.
        val downscale = 4
        val w = max(1, source.width / downscale)
        val h = max(1, source.height / downscale)

        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = IntArray(w * h)
        boxBlurHorizontal(pixels, out, w, h, r)
        boxBlurVertical(out, pixels, w, h, r)

        small.setPixels(pixels, 0, w, 0, 0, w, h)
        val upscaled = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        if (upscaled !== small) small.recycle()
        return upscaled
    }

    private fun boxBlurHorizontal(input: IntArray, output: IntArray, w: Int, h: Int, r: Int) {
        val window = 2 * r + 1
        for (y in 0 until h) {
            val rowStart = y * w
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
            // Prime the window using the leftmost pixel for the negative range.
            val leftClamp = input[rowStart]
            for (i in -r..r) {
                val idx = rowStart + i.coerceIn(0, w - 1)
                val c = if (i < 0) leftClamp else input[idx]
                sumA += (c ushr 24) and 0xFF
                sumR += (c ushr 16) and 0xFF
                sumG += (c ushr 8) and 0xFF
                sumB += c and 0xFF
            }
            for (x in 0 until w) {
                output[rowStart + x] =
                    ((sumA / window) shl 24) or
                        ((sumR / window) shl 16) or
                        ((sumG / window) shl 8) or
                        (sumB / window)
                val outX = x - r
                val inX = x + r + 1
                val outC = input[rowStart + outX.coerceIn(0, w - 1)]
                val inC = input[rowStart + inX.coerceIn(0, w - 1)]
                sumA += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
                sumR += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
                sumG += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
                sumB += (inC and 0xFF) - (outC and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(input: IntArray, output: IntArray, w: Int, h: Int, r: Int) {
        val window = 2 * r + 1
        for (x in 0 until w) {
            var sumR = 0; var sumG = 0; var sumB = 0; var sumA = 0
            val topClamp = input[x]
            for (i in -r..r) {
                val rowIdx = i.coerceIn(0, h - 1)
                val c = if (i < 0) topClamp else input[rowIdx * w + x]
                sumA += (c ushr 24) and 0xFF
                sumR += (c ushr 16) and 0xFF
                sumG += (c ushr 8) and 0xFF
                sumB += c and 0xFF
            }
            for (y in 0 until h) {
                output[y * w + x] =
                    ((sumA / window) shl 24) or
                        ((sumR / window) shl 16) or
                        ((sumG / window) shl 8) or
                        (sumB / window)
                val outY = y - r
                val inY = y + r + 1
                val outC = input[outY.coerceIn(0, h - 1) * w + x]
                val inC = input[inY.coerceIn(0, h - 1) * w + x]
                sumA += ((inC ushr 24) and 0xFF) - ((outC ushr 24) and 0xFF)
                sumR += ((inC ushr 16) and 0xFF) - ((outC ushr 16) and 0xFF)
                sumG += ((inC ushr 8) and 0xFF) - ((outC ushr 8) and 0xFF)
                sumB += (inC and 0xFF) - (outC and 0xFF)
            }
        }
    }

    /**
     * Persists the edited bitmap to the app's cache directory as a JPEG and
     * returns a `file://` Uri the caller can hand back to the booking flow.
     * The resulting Uri is consumed by [android.content.ContentResolver.openInputStream]
     * just like a gallery URI, so no FileProvider configuration is required.
     */
    fun saveToCache(context: Context, bitmap: Bitmap, quality: Int = 92): Uri {
        val dir = File(context.cacheDir, "photo_edits").apply { mkdirs() }
        val file = File(dir, "edit_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, min(100, max(50, quality)), it)
            it.flush()
        }
        return Uri.fromFile(file)
    }
}

/** A single freehand annotation stroke in image-pixel coordinates. */
data class EditStroke(
    val points: List<EditPoint>,
    val color: Int,
    val widthPx: Float
)

data class EditPoint(val x: Float, val y: Float)

/** Spotlight shape and blur strength in image-pixel coordinates. */
sealed interface SpotlightShape {
    val rect: RectF
    val blurRadiusPx: Int

    data class Oval(
        override val rect: RectF,
        override val blurRadiusPx: Int = 14
    ) : SpotlightShape

    data class Rect(
        override val rect: RectF,
        val cornerRadius: Float = 24f,
        override val blurRadiusPx: Int = 14
    ) : SpotlightShape
}
