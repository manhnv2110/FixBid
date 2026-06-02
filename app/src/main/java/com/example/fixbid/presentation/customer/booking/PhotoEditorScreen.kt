package com.example.fixbid.presentation.customer.booking

import android.graphics.Bitmap
import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LensBlur
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fixbid.core.utils.BitmapUtils
import com.example.fixbid.core.utils.EditPoint
import com.example.fixbid.core.utils.EditStroke
import com.example.fixbid.core.utils.SpotlightShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Inline full-screen photo editor used by the booking flow so customers can
 * mark up the photo of the issue before sending it to workers.
 *
 * Two tools are provided:
 *  - **Brush**: free-hand strokes in 5 selectable colours with adjustable
 *    width. Each stroke is undo-able.
 *  - **Spotlight**: drag a rectangular or oval region; everything outside
 *    the region is blurred and dimmed when the result is saved.
 *
 * The editor renders the live preview entirely in Compose; the heavyweight
 * bitmap operations (loading the source, applying the spotlight blur, baking
 * strokes onto the final JPEG) only run on Save, off the main thread.
 */
@Composable
fun PhotoEditorScreen(
    sourceUri: Uri,
    onCancel: () -> Unit,
    onSave: (Uri) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(sourceUri) {
        val bmp = withContext(Dispatchers.IO) {
            BitmapUtils.loadOriented(context, sourceUri)
        }
        if (bmp != null) {
            sourceBitmap = bmp
        } else {
            android.util.Log.w("PhotoEditor", "Could not load bitmap from $sourceUri")
            loadError = "Không thể mở ảnh này. Hãy thử chọn ảnh khác từ thư viện."
        }
    }

    // ── Editor state ─────────────────────────────────────────────────────────
    var tool by remember { mutableStateOf(EditorTool.BRUSH) }
    var brushColor by remember { mutableStateOf(BrushColor.RED) }
    var brushWidthDp by remember { mutableFloatStateOf(8f) }
    var spotlightShape by remember { mutableStateOf(SpotlightKind.OVAL) }

    // Strokes are stored in image-pixel coords (transformed from canvas coords
    // when the gesture ends) so saving doesn't depend on the canvas size.
    val strokes = remember { mutableStateListOf<EditStroke>() }
    var inProgressPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    // Spotlight region in canvas coords while the user drags it.
    var spotlightCanvasRect by remember { mutableStateOf<RectF?>(null) }
    var spotlightStart by remember { mutableStateOf<Offset?>(null) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            EditorTopBar(
                onCancel = onCancel,
                onUndo = {
                    if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                    spotlightCanvasRect = null
                },
                onReset = {
                    strokes.clear()
                    spotlightCanvasRect = null
                },
                onSave = save@{
                    val bmp = sourceBitmap ?: return@save
                    if (isSaving) return@save
                    isSaving = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            // Convert canvas-space artefacts into image-space.
                            val imageStrokes = strokes
                            val imageSpotlight = spotlightCanvasRect?.let {
                                val mapped = mapCanvasRectToImage(
                                    canvasRect = it,
                                    canvasSize = canvasSize,
                                    imageWidth = bmp.width,
                                    imageHeight = bmp.height
                                )
                                when (spotlightShape) {
                                    SpotlightKind.OVAL -> SpotlightShape.Oval(mapped)
                                    SpotlightKind.RECT -> SpotlightShape.Rect(mapped)
                                }
                            }
                            val rendered = BitmapUtils.renderEdits(bmp, imageStrokes, imageSpotlight)
                            val uri = BitmapUtils.saveToCache(context, rendered)
                            if (rendered !== bmp) rendered.recycle()
                            uri
                        }
                        isSaving = false
                        Toast.makeText(context, "Đã lưu ảnh đã chỉnh sửa", Toast.LENGTH_SHORT).show()
                        onSave(result)
                    }
                },
                isSaving = isSaving,
                hasContent = strokes.isNotEmpty() || spotlightCanvasRect != null
            )
        },
        bottomBar = {
            EditorBottomBar(
                tool = tool,
                onToolChange = { newTool ->
                    tool = newTool
                    // Discard any half-drawn spotlight when switching tools.
                    if (newTool != EditorTool.SPOTLIGHT) {
                        spotlightStart = null
                    }
                },
                brushColor = brushColor,
                onBrushColorChange = { brushColor = it },
                brushWidthDp = brushWidthDp,
                onBrushWidthChange = { brushWidthDp = it },
                spotlightShape = spotlightShape,
                onSpotlightShapeChange = { spotlightShape = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            when {
                loadError != null -> {
                    Text(
                        text = loadError!!,
                        color = Color.White,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                sourceBitmap == null -> {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                else -> {
                    val bmp = sourceBitmap!!
                    val aspect = bmp.width.toFloat() / bmp.height.toFloat()
                    val brushWidthPx = with(density) { brushWidthDp.dp.toPx() }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Letterbox the image to its natural aspect ratio so the
                        // canvas overlays line up perfectly with displayed pixels.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspect)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1A1A1A))
                                .onSizeChanged { canvasSize = it }
                                .pointerInput(tool, brushColor, brushWidthPx, spotlightShape) {
                                    when (tool) {
                                        EditorTool.BRUSH -> {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    inProgressPoints = listOf(offset)
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    inProgressPoints =
                                                        inProgressPoints + change.position
                                                },
                                                onDragEnd = {
                                                    if (inProgressPoints.size >= 2 && canvasSize.width > 0 && canvasSize.height > 0) {
                                                        val imageStroke = EditStroke(
                                                            points = inProgressPoints.map {
                                                                mapCanvasPointToImage(
                                                                    it,
                                                                    canvasSize,
                                                                    bmp.width,
                                                                    bmp.height
                                                                )
                                                            },
                                                            color = brushColor.color.toArgb(),
                                                            widthPx = mapStrokeWidth(
                                                                brushWidthPx,
                                                                canvasSize,
                                                                bmp
                                                            )
                                                        )
                                                        strokes.add(imageStroke)
                                                    }
                                                    inProgressPoints = emptyList()
                                                },
                                                onDragCancel = { inProgressPoints = emptyList() }
                                            )
                                        }
                                        EditorTool.SPOTLIGHT -> {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    spotlightStart = offset
                                                    spotlightCanvasRect = RectF(
                                                        offset.x, offset.y,
                                                        offset.x, offset.y
                                                    )
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val start = spotlightStart ?: return@detectDragGestures
                                                    val end = change.position
                                                    spotlightCanvasRect = RectF(
                                                        min(start.x, end.x),
                                                        min(start.y, end.y),
                                                        max(start.x, end.x),
                                                        max(start.y, end.y)
                                                    )
                                                },
                                                onDragEnd = {
                                                    spotlightCanvasRect?.let {
                                                        if (it.width() < 16f || it.height() < 16f) {
                                                            spotlightCanvasRect = null
                                                        }
                                                    }
                                                    spotlightStart = null
                                                },
                                                onDragCancel = {
                                                    spotlightCanvasRect = null
                                                    spotlightStart = null
                                                }
                                            )
                                        }
                                    }
                                }
                        ) {
                            // Background image
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            // Live preview overlay
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                // Already-committed strokes (image space → canvas space)
                                strokes.forEach { stroke ->
                                    val canvasPoints = stroke.points.map { p ->
                                        mapImagePointToCanvas(
                                            EditPoint(p.x, p.y),
                                            IntSize(size.width.toInt(), size.height.toInt()),
                                            bmp.width,
                                            bmp.height
                                        )
                                    }
                                    if (canvasPoints.size >= 2) {
                                        val path = Path().apply {
                                            moveTo(canvasPoints.first().x, canvasPoints.first().y)
                                            for (i in 1 until canvasPoints.size) {
                                                lineTo(canvasPoints[i].x, canvasPoints[i].y)
                                            }
                                        }
                                        drawPath(
                                            path = path,
                                            color = Color(stroke.color),
                                            style = Stroke(
                                                width = invertStrokeWidth(
                                                    stroke.widthPx,
                                                    IntSize(size.width.toInt(), size.height.toInt()),
                                                    bmp
                                                ),
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }

                                // Stroke being drawn right now
                                if (inProgressPoints.size >= 2) {
                                    val path = Path().apply {
                                        moveTo(inProgressPoints.first().x, inProgressPoints.first().y)
                                        for (i in 1 until inProgressPoints.size) {
                                            lineTo(inProgressPoints[i].x, inProgressPoints[i].y)
                                        }
                                    }
                                    drawPath(
                                        path = path,
                                        color = brushColor.color,
                                        style = Stroke(
                                            width = brushWidthPx,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }

                                // Spotlight preview: dim everything outside, sharp window inside.
                                spotlightCanvasRect?.let { rect ->
                                    val rectSize = Size(rect.width(), rect.height())
                                    val topLeft = Offset(rect.left, rect.top)

                                    // Dim outside via clipPath inversion.
                                    val outsidePath = Path().apply {
                                        addRect(
                                            androidx.compose.ui.geometry.Rect(
                                                offset = Offset(0f, 0f),
                                                size = Size(size.width, size.height)
                                            )
                                        )
                                        when (spotlightShape) {
                                            SpotlightKind.OVAL -> addOval(
                                                androidx.compose.ui.geometry.Rect(topLeft, rectSize)
                                            )
                                            SpotlightKind.RECT -> addRoundRect(
                                                androidx.compose.ui.geometry.RoundRect(
                                                    rect = androidx.compose.ui.geometry.Rect(topLeft, rectSize),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
                                                )
                                            )
                                        }
                                        fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                                    }
                                    drawPath(path = outsidePath, color = Color.Black.copy(alpha = 0.55f))

                                    // Outline of the spotlight region.
                                    val outline = Path().apply {
                                        when (spotlightShape) {
                                            SpotlightKind.OVAL -> addOval(
                                                androidx.compose.ui.geometry.Rect(topLeft, rectSize)
                                            )
                                            SpotlightKind.RECT -> addRoundRect(
                                                androidx.compose.ui.geometry.RoundRect(
                                                    rect = androidx.compose.ui.geometry.Rect(topLeft, rectSize),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(24f, 24f)
                                                )
                                            )
                                        }
                                    }
                                    drawPath(
                                        path = outline,
                                        color = Color.White,
                                        style = Stroke(width = 3f)
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

// ─── Top bar ─────────────────────────────────────────────────────────────────

@Composable
private fun EditorTopBar(
    onCancel: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onSave: () -> Unit,
    isSaving: Boolean,
    hasContent: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF111111),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Huỷ",
                    tint = Color.White
                )
            }
            Text(
                text = "Chỉnh sửa ảnh",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp)
            )
            IconButton(onClick = onUndo, enabled = hasContent) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Hoàn tác",
                    tint = if (hasContent) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
            IconButton(onClick = onReset, enabled = hasContent) {
                Icon(
                    Icons.Outlined.RestartAlt,
                    contentDescription = "Đặt lại",
                    tint = if (hasContent) Color.White else Color.White.copy(alpha = 0.4f)
                )
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onSave,
                enabled = !isSaving,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Lưu", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── Bottom bar ──────────────────────────────────────────────────────────────

@Composable
private fun EditorBottomBar(
    tool: EditorTool,
    onToolChange: (EditorTool) -> Unit,
    brushColor: BrushColor,
    onBrushColorChange: (BrushColor) -> Unit,
    brushWidthDp: Float,
    onBrushWidthChange: (Float) -> Unit,
    spotlightShape: SpotlightKind,
    onSpotlightShapeChange: (SpotlightKind) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF111111),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Tool-specific controls
            when (tool) {
                EditorTool.BRUSH -> {
                    Text(
                        text = "Khoanh vùng cần sửa",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(BrushColor.entries.size) { i ->
                                val c = BrushColor.entries[i]
                                ColorSwatch(
                                    color = c.color,
                                    selected = c == brushColor,
                                    onClick = { onBrushColorChange(c) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Cỡ nét",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            modifier = Modifier.width(50.dp)
                        )
                        Slider(
                            value = brushWidthDp,
                            onValueChange = onBrushWidthChange,
                            valueRange = 3f..28f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size((brushWidthDp + 4).dp)
                                .clip(CircleShape)
                                .background(brushColor.color),
                        )
                    }
                }
                EditorTool.SPOTLIGHT -> {
                    Text(
                        text = "Kéo để chọn vùng cần làm nổi bật",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ShapeChip(
                            label = "Hình tròn",
                            icon = Icons.Outlined.RadioButtonUnchecked,
                            selected = spotlightShape == SpotlightKind.OVAL,
                            onClick = { onSpotlightShapeChange(SpotlightKind.OVAL) }
                        )
                        ShapeChip(
                            label = "Hình chữ nhật",
                            icon = Icons.Outlined.CropSquare,
                            selected = spotlightShape == SpotlightKind.RECT,
                            onClick = { onSpotlightShapeChange(SpotlightKind.RECT) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Spacer(Modifier.height(10.dp))

            // Tool selector tabs (always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToolTab(
                    modifier = Modifier.weight(1f),
                    label = "Khoanh vùng",
                    icon = Icons.Filled.Brush,
                    selected = tool == EditorTool.BRUSH,
                    onClick = { onToolChange(EditorTool.BRUSH) }
                )
                ToolTab(
                    modifier = Modifier.weight(1f),
                    label = "Làm nổi bật",
                    icon = Icons.Filled.LensBlur,
                    selected = tool == EditorTool.SPOTLIGHT,
                    onClick = { onToolChange(EditorTool.SPOTLIGHT) }
                )
            }
        }
    }
}

@Composable
private fun ToolTab(
    modifier: Modifier = Modifier,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary
        else Color.White.copy(alpha = 0.08f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(34.dp),
        shape = CircleShape,
        color = color,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            width = 3.dp,
            color = Color.White
        ) else null,
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Đã chọn",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ShapeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primary
        else Color.White.copy(alpha = 0.08f),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─── Geometry helpers ────────────────────────────────────────────────────────

/**
 * Convert a canvas-space point (the coordinate system the user draws in) to
 * the underlying image's pixel coordinate system. The image is drawn with
 * `ContentScale.Fit` inside the canvas, so we have to factor in the letterbox
 * margins on either the X or the Y axis.
 */
private fun mapCanvasPointToImage(
    canvasPoint: Offset,
    canvasSize: IntSize,
    imageWidth: Int,
    imageHeight: Int
): EditPoint {
    if (canvasSize.width == 0 || canvasSize.height == 0) return EditPoint(0f, 0f)
    val canvasAspect = canvasSize.width.toFloat() / canvasSize.height.toFloat()
    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
    return if (imageAspect > canvasAspect) {
        // Image is wider — letterboxed top and bottom.
        val displayedHeight = canvasSize.width / imageAspect
        val verticalPad = (canvasSize.height - displayedHeight) / 2f
        val scale = imageWidth.toFloat() / canvasSize.width
        EditPoint(
            x = (canvasPoint.x * scale).coerceIn(0f, imageWidth.toFloat()),
            y = ((canvasPoint.y - verticalPad) * scale).coerceIn(0f, imageHeight.toFloat())
        )
    } else {
        val displayedWidth = canvasSize.height * imageAspect
        val horizontalPad = (canvasSize.width - displayedWidth) / 2f
        val scale = imageHeight.toFloat() / canvasSize.height
        EditPoint(
            x = ((canvasPoint.x - horizontalPad) * scale).coerceIn(0f, imageWidth.toFloat()),
            y = (canvasPoint.y * scale).coerceIn(0f, imageHeight.toFloat())
        )
    }
}

/** Inverse of [mapCanvasPointToImage] — used to render saved strokes. */
private fun mapImagePointToCanvas(
    imagePoint: EditPoint,
    canvasSize: IntSize,
    imageWidth: Int,
    imageHeight: Int
): Offset {
    if (imageWidth == 0 || imageHeight == 0) return Offset.Zero
    val canvasAspect = canvasSize.width.toFloat() / canvasSize.height.toFloat()
    val imageAspect = imageWidth.toFloat() / imageHeight.toFloat()
    return if (imageAspect > canvasAspect) {
        val displayedHeight = canvasSize.width / imageAspect
        val verticalPad = (canvasSize.height - displayedHeight) / 2f
        val scale = canvasSize.width.toFloat() / imageWidth
        Offset(
            x = imagePoint.x * scale,
            y = imagePoint.y * scale + verticalPad
        )
    } else {
        val displayedWidth = canvasSize.height * imageAspect
        val horizontalPad = (canvasSize.width - displayedWidth) / 2f
        val scale = canvasSize.height.toFloat() / imageHeight
        Offset(
            x = imagePoint.x * scale + horizontalPad,
            y = imagePoint.y * scale
        )
    }
}

private fun mapCanvasRectToImage(
    canvasRect: RectF,
    canvasSize: IntSize,
    imageWidth: Int,
    imageHeight: Int
): RectF {
    val tl = mapCanvasPointToImage(
        Offset(canvasRect.left, canvasRect.top), canvasSize, imageWidth, imageHeight
    )
    val br = mapCanvasPointToImage(
        Offset(canvasRect.right, canvasRect.bottom), canvasSize, imageWidth, imageHeight
    )
    return RectF(
        min(tl.x, br.x),
        min(tl.y, br.y),
        max(tl.x, br.x),
        max(tl.y, br.y)
    )
}

/** Convert canvas-space stroke width to image-space stroke width. */
private fun mapStrokeWidth(canvasWidthPx: Float, canvasSize: IntSize, image: Bitmap): Float {
    if (canvasSize.width == 0 || canvasSize.height == 0) return canvasWidthPx
    val canvasAspect = canvasSize.width.toFloat() / canvasSize.height.toFloat()
    val imageAspect = image.width.toFloat() / image.height.toFloat()
    val scale = if (imageAspect > canvasAspect) {
        image.width.toFloat() / canvasSize.width
    } else {
        image.height.toFloat() / canvasSize.height
    }
    return canvasWidthPx * scale
}

/** Inverse — image-space stroke width → canvas-space stroke width for preview. */
private fun invertStrokeWidth(imageWidthPx: Float, canvasSize: IntSize, image: Bitmap): Float {
    if (image.width == 0 || image.height == 0) return imageWidthPx
    val canvasAspect = canvasSize.width.toFloat() / canvasSize.height.toFloat()
    val imageAspect = image.width.toFloat() / image.height.toFloat()
    val scale = if (imageAspect > canvasAspect) {
        canvasSize.width.toFloat() / image.width
    } else {
        canvasSize.height.toFloat() / image.height
    }
    return imageWidthPx * scale
}

// ─── Tool / colour types ─────────────────────────────────────────────────────

private enum class EditorTool { BRUSH, SPOTLIGHT }

private enum class SpotlightKind { OVAL, RECT }

private enum class BrushColor(val color: Color) {
    RED(Color(0xFFE53935)),
    YELLOW(Color(0xFFFFC107)),
    GREEN(Color(0xFF43A047)),
    BLUE(Color(0xFF1E88E5)),
    BLACK(Color(0xFF111111))
}
