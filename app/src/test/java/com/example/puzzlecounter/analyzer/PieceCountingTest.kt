package com.example.puzzlecounter.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PieceCountingTest {

    private val typical = 2000f

    private fun piecesIn(ratio: Float) = PieceCounting.piecesIn((typical * ratio).toInt(), typical)

    @Test
    fun typicalPieceArea_isTheMedian() {
        assertEquals(30f, PieceCounting.typicalPieceArea(listOf(10, 50, 20, 40, 30, 35, 25, 60, 15))!!, 0f)
        assertEquals(32.5f, PieceCounting.typicalPieceArea(listOf(10, 50, 20, 40, 30, 35, 25, 60))!!, 0f)
    }

    @Test
    fun typicalPieceArea_isRobustAgainstClusters() {
        // a fifth of the blobs are clusters of several pieces: the median stays at the single piece area
        val areas = List(80) { 2000 + it % 7 * 10 } + List(20) { 4500 }
        assertEquals(2030f, PieceCounting.typicalPieceArea(areas)!!, 40f)
    }

    @Test
    fun typicalPieceArea_needsEnoughBlobs() {
        assertNull(PieceCounting.typicalPieceArea(emptyList()))
        assertNull(PieceCounting.typicalPieceArea(List(PieceCounting.MIN_BLOBS_FOR_REFERENCE - 1) { 2000 }))
        assertEquals(2000f, PieceCounting.typicalPieceArea(List(PieceCounting.MIN_BLOBS_FOR_REFERENCE) { 2000 })!!, 0f)
    }

    @Test
    fun singlePieces_countOnce_evenTheLargestOnes() {
        assertEquals(1, piecesIn(0.3f)) // a piece that other pieces cover
        assertEquals(1, piecesIn(1.0f))
        assertEquals(1, piecesIn(1.34f)) // the largest single piece measured
    }

    @Test
    fun clusters_countBySteps() {
        assertEquals(2, piecesIn(1.36f)) // smallest cluster measured
        assertEquals(2, piecesIn(1.55f))
        assertEquals(3, piecesIn(1.70f))
        assertEquals(3, piecesIn(2.20f))
        assertEquals(4, piecesIn(2.30f))
        assertEquals(10, piecesIn(6.5f))
    }

    @Test
    fun withoutAReference_everyBlobIsOnePiece() {
        assertEquals(1, PieceCounting.piecesIn(50_000, null))
        assertEquals(1, PieceCounting.piecesIn(50_000, 0f))
    }

    @Test
    fun hugeStacks_aBlobCoveringALargePartOfThePhoto() {
        val imageArea = 1080 * 1080
        assertTrue(PieceCounting.hasHugeStacks(listOf(2000, (imageArea * 0.5).toInt()), listOf(1, 200), imageArea))
        assertFalse(PieceCounting.hasHugeStacks(listOf(2000, (imageArea * 0.2).toInt()), listOf(1, 1), imageArea))
    }

    @Test
    fun hugeStacks_tooManyClusteredPieces() {
        val imageArea = 1080 * 1080
        // 10 clustered pieces of 200: the usual share (about 5 %) is fine
        assertFalse(PieceCounting.hasHugeStacks(emptyList(), List(190) { 1 } + List(5) { 2 }, imageArea))
        // 40 clustered pieces of 200 (20 %): too many
        assertTrue(PieceCounting.hasHugeStacks(emptyList(), List(160) { 1 } + List(20) { 2 }, imageArea))
    }

    @Test
    fun hugeStacks_aSinglePairInASmallPhotoIsNoHeap() {
        val imageArea = 1080 * 1080
        // ten pieces, one touching pair: 20 %, but a pair is within the counting accuracy and no heap
        assertFalse(PieceCounting.hasHugeStacks(emptyList(), List(8) { 1 } + 2, imageArea))
        // the same at the limits of the rule: 13 and 14 pieces with one pair
        assertFalse(PieceCounting.hasHugeStacks(emptyList(), List(11) { 1 } + 2, imageArea))
        assertFalse(PieceCounting.hasHugeStacks(emptyList(), List(12) { 1 } + 2, imageArea))
    }

    @Test
    fun hugeStacks_moreThanAPairInASmallPhotoIsStillOne() {
        val imageArea = 1080 * 1080
        // two pairs among ten pieces
        assertTrue(PieceCounting.hasHugeStacks(emptyList(), List(6) { 1 } + listOf(2, 2), imageArea))
        // one cluster of three
        assertTrue(PieceCounting.hasHugeStacks(emptyList(), List(7) { 1 } + 3, imageArea))
        // 14 pieces: the share rule alone (15 % of 14 is 2.1) flags three clustered pieces, as before
        assertTrue(PieceCounting.hasHugeStacks(emptyList(), List(11) { 1 } + 3, imageArea))
        assertFalse(PieceCounting.hasHugeStacks(emptyList(), List(12) { 1 } + 2, imageArea))
    }

    // --- blobs that lie on top of each other ---

    private fun blob(left: Int, top: Int, width: Int, height: Int) =
        BlobMask(left, top, width, height, BooleanArray(width * height) { true }, width * height)

    /** Eight lone pieces of 40 x 40 px (1600 px²) far apart from each other: they tell the typical piece area. */
    private fun lonePieces() = List(8) { blob(left = it * 100, top = 500, width = 40, height = 40) }

    @Test
    fun separateBlobs_countOneEach() {
        assertEquals(List(8) { 1 }, PieceCounting.countPieces(lonePieces()).toList())
    }

    @Test
    fun touchingPieces_stayApart() {
        // two pieces whose masks share a border of 4 px: 10 % of the smaller mask, far below the group limit
        val counts = PieceCounting.countPieces(lonePieces() + blob(0, 0, 40, 40) + blob(36, 0, 40, 40))
        assertEquals(List(10) { 1 }, counts.toList())
    }

    @Test
    fun aBlobOnABiggerBlob_isCountedTogether() {
        // a cluster of 1.5 pieces' area with a part of it reported again: counted once by the area they cover
        val cluster = blob(0, 0, 60, 40) // 2400 px² = 1.5 typical pieces -> 2 pieces
        val insideIt = blob(10, 10, 20, 20)
        val counts = PieceCounting.countPieces(lonePieces() + cluster + insideIt)
        assertEquals(2, counts[8])
        assertEquals(0, counts[9])
        assertEquals(10, counts.sum())
    }

    @Test
    fun twoFragmentsOfOnePiece_countOnce() {
        // 20 x 30 and 20 x 30 px overlapping by half: together 900 px² = 0.56 typical pieces
        val counts = PieceCounting.countPieces(lonePieces() + blob(0, 0, 20, 30) + blob(10, 0, 20, 30))
        assertEquals(9, counts.sum())
        assertEquals(1, counts[8] + counts[9])
    }

    @Test
    fun twoOverlappingPieces_countTwice() {
        // 40 x 40 and 40 x 20 px overlapping by 40 % of the smaller one: together 1.3 typical pieces
        val counts = PieceCounting.countPieces(lonePieces() + blob(0, 0, 40, 40) + blob(24, 0, 40, 20))
        assertEquals(2, counts[8] + counts[9])
        assertEquals(10, counts.sum())
    }

    @Test
    fun withoutAReference_overlappingBlobsCountEach() {
        val counts = PieceCounting.countPieces(listOf(blob(0, 0, 40, 40), blob(10, 10, 20, 20)))
        assertEquals(2, counts.sum())
    }

    @Test
    fun groupCount_followsTheCoveredArea() {
        // a 40 x 100 px chain (2.5 typical pieces) with two of its pieces reported again inside it: 4 pieces
        val chain = blob(0, 0, 40, 100)
        val counts = PieceCounting.countPieces(lonePieces() + chain + blob(0, 0, 40, 36) + blob(0, 64, 40, 36))
        assertEquals(4, counts[8])
        assertEquals(12, counts.sum())
    }

    @Test
    fun hugeStacks_emptyPhoto() {
        assertFalse(PieceCounting.hasHugeStacks(emptyList(), emptyList(), 1080 * 1080))
    }
}
