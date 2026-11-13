package com.example.puzzlecounter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import kotlin.random.Random

object TestImages {

    /** A photo-like scene: a few separated rounded-rect "pieces" on a wooden table tone. */
    fun fakePuzzlePhoto(size: Int = 1080, pieceCount: Int = 12): Bitmap {
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(90, 70, 50))
        val random = Random(42)
        val paint = Paint().apply { isAntiAlias = true }
        repeat(pieceCount) {
            val w = size * 0.08f
            val h = size * 0.06f
            val cx = random.nextFloat() * (size - w) + w / 2f
            val cy = random.nextFloat() * (size - h) + h / 2f
            paint.color = Color.rgb(150 + random.nextInt(100), 150 + random.nextInt(100), 150 + random.nextInt(100))
            canvas.drawRoundRect(RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f), 8f, 8f, paint)
        }
        return bitmap
    }
}
