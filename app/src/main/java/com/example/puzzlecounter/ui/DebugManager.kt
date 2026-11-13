package com.example.puzzlecounter.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.core.content.edit
import com.example.puzzlecounter.analyzer.DetectionResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

data class DebugScanSession(
    val id: String,
    val rawPath: String,
    val maskPath: String,
    val annotPath: String,
    val pieceCount: Int,
    /** Blobs that hold several touching pieces. */
    val clusterCount: Int,
)

/** Stores the images and counts of the last [MAX_SESSIONS] scans for the developer inspector. */
class DebugManager(context: Context) {
    private val debugDir = File(context.filesDir, "debug_scans").apply { mkdirs() }
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveSession(scaledRaw: Bitmap, result: DetectionResult): DebugScanSession {
        val id = System.currentTimeMillis().toString()
        val rawFile = File(debugDir, "raw_$id.jpg")
        val maskFile = File(debugDir, "mask_$id.png")
        val annotFile = File(debugDir, "annot_$id.jpg")

        rawFile.outputStream().use { scaledRaw.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        maskFile.outputStream().use { result.maskImage.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val annotated = createAnnotatedBitmap(scaledRaw, result)
        annotFile.outputStream().use { annotated.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        annotated.recycle()

        val session = DebugScanSession(
            id = id,
            rawPath = rawFile.absolutePath,
            maskPath = maskFile.absolutePath,
            annotPath = annotFile.absolutePath,
            pieceCount = result.pieceCount,
            clusterCount = result.clusterCount
        )

        val sessions = listOf(session) + getSessions()
        sessions.drop(MAX_SESSIONS).forEach { deleteFiles(it.id) }
        saveSessions(sessions.take(MAX_SESSIONS))
        return session
    }

    fun getSessions(): List<DebugScanSession> {
        val json = prefs.getString(KEY_SESSIONS, null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.getJSONObject(i)
                DebugScanSession(
                    id = obj.getString("id"),
                    rawPath = obj.optString("rawPath"),
                    maskPath = obj.optString("maskPath"),
                    annotPath = obj.optString("annotPath"),
                    pieceCount = obj.optInt(KEY_PIECE_COUNT),
                    clusterCount = obj.optInt(KEY_CLUSTER_COUNT)
                ).takeIf { s -> listOf(s.rawPath, s.maskPath, s.annotPath).any { File(it).exists() } }
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Gespeicherte Debug-Sessions sind unlesbar und werden ignoriert", e)
            emptyList()
        }
    }

    fun clearAll() {
        debugDir.listFiles()?.forEach { it.delete() }
        prefs.edit { remove(KEY_SESSIONS) }
    }

    private fun saveSessions(sessions: List<DebugScanSession>) {
        val array = JSONArray()
        for (s in sessions) {
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("rawPath", s.rawPath)
                    .put("maskPath", s.maskPath)
                    .put("annotPath", s.annotPath)
                    .put(KEY_PIECE_COUNT, s.pieceCount)
                    .put(KEY_CLUSTER_COUNT, s.clusterCount)
            )
        }
        prefs.edit { putString(KEY_SESSIONS, array.toString()) }
    }

    /** Deletes every image of a session (files are named `<kind>_<id>.<ext>`). */
    private fun deleteFiles(id: String) {
        debugDir.listFiles { file -> file.nameWithoutExtension.endsWith("_$id") }?.forEach { it.delete() }
    }

    /**
     * The scan photo with every counted piece outlined and numbered, so that the count can be checked by eye. The
     * numbers run from top to bottom and from left to right (in rows as tall as a piece); a group of overlapping
     * pieces carries the numbers of all pieces it stands for ("18-20"), the last number is the total. Thin
     * outlines mark blobs that lie on a larger one and are counted with it.
     */
    private fun createAnnotatedBitmap(original: Bitmap, result: DetectionResult): Bitmap {
        val bmp = original.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(bmp)

        val shadow = strokePaint(Color.BLACK, 5f)
        val singleFill = fillPaint(SINGLE_COLOR, 28)
        val singleStroke = strokePaint(SINGLE_COLOR, 2.5f)
        val clusterFill = fillPaint(CLUSTER_COLOR, 50)
        val clusterStroke = strokePaint(CLUSTER_COLOR, 3.5f)
        val insideStroke = strokePaint(INSIDE_COLOR, 2f)
        val numberText = textPaint(15f).apply { typeface = Typeface.DEFAULT_BOLD }

        val medianArea = result.pieces.map { it.area }.sorted().let { it.getOrElse(it.size / 2) { 0 } }
        val rowHeight = sqrt(medianArea.toFloat()).coerceAtLeast(1f)
        val inReadingOrder = result.pieces.sortedWith(compareBy({ (it.rect.exactCenterY() / rowHeight).toInt() }, { it.rect.exactCenterX() }))

        var number = 1
        for (piece in inReadingOrder) {
            val label = when (piece.pieceCount) {
                0 -> null
                1 -> "$number"
                else -> "$number-${number + piece.pieceCount - 1}"
            }
            when (piece.pieceCount) {
                0 -> canvas.drawPath(piece.path, insideStroke)
                1 -> {
                    canvas.drawPath(piece.path, singleFill)
                    canvas.drawPath(piece.path, shadow)
                    canvas.drawPath(piece.path, singleStroke)
                }
                else -> {
                    canvas.drawPath(piece.path, clusterFill)
                    canvas.drawPath(piece.path, shadow)
                    canvas.drawPath(piece.path, clusterStroke)
                }
            }
            if (label != null) {
                canvas.drawText(label, piece.rect.exactCenterX() - numberText.measureText(label) / 2f, piece.rect.exactCenterY() + numberText.textSize / 3f, numberText)
            }
            number += piece.pieceCount
        }
        return bmp
    }

    private fun strokePaint(argb: Int, width: Float) =
        Paint().apply { color = argb; style = Paint.Style.STROKE; strokeWidth = width; isAntiAlias = true }

    private fun fillPaint(argb: Int, alphaValue: Int) =
        Paint().apply { color = argb; alpha = alphaValue; style = Paint.Style.FILL }

    private fun textPaint(size: Float) =
        Paint().apply { color = Color.WHITE; textSize = size; isAntiAlias = true; setShadowLayer(3f, 1f, 1f, Color.BLACK) }

    private companion object {
        const val TAG = "DebugManager"
        const val PREFS_NAME = "debug_manager_prefs"
        const val KEY_SESSIONS = "sessions_json"
        const val MAX_SESSIONS = 10

        // The key of the piece count is "validPieces" so that sessions saved by earlier versions stay readable.
        const val KEY_PIECE_COUNT = "validPieces"
        const val KEY_CLUSTER_COUNT = "clusterCount"

        const val SINGLE_COLOR = 0xFF00FF66.toInt()
        const val CLUSTER_COLOR = 0xFFFFB300.toInt()
        const val INSIDE_COLOR = 0xFFFF00D4.toInt()
    }
}
