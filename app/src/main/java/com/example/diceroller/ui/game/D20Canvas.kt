package com.example.diceroller.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.example.diceroller.ui.theme.DieFaceDark
import com.example.diceroller.ui.theme.DieFaceLight
import com.example.diceroller.ui.theme.DieFaceMid
import com.example.diceroller.ui.theme.DieEdge
import com.example.diceroller.ui.theme.DieEdgeGold
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private class Vec3(val x: Float, val y: Float, val z: Float)
private class FaceDef(val v0: Int, val v1: Int, val v2: Int, val number: Int)

private val PHI = ((1f + sqrt(5f)) / 2f)

private val BASE_VERTICES = arrayOf(
    Vec3(-1f, PHI, 0f),  Vec3(1f, PHI, 0f),
    Vec3(-1f, -PHI, 0f), Vec3(1f, -PHI, 0f),
    Vec3(0f, -1f, PHI),  Vec3(0f, 1f, PHI),
    Vec3(0f, -1f, -PHI), Vec3(0f, 1f, -PHI),
    Vec3(PHI, 0f, -1f),  Vec3(PHI, 0f, 1f),
    Vec3(-PHI, 0f, -1f), Vec3(-PHI, 0f, 1f)
)

private val FACES = arrayOf(
    FaceDef(0, 11, 5, 1),   FaceDef(0, 5, 1, 2),
    FaceDef(0, 1, 7, 3),    FaceDef(0, 7, 10, 4),
    FaceDef(0, 10, 11, 5),  FaceDef(1, 5, 9, 6),
    FaceDef(5, 11, 4, 7),   FaceDef(11, 10, 2, 8),
    FaceDef(10, 7, 6, 9),   FaceDef(7, 1, 8, 10),
    FaceDef(3, 9, 4, 11),   FaceDef(3, 4, 2, 12),
    FaceDef(3, 2, 6, 13),   FaceDef(3, 6, 8, 14),
    FaceDef(3, 8, 9, 15),   FaceDef(4, 9, 5, 16),
    FaceDef(2, 4, 11, 17),  FaceDef(6, 2, 10, 18),
    FaceDef(8, 6, 7, 19),   FaceDef(9, 8, 1, 20)
)

// Precompute face center normals (outward-pointing direction of each face)
// Used to find target rotation that puts rolled face toward camera
private val FACE_NORMALS: Array<Vec3> = Array(20) { i ->
    val f = FACES[i]
    val v0 = BASE_VERTICES[f.v0]; val v1 = BASE_VERTICES[f.v1]; val v2 = BASE_VERTICES[f.v2]
    val cx = (v0.x + v1.x + v2.x) / 3f
    val cy = (v0.y + v1.y + v2.y) / 3f
    val cz = (v0.z + v1.z + v2.z) / 3f
    val len = sqrt(cx * cx + cy * cy + cz * cz)
    Vec3(cx / len, cy / len, cz / len)
}

// Precomputed target rotations for each face (1-20).
// Index 0 unused, indices 1-20 = target (rotX, rotY) in degrees.
// Computed at class load time so roll() is instant.
private val TARGET_ROTATIONS: Array<Pair<Float, Float>> = run {
    val results = Array(21) { 0f to 0f }
    for (fi in FACES.indices) {
        val n = FACE_NORMALS[fi]
        var bestRx = 0f
        var bestRy = 0f
        var bestDot = -2f
        for (rxi in 0 until 72) {
            val rx = Math.toRadians((rxi * 5).toDouble()).toFloat()
            val cRx = cos(rx); val sRx = sin(rx)
            val ny1 = n.y * cRx - n.z * sRx
            val nz1 = n.y * sRx + n.z * cRx
            for (ryi in 0 until 72) {
                val ry = Math.toRadians((ryi * 5).toDouble()).toFloat()
                val nz2 = -n.x * sin(ry) + nz1 * cos(ry)
                if (nz2 > bestDot) {
                    bestDot = nz2
                    bestRx = (rxi * 5).toFloat()
                    bestRy = (ryi * 5).toFloat()
                }
            }
        }
        results[FACES[fi].number] = bestRx to bestRy
    }
    results
}

/** Instant lookup of precomputed target rotation for a face number. */
fun getTargetRotationForFace(faceNumber: Int): Pair<Float, Float> {
    if (faceNumber < 1 || faceNumber > 20) return 0f to 0f
    return TARGET_ROTATIONS[faceNumber]
}

