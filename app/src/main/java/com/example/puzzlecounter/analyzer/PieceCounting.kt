package com.example.puzzlecounter.analyzer

import kotlin.math.roundToInt

/**
 * The mask of a detected blob: [pixels] is a [width] x [height] raster whose top left pixel lies at ([left], [top])
 * of the photo. [area] is the pixel count of the blob that is counted (its largest connected part).
 */
internal class BlobMask(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val pixels: BooleanArray,
    val area: Int,
) {
    val pixelCount: Int = pixels.count { it }
}

/**
 * How the pieces of a photo are counted from the blobs the model found.
 *
 * Almost every blob is one piece. Pieces that touch or overlap are often segmented as one blob, which would count
 * as a single piece. All pieces of a puzzle are cut to the same size, so such a cluster shows up as a blob that is
 * much larger than a typical piece, and the number of pieces in it can be estimated from its area.
 * The model often also reports a piece inside such a blob on its own: blobs whose masks mostly lie on top of each
 * other are one group and are counted by the area they cover together, otherwise that piece would count twice.
 *
 * The constants come from four photos of one pile of about 200 pieces (1080 x 1080 px, analysed at the 1024 px of
 * the model, where a piece covers about 1500 to 1750 px²), checked blob by blob against the photos: single pieces
 * vary around the median area by about 16 % (standard deviation) and reach 1.34 times the median at most, while
 * every blob from 1.35 times on held two or more pieces (two overlapping pieces cover 1.35 to 1.6 times, the four in
 * one cluster 2.2 times). The masks of separate pieces that merely touch overlap by at most 23 % of the smaller one;
 * the blobs inside a cluster by 44 to 88 %.
 */
internal object PieceCounting {

    /** Fewer blobs than this cannot tell what a typical piece looks like (a cluster could be the majority). */
    const val MIN_BLOBS_FOR_REFERENCE = 8

    /** A blob at least this many times the typical piece area is a cluster of several pieces. */
    const val CLUSTER_MIN_RATIO = 1.35f

    /**
     * Area that every piece of a cluster adds, relative to a lone piece: the pieces of a cluster cover each other
     * by about a third. Gives two pieces up to 1.6 times the typical area, three up to 2.3 times, and so on.
     */
    const val AREA_PER_PIECE_IN_CLUSTER = 0.65f

    /** Blobs of which at least this share of the smaller mask lies on the other one belong to the same group. */
    const val GROUP_OVERLAP = 0.30f

    /** A group that covers less than this many typical pieces together is one piece the model cut in two. */
    const val GROUP_SPLIT_MAX_RATIO = 0.8f

    /** A single blob covering this share of the photo is a heap, not a piece. */
    const val MAX_BLOB_IMAGE_SHARE = 0.40f

    /** If more than this share of the pieces sit in clusters, their estimated number is too unreliable. */
    const val MAX_CLUSTERED_SHARE = 0.15f

    /**
     * The share rule needs more clustered pieces than this: a single cluster of two pieces is no heap, however few
     * pieces the photo has. Of ten pieces one touching pair is 20 %, over the share, but the estimate for a pair is off
     * by one piece at most - less than the counting accuracy of a photo (see `countingTolerance`) - and the photo
     * says that the number is estimated there. For 14 pieces and more the share is above this number, so nothing
     * changes for them (the test piles have about 200 pieces and about 5 % of them in clusters).
     */
    const val MIN_CLUSTERED_PIECES_FOR_STACKS = 2

