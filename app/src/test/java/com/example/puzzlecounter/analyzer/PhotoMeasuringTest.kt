package com.example.puzzlecounter.analyzer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * The measuring of brightness and sharpness on gray planes, without a phone: a few textured pieces on a flat table
 * (the scenes the whole-photo measurement wrongly rejected) and dense photos (which must be measured as before).
 */
class PhotoMeasuringTest {

    private val checker = ImageQualityChecker()

    private fun plane(width: Int, height: Int, gray: (x: Int, y: Int) -> Int) =
        GrayPlane(width, height, IntArray(width * height) { gray(it % width, it / width) })

    /**
     * A sharp piece: noise of [amplitude] gray levels around [mean] (the Laplacian of it varies from pixel to pixel
     * like the edges and prints of a real piece; a regular pattern would not).
     */
    private fun noisy(seed: Int, mean: Int = 130, amplitude: Int = 60): (Int, Int) -> Int {
        val random = Random(seed)
        return { _, _ -> mean - amplitude / 2 + random.nextInt(amplitude + 1) }
    }

    /**
     * A [size] x [size] plane with a flat [table] and square pieces of [pieceEdge] px at the given top left corners,
     * filled by [piece] (called once per pixel of a piece, row by row). Returns the plane and the boxes of the pieces.
     */
    private fun scene(
        size: Int,
        table: Int,
        pieceEdge: Int,
        corners: List<Pair<Int, Int>>,
        piece: (Int, Int) -> Int = noisy(1),
    ): Pair<GrayPlane, List<PieceBox>> {
        val boxes = corners.map { (x, y) -> PieceBox(x, y, x + pieceEdge, y + pieceEdge) }
        val plane = plane(size, size) { x, y ->
            if (boxes.any { x >= it.left && x < it.right && y >= it.top && y < it.bottom }) piece(x, y) else table
        }
        return plane to boxes
    }

    /** Six pieces of 24 px spread over a 400 px photo: about 2 % of it. */
    private val sixCorners = listOf(30 to 40, 150 to 60, 300 to 30, 60 to 200, 200 to 230, 320 to 300)

    private fun measure(plane: GrayPlane, boxes: List<PieceBox>, photoSize: Int = plane.width) =
        PhotoMeasuring.measure(plane, photoSize, photoSize, boxes)

    private fun verdict(m: PhotoMeasurements) = checker.decide(m, hasHugeStacks = false, pieceEdgeShare = null).issue

    // ------------------------------------------------------------------ the whole photo, as before