// Reusable Paint objects – created once, properties updated per draw call
private val glowPaint = android.graphics.Paint().apply {
    textAlign = android.graphics.Paint.Align.CENTER
    isFakeBoldText = true
    isAntiAlias = true
}
private val mainOutlinePaint = android.graphics.Paint().apply {
    textAlign = android.graphics.Paint.Align.CENTER
    isFakeBoldText = true
    isAntiAlias = true
    style = android.graphics.Paint.Style.STROKE
}
private val mainFillPaint = android.graphics.Paint().apply {
    textAlign = android.graphics.Paint.Align.CENTER
    isFakeBoldText = true
    isAntiAlias = true
    style = android.graphics.Paint.Style.FILL
}
private val faceOutlinePaint = android.graphics.Paint().apply {
    textAlign = android.graphics.Paint.Align.CENTER
    isFakeBoldText = true
    isAntiAlias = true
    style = android.graphics.Paint.Style.STROKE
}
private val faceFillPaint = android.graphics.Paint().apply {
    textAlign = android.graphics.Paint.Align.CENTER
    isFakeBoldText = true
    isAntiAlias = true
    style = android.graphics.Paint.Style.FILL
}

// Pre-allocated arrays
private val transformedX = FloatArray(12)
private val transformedY = FloatArray(12)
private val transformedZ = FloatArray(12)
private val projectedX = FloatArray(12)
private val projectedY = FloatArray(12)
private val faceDepths = FloatArray(20)
private val faceNormalZ = FloatArray(20)
private val faceBrightness = FloatArray(20)
private val sortedIndices = IntArray(20)

@Composable
fun D20Canvas(
    highlightNumber: Int,
    rotationAngle: Float,
    scale: Float,
    yOffset: Float,
    shadowBlur: Float,
    rotationX: Float = 0f,
    rotationY: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.size(320.dp)
    ) {
        val cx = size.width / 2
        val cy = size.height / 2
        val dieScale = (size.width / (2f * PHI)) * 0.70f * scale

        // Shadow
        drawCircle(
            color = Color.Black.copy(alpha = 0.35f),
            radius = dieScale * PHI * 0.9f + shadowBlur,
            center = Offset(cx, cy + dieScale * 0.8f + shadowBlur * 0.3f),
        )

        translate(top = yOffset) {
            val rx = Math.toRadians(rotationX.toDouble()).toFloat()
            val ry = Math.toRadians(rotationY.toDouble()).toFloat()
            val rz = Math.toRadians(rotationAngle.toDouble()).toFloat()

            val cRx = cos(rx); val sRx = sin(rx)
            val cRy = cos(ry); val sRy = sin(ry)
            val cRz = cos(rz); val sRz = sin(rz)

            for (i in BASE_VERTICES.indices) {
                val v = BASE_VERTICES[i]
                val y1 = v.y * cRx - v.z * sRx
                val z1 = v.y * sRx + v.z * cRx
                val x2 = v.x * cRy + z1 * sRy
                val z2 = -v.x * sRy + z1 * cRy
                transformedX[i] = x2 * cRz - y1 * sRz
                transformedY[i] = x2 * sRz + y1 * cRz
                transformedZ[i] = z2
                projectedX[i] = cx + transformedX[i] * dieScale
                projectedY[i] = cy + transformedY[i] * dieScale
            }

            var visibleCount = 0
            for (i in FACES.indices) {
                val f = FACES[i]
                val v0x = transformedX[f.v0]; val v0y = transformedY[f.v0]; val v0z = transformedZ[f.v0]
                val v1x = transformedX[f.v1]; val v1y = transformedY[f.v1]; val v1z = transformedZ[f.v1]
                val v2x = transformedX[f.v2]; val v2y = transformedY[f.v2]; val v2z = transformedZ[f.v2]

                val ux = v1x - v0x; val uy = v1y - v0y; val uz = v1z - v0z
                val wx = v2x - v0x; val wy = v2y - v0y; val wz = v2z - v0z
                val nz = ux * wy - uy * wx

                // Only draw faces clearly facing camera (threshold > 0.05 eliminates edge-on faces)
                if (nz > 0.05f) {
                    val nx = uy * wz - uz * wy
                    val ny = uz * wx - ux * wz
                    val len = sqrt(nx * nx + ny * ny + nz * nz)
                    val brightness = if (len > 0.001f) nz / len else 0.5f

                    faceDepths[visibleCount] = (v0z + v1z + v2z)
                    faceNormalZ[visibleCount] = nz
                    faceBrightness[visibleCount] = brightness
                    sortedIndices[visibleCount] = i
                    visibleCount++
                }
            }

            // Sort by depth (insertion sort)
            for (i in 1 until visibleCount) {
                val key = sortedIndices[i]
                val keyDepth = faceDepths[i]
                val keyNz = faceNormalZ[i]
                val keyBr = faceBrightness[i]
                var j = i - 1
                while (j >= 0 && faceDepths[j] > keyDepth) {
                    sortedIndices[j + 1] = sortedIndices[j]
                    faceDepths[j + 1] = faceDepths[j]
                    faceNormalZ[j + 1] = faceNormalZ[j]
                    faceBrightness[j + 1] = faceBrightness[j]
                    j--
                }
                sortedIndices[j + 1] = key
                faceDepths[j + 1] = keyDepth
                faceNormalZ[j + 1] = keyNz
                faceBrightness[j + 1] = keyBr
            }

            val path = Path()
            for (idx in 0 until visibleCount) {
                val fi = sortedIndices[idx]
                val f = FACES[fi]
                val brightness = faceBrightness[idx]

                path.reset()
                path.moveTo(projectedX[f.v0], projectedY[f.v0])
                path.lineTo(projectedX[f.v1], projectedY[f.v1])
                path.lineTo(projectedX[f.v2], projectedY[f.v2])
                path.close()

                val faceColor = when {
                    brightness > 0.75f -> DieFaceLight
                    brightness > 0.45f -> DieFaceMid
                    else -> DieFaceDark
                }

                drawPath(path, color = faceColor, style = Fill)
                drawPath(path, color = DieEdge, style = Stroke(width = 3f * scale))
                drawPath(path, color = DieEdgeGold.copy(alpha = 0.35f), style = Stroke(width = 1.5f * scale))

                // Only draw numbers on faces with enough brightness (skip near-edge faces)
                if (brightness > 0.25f) {
                    val tcx = (projectedX[f.v0] + projectedX[f.v1] + projectedX[f.v2]) / 3f
                    val tcy = (projectedY[f.v0] + projectedY[f.v1] + projectedY[f.v2]) / 3f
                    val isMain = highlightNumber != 0 && f.number == highlightNumber
                    val textAlpha = (brightness * 255).toInt().coerceIn(180, 255)
                    val textSz = dieScale * if (isMain) 0.45f else 0.30f

                    if (isMain) {
                        drawMainFaceNumber(tcx, tcy, textSz, f.number)
                    } else {
                        drawFaceNumber(tcx, tcy, textSz, f.number, alpha = textAlpha)
                    }
                }
            }
        }
    }
}

