package com.example.puzzlecounter.analyzer

import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect

/**
 * A blob the model found, with its contour in the pixel coordinates of the analysed bitmap: one piece, or a
 * cluster of touching pieces whose [pieceCount] is estimated from its [area] (see [PieceCounting]).
 */
class DetectedPiece(
    val path: Path,
    val rect: Rect,
    /** Pixels covered by the mask. */
    val area: Int,
    val score: Float,
    /** The pieces this blob stands for; 0 if it lies on a larger blob that is counted together with it. */
    val pieceCount: Int,
)

class DetectionResult(
    val pieces: List<DetectedPiece>,
    /** All contours drawn on black (debug image). */
    val maskImage: Bitmap,
    imageArea: Int,
) {
    /** The number of pieces in the photo. */
    val pieceCount = pieces.sumOf { it.pieceCount }

    /** The number of blobs that hold several touching pieces; their share of [pieceCount] is estimated. */
    val clusterCount = pieces.count { it.pieceCount > 1 }

    /** Heaps instead of spread out pieces: the count of this photo cannot be relied on. */
    val hasHugeStacks = PieceCounting.hasHugeStacks(pieces.map { it.area }, pieces.map { it.pieceCount }, imageArea)
}