    /** The arithmetic of the first version of the checker, written out again to pin the whole-photo numbers down. */
    private fun referenceMeasurements(width: Int, height: Int, grays: IntArray): Pair<Int, Double> {
        var totalLuminance = 0L
        for (g in grays) totalLuminance += g
        var laplaceSum = 0L
        var laplaceSqSum = 0L
        var laplaceCount = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val laplace = abs(grays[i - width] + grays[i + width] + grays[i - 1] + grays[i + 1] - 4 * grays[i])
                laplaceSum += laplace
                laplaceSqSum += laplace * laplace
                laplaceCount++
            }
        }
        val mean = laplaceSum.toDouble() / laplaceCount
        return (totalLuminance / grays.size).toInt() to ((laplaceSqSum.toDouble() / laplaceCount) - (mean * mean))
    }

    @Test
    fun wholePhoto_isMeasuredExactlyAsBefore() {
        val random = Random(7)
        val noise = plane(97, 61) { _, _ -> random.nextInt(256) }
        val (luminance, sharpness) = referenceMeasurements(noise.width, noise.height, noise.grays)
        val measured = PhotoMeasuring.wholePhoto(noise)
        assertEquals(luminance, measured.averageLuminance)
        assertEquals(sharpness, measured.sharpness, 0.0)
        assertFalse(measured.measuredOnPieces)
    }

    @Test
    fun wholePhoto_ofAFlatPlane_hasItsGrayAndNoSharpness() {
        val flat = PhotoMeasuring.wholePhoto(plane(40, 40) { _, _ -> 130 })
        assertEquals(130, flat.averageLuminance)
        assertEquals(0.0, flat.sharpness, 0.0)
    }

    // ------------------------------------------------------------------ a few pieces on a flat table

    @Test
    fun fewPiecesOnAWhiteSheet_theWholePhotoLooksOverexposed_theirRegionDoesNot() {
        val (plane, boxes) = scene(400, table = 245, pieceEdge = 24, corners = sixCorners)
        val whole = PhotoMeasuring.wholePhoto(plane)
        assertTrue("whole photo ${whole.averageLuminance}", whole.averageLuminance > 225)
        assertEquals(PhotoIssue.OVEREXPOSED, verdict(whole))

        val onPieces = measure(plane, boxes)
        assertTrue(onPieces.measuredOnPieces)
        assertTrue("pieces ${onPieces.averageLuminance}", onPieces.averageLuminance in 120..140)
        assertTrue("pieces ${onPieces.sharpness}", onPieces.sharpness >= 250)
        assertNull(verdict(onPieces))
    }

    @Test
    fun fewPiecesOnAnOrdinaryTable_theWholePhotoLooksBlurry_theirRegionDoesNot() {
        val (plane, boxes) = scene(400, table = 150, pieceEdge = 24, corners = sixCorners)
        val whole = PhotoMeasuring.wholePhoto(plane)
        assertTrue("whole photo ${whole.sharpness}", whole.sharpness < 250)
        assertEquals(PhotoIssue.BLURRY, verdict(whole))
        assertNull(verdict(measure(plane, boxes)))
    }

    @Test
    fun blurredPiecesAreStillBlurry() {
        // a smooth ramp inside every piece: no edges to speak of
        val (plane, boxes) = scene(400, table = 150, pieceEdge = 24, corners = sixCorners) { x, y -> 120 + (x + y) % 20 }
        val onPieces = measure(plane, boxes)
        assertTrue(onPieces.measuredOnPieces)
        assertEquals(PhotoIssue.BLURRY, verdict(onPieces))
    }

    @Test
    fun darkPiecesAreStillTooDark_andBrightPiecesStillOverexposed() {
        val (dark, darkBoxes) = scene(400, table = 20, pieceEdge = 24, corners = sixCorners, piece = noisy(2, mean = 25, amplitude = 30))
        assertEquals(PhotoIssue.TOO_DARK, verdict(measure(dark, darkBoxes)))

        val (bright, brightBoxes) = scene(400, table = 250, pieceEdge = 24, corners = sixCorners, piece = noisy(3, mean = 235, amplitude = 40))
        assertEquals(PhotoIssue.OVEREXPOSED, verdict(measure(bright, brightBoxes)))
    }

    @Test
    fun aTableDarkerThanThePieces_doesNotMakeTheirPhotoDark() {
        val (plane, boxes) = scene(400, table = 15, pieceEdge = 24, corners = sixCorners)
        assertEquals(PhotoIssue.TOO_DARK, verdict(PhotoMeasuring.wholePhoto(plane)))
        assertNull(verdict(measure(plane, boxes)))
    }

    // ------------------------------------------------------------------ where the whole photo is measured

    @Test
    fun withoutBoxes_theWholePhotoIsMeasured() {
        val (plane, _) = scene(400, table = 245, pieceEdge = 24, corners = sixCorners)
        val measured = measure(plane, emptyList())
        assertFalse(measured.measuredOnPieces)
        assertEquals(PhotoMeasuring.wholePhoto(plane).sharpness, measured.sharpness, 0.0)
    }

    @Test
    fun piecesThatCoverALargePartOfThePhoto_areMeasuredAsAWhole_exactlyLikeBefore() {
        // four big pieces: their region (the boxes shrunk by 15 %) covers 28 % of the photo
        val (plane, boxes) = scene(400, table = 150, pieceEdge = 150, corners = listOf(20 to 20, 230 to 20, 20 to 230, 230 to 230))
        val measured = measure(plane, boxes)
        val whole = PhotoMeasuring.wholePhoto(plane)
        assertFalse(measured.measuredOnPieces)
        assertEquals(whole.averageLuminance, measured.averageLuminance)
        assertEquals(whole.sharpness, measured.sharpness, 0.0)
    }

    @Test
    fun theCoverageLimit_isTwentyPercentOfThePhoto() {
        val corners = listOf(20 to 20, 240 to 20, 20 to 240, 240 to 240)
        // four pieces of 120 px: regions of 84 x 84 px, 4 x 7 056 = 17.6 % of 160 000 px: measured on the pieces
        val (sparse, sparseBoxes) = scene(400, table = 150, pieceEdge = 120, corners = corners)
        assertTrue(measure(sparse, sparseBoxes).measuredOnPieces)
        // four pieces of 134 px: regions of 94 x 94 px, 4 x 8 836 = 22.1 %: the whole photo
        val (dense, denseBoxes) = scene(400, table = 150, pieceEdge = 134, corners = corners)
        assertFalse(measure(dense, denseBoxes).measuredOnPieces)
    }

    @Test
    fun aRegionOfFewerThan100Pixels_saysTooLittle() {
        // a small photo: the box of 8 px is big enough compared with the photo, but its region is 6 x 6 = 36 px
        val (plane, boxes) = scene(50, table = 245, pieceEdge = 8, corners = listOf(20 to 20))
        assertFalse(measure(plane, boxes).measuredOnPieces)
    }

    @Test
    fun aSinglePieceOfNormalSize_isMeasuredOnItsOwn() {
        // 5 % of the photo's edge: a piece of an ordinary photo (region 14 x 14 px)
        val (plane, boxes) = scene(400, table = 245, pieceEdge = 20, corners = listOf(190 to 190))
        val measured = measure(plane, boxes)
        assertTrue(measured.measuredOnPieces)
        assertTrue("luminance ${measured.averageLuminance}", measured.averageLuminance in 115..145)
        assertNull(verdict(measured))
    }

    // ------------------------------------------------------------------ pieces too small to be trusted

    @Test
    fun tinyPieces_areMeasuredAsAWholePhoto() {
        // a pile photographed from far away: the model finds a few boxes of 8 px in a 400 px photo (2 %)
        assertTrue("8 px is below the size limit", 8.0 < PhotoMeasuring.MIN_BOX_EDGE_SHARE * 400)
        val (plane, boxes) = scene(400, table = 150, pieceEdge = 8, corners = List(12) { 20 + it * 30 to 100 })
        val measured = measure(plane, boxes)
        assertFalse(measured.measuredOnPieces)
        assertEquals(PhotoIssue.BLURRY, verdict(measured))
    }

    @Test
    fun piecesJustAboveTheSizeLimit_areMeasuredOnTheirRegion() {
        val edge = 13 // 3.25 % of 400 px, the limit is 2.8 %
        assertTrue(edge > PhotoMeasuring.MIN_BOX_EDGE_SHARE * 400)
        val (plane, boxes) = scene(400, table = 245, pieceEdge = edge, corners = List(12) { 20 + it * 30 to 100 })
        assertTrue(measure(plane, boxes).measuredOnPieces)
    }

    @Test
    fun oneStrayTinyBox_doesNotTurnThePiecesIntoTinyOnes() {
        val (plane, boxes) = scene(400, table = 245, pieceEdge = 24, corners = sixCorners)
        val withStray = boxes + PieceBox(10, 10, 14, 14)
        assertTrue(measure(plane, withStray).measuredOnPieces)
    }

    // ------------------------------------------------------------------ geometry

    @Test
    fun boxesInPhotoPixels_areMappedOntoTheShrunkPlane() {
        // a photo of 1000 x 500 px analysed at 400 x 200: the noisy piece lies at (600, 200)-(700, 300) in the photo
        val pieceNoise = noisy(4)
        val plane = plane(400, 200) { x, y -> if (x in 240 until 280 && y in 80 until 120) pieceNoise(x, y) else 245 }
        val box = PieceBox(600, 200, 700, 300)
        val measured = PhotoMeasuring.measure(plane, 1000, 500, listOf(box))
        assertTrue(measured.measuredOnPieces)
        assertTrue("luminance ${measured.averageLuminance}", measured.averageLuminance in 120..140)
        assertTrue("sharpness ${measured.sharpness}", measured.sharpness >= 250)
        // the same box taken for plane pixels lies outside the plane: nothing is measured there
        val wrong = PhotoMeasuring.measure(plane, 400, 200, listOf(box))
        assertFalse(wrong.measuredOnPieces)
    }

    @Test
    fun boxesBeyondThePlane_areClipped_andEmptyBoxesIgnored() {
        val (plane, boxes) = scene(400, table = 245, pieceEdge = 24, corners = sixCorners)
        val odd = boxes + listOf(
            PieceBox(-30, -30, 20, 20), // sticks out at the top left
            PieceBox(380, 380, 460, 460), // sticks out at the bottom right
            PieceBox(100, 100, 100, 140), // no width
            PieceBox(200, 200, 150, 150), // upside down
        )
        assertTrue(measure(plane, odd).measuredOnPieces)
    }

    @Test
    fun aPlaneMustMatchItsSize() {
        try {
            GrayPlane(4, 4, IntArray(15))
            fail("a plane with the wrong number of values must not be accepted")
        } catch (expected: IllegalArgumentException) {
            // as intended
        }
    }

    // ------------------------------------------------------------------ the verdict

    @Test
    fun theVerdict_namesTheFirstProblem_inTheOrderOfTheChecks() {
        fun issue(luminance: Int, sharpness: Double, edgeShare: Float?, stacks: Boolean) =
            checker.decide(PhotoMeasurements(luminance, sharpness), stacks, edgeShare).issue

        assertNull(issue(150, 1400.0, 0.04f, false))
        assertEquals(PhotoIssue.TOO_DARK, issue(49, 1400.0, 0.04f, true))
        assertEquals(PhotoIssue.OVEREXPOSED, issue(226, 10.0, 0.01f, true))
        assertEquals(PhotoIssue.BLURRY, issue(150, 249.0, 0.01f, true))
        assertEquals(PhotoIssue.PIECES_TOO_SMALL, issue(150, 250.0, 0.021f, true))
        assertEquals(PhotoIssue.STACKED_PIECES, issue(150, 250.0, 0.022f, true))
        assertNull(issue(50, 250.0, null, false))
        assertNull(issue(225, 250.0, 0.022f, false))
    }
}