// Highlighted rolled number: gold text with glow and thick dark outline
private fun DrawScope.drawMainFaceNumber(
    cx: Float, cy: Float, textSize: Float, number: Int
) {
    val text = number.toString()
    val textY = cy + textSize / 3f

    // Glow layer (gold, blurred) – reuse cached Paint
    glowPaint.color = android.graphics.Color.argb(180, 255, 215, 0)
    glowPaint.textSize = textSize
    glowPaint.maskFilter = android.graphics.BlurMaskFilter(textSize * 0.15f, android.graphics.BlurMaskFilter.Blur.NORMAL)
    drawContext.canvas.nativeCanvas.drawText(text, cx, textY, glowPaint)

    // Dark outline – reuse cached Paint
    mainOutlinePaint.color = android.graphics.Color.argb(255, 20, 10, 0)
    mainOutlinePaint.textSize = textSize
    mainOutlinePaint.strokeWidth = textSize * 0.14f
    drawContext.canvas.nativeCanvas.drawText(text, cx, textY, mainOutlinePaint)

    // Gold fill – reuse cached Paint
    mainFillPaint.color = android.graphics.Color.argb(255, 255, 215, 0)
    mainFillPaint.textSize = textSize
    drawContext.canvas.nativeCanvas.drawText(text, cx, textY, mainFillPaint)
}

// Other face numbers: white with dark outline, smaller
private fun DrawScope.drawFaceNumber(
    cx: Float, cy: Float, textSize: Float, number: Int,
    alpha: Int = 255
) {
    val text = number.toString()
    val textY = cy + textSize / 3f

    // Dark outline – reuse cached Paint
    faceOutlinePaint.color = android.graphics.Color.argb(alpha, 0, 0, 0)
    faceOutlinePaint.textSize = textSize
    faceOutlinePaint.strokeWidth = textSize * 0.10f
    drawContext.canvas.nativeCanvas.drawText(text, cx, textY, faceOutlinePaint)

    // White fill – reuse cached Paint
    faceFillPaint.color = android.graphics.Color.argb(alpha, 255, 255, 255)
    faceFillPaint.textSize = textSize
    drawContext.canvas.nativeCanvas.drawText(text, cx, textY, faceFillPaint)
}