    /** The area of a single piece in this photo (median of all blobs), or null if there are too few blobs. */
    fun typicalPieceArea(areas: List<Int>): Float? {
        if (areas.size < MIN_BLOBS_FOR_REFERENCE) return null
        val sorted = areas.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle].toFloat() else (sorted[middle - 1] + sorted[middle]) / 2f
    }

    /** The number of pieces in a blob of [area]; every blob counts as one piece if there is no [typicalArea]. */
    fun piecesIn(area: Int, typicalArea: Float?): Int {
        if (typicalArea == null || typicalArea <= 0f) return 1
        val ratio = area / typicalArea
        return if (ratio < CLUSTER_MIN_RATIO) 1 else maxOf(2, (ratio / AREA_PER_PIECE_IN_CLUSTER).roundToInt())
    }

    /**
     * The number of pieces each of the [blobs] stands for. A group of blobs that lie on top of each other is
     * counted as a whole: the largest blob of the group gets the pieces, the others stand for none.
     */
    fun countPieces(blobs: List<BlobMask>): IntArray {
        val typicalArea = typicalPieceArea(blobs.map { it.area })
        val counts = IntArray(blobs.size)
        for (group in groupsOfOverlappingBlobs(blobs)) {
            val largest = group.maxBy { blobs[it].area }
            counts[largest] = if (group.size == 1) {
                piecesIn(blobs[largest].area, typicalArea)
            } else {
                piecesInGroup(unionArea(group.map { blobs[it] }), group.size, typicalArea)
            }
        }
        return counts
    }

    /**
     * The pieces in a group of overlapping blobs that cover [coveredArea] together. Where the area is that of a
     * cluster, it counts like one. Where it is that of at most one piece, the area cannot tell one piece that was
     * reported twice from a piece that another one covers: the model's masks are not exclusive, so the mask of the
     * covering piece reaches over the visible part of the piece below, and that part is reported as a small blob of
     * its own that lies mostly inside the big one (pictures of such places in the test photos show real, mostly
     * covered pieces, with a confidence of 0.4 to 0.6). Such blobs count as pieces of their own, as many as there are
     * blobs but at most three; only a group that covers less than [GROUP_SPLIT_MAX_RATIO] pieces is taken for the
     * fragments of one piece.
     */
    private fun piecesInGroup(coveredArea: Int, blobCount: Int, typicalArea: Float?): Int {
        if (typicalArea == null || typicalArea <= 0f) return blobCount
        val ratio = coveredArea / typicalArea
        return when {
            ratio < GROUP_SPLIT_MAX_RATIO -> 1
            ratio < CLUSTER_MIN_RATIO -> minOf(blobCount, 3)
            else -> piecesIn(coveredArea, typicalArea)
        }
    }

    /** Indices of the blobs, grouped: blobs whose masks overlap by at least [GROUP_OVERLAP] of the smaller one are joined. */
    private fun groupsOfOverlappingBlobs(blobs: List<BlobMask>): List<List<Int>> {
        val parent = IntArray(blobs.size) { it }
        fun root(i: Int): Int {
            var node = i
            while (parent[node] != node) {
                parent[node] = parent[parent[node]]
                node = parent[node]
            }
            return node
        }
        for (i in blobs.indices) {
            for (j in i + 1 until blobs.size) {
                val overlap = overlappingPixels(blobs[i], blobs[j])
                if (overlap > 0 && overlap >= GROUP_OVERLAP * minOf(blobs[i].pixelCount, blobs[j].pixelCount)) {
                    parent[root(i)] = root(j)
                }
            }
        }
        return blobs.indices.groupBy { root(it) }.values.toList()
    }

    private fun overlappingPixels(a: BlobMask, b: BlobMask): Int {
        val left = maxOf(a.left, b.left)
        val top = maxOf(a.top, b.top)
        val right = minOf(a.left + a.width, b.left + b.width)
        val bottom = minOf(a.top + a.height, b.top + b.height)
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                if (a.pixels[(y - a.top) * a.width + (x - a.left)] && b.pixels[(y - b.top) * b.width + (x - b.left)]) count++
            }
        }
        return count
    }

    /** The number of pixels that at least one of the [blobs] covers. */
    private fun unionArea(blobs: List<BlobMask>): Int {
        val left = blobs.minOf { it.left }
        val top = blobs.minOf { it.top }
        val right = blobs.maxOf { it.left + it.width }
        val bottom = blobs.maxOf { it.top + it.height }
        val width = right - left
        val covered = BooleanArray(width * (bottom - top))
        for (blob in blobs) {
            for (y in 0 until blob.height) {
                for (x in 0 until blob.width) {
                    if (blob.pixels[y * blob.width + x]) covered[(blob.top + y - top) * width + (blob.left + x - left)] = true
                }
            }
        }
        return covered.count { it }
    }

    /** Whether the photo shows heaps of pieces: a blob far too large for a piece, or too many clustered pieces. */
    fun hasHugeStacks(areas: List<Int>, pieceCounts: List<Int>, imageArea: Int): Boolean {
        val clustered = pieceCounts.filter { it > 1 }.sum()
        val tooManyClustered = clustered > maxOf(pieceCounts.sum() * MAX_CLUSTERED_SHARE, MIN_CLUSTERED_PIECES_FOR_STACKS.toFloat())
        return areas.any { it > imageArea * MAX_BLOB_IMAGE_SHARE } || tooManyClustered
    }
}
