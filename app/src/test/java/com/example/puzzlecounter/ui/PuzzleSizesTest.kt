package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The reference of the sizes of rectangular puzzles (box number against the pieces really in the box), checked row by row. */
class PuzzleSizesTest {

    /** The table as it was given, (box, exact): every row must be in the data exactly like this. */
    private val reference = listOf(
        100 to 104, 200 to 204, 300 to 300, 500 to 500, 750 to 750, 1000 to 1000, 1000 to 1008, 1500 to 1530, 2000 to 2000,
        3000 to 3168, 4000 to 4000, 5000 to 5040, 6000 to 6000, 9000 to 9120, 12000 to 12096, 13200 to 13224, 18000 to 18240,
        24000 to 24000, 32000 to 32256, 33600 to 33600, 40320 to 40320, 42000 to 42000, 54000 to 54000, 60000 to 60000,
    )

    @Test
    fun everyRowOfTheTable_isThere() {
        assertEquals(reference, PuzzleSizes.all.map { it.boxCount to it.exactCount })
    }

    @Test
    fun theExactCounts_areDistinct_andFromAHundredToSixtyThousand() {
        val exact = PuzzleSizes.all.map { it.exactCount }
        assertEquals(exact.size, exact.toSet().size)
        assertEquals(exact.sorted(), exact)
        assertTrue(exact.all { it in 100..60_000 })
        assertEquals(60_000, exact.max())
    }

    @Test
    fun theBoxNumberIsRounded_byAtMostSixPercent() {
        for (size in PuzzleSizes.all) {
            val deviation = Math.abs(size.exactCount - size.boxCount) / size.boxCount.toDouble()
            assertTrue("${size.boxCount} -> ${size.exactCount}", deviation <= 0.06)
        }
    }

    @Test
    fun aGrid_alwaysMakesTheExactCount_andItsBorderFollowsTheFormula() {
        // columns x rows = exact count, border = 2 x (columns + rows) - 4 (checked against the table: border pieces including the 4 corners)
        val borders = mapOf(104 to 38, 204 to 54, 300 to 66, 500 to 86, 750 to 106, 1000 to 126, 1008 to 124, 1530 to 154, 2000 to 176, 4000 to 256)
        for (size in PuzzleSizes.all) {
            val grid = size.grid ?: continue
            assertEquals("$grid", size.exactCount, grid.columns * grid.rows)
            assertEquals("${size.exactCount}", borders.getValue(size.exactCount), grid.edgePieces)
            assertTrue("a puzzle has more inner pieces than border pieces from 100 on... ${size.exactCount}", size.exactCount - grid.edgePieces > 0)
        }
        assertEquals(borders.keys, PuzzleSizes.all.filter { it.grid != null }.map { it.exactCount }.toSet())
    }

    @Test
    fun theGrids_areNotGivenWhereMoreThanOneFits() {
        for (count in listOf(3168, 5040, 9120, 12096, 13224, 18240)) {
            assertNull("$count", PuzzleSizes.withExactCount(count)!!.grid)
        }
    }

    @Test
    fun aRavensburgerThousand_hasThirtySixTimesTwentyEightPieces() {
        val ravensburger = PuzzleSizes.withExactCount(1008)!!
        assertEquals(PuzzleMaker.RAVENSBURGER, ravensburger.maker)
        assertEquals("36 × 28", ravensburger.grid.toString())
        assertEquals(124, ravensburger.grid!!.edgePieces)
        assertEquals(126, PuzzleSizes.withExactCount(1000)!!.grid!!.edgePieces)
    }

    @Test
    fun variants_areThePuzzlesThatReallyHaveAnotherNumber() {
        fun variants(box: Int) = PuzzleSizes.variantsOf(box).map { it.exactCount }
        assertEquals(listOf(104), variants(100))
        assertEquals(listOf(204), variants(200))
        assertEquals(listOf(1008), variants(1000))
        assertEquals(listOf(1530), variants(1500))
        assertEquals(listOf(3168), variants(3000))
        assertEquals(listOf(5040), variants(5000))
        assertEquals(listOf(9120), variants(9000))
        assertEquals(listOf(12096), variants(12000))
        assertEquals(listOf(13224), variants(13200))
        assertEquals(listOf(18240), variants(18000))
        assertEquals(listOf(32256), variants(32000))
        // sizes whose box number is exact, and numbers that are no box number at all
        for (box in listOf(300, 500, 750, 2000, 4000, 6000, 24000, 33600, 40320, 42000, 54000, 60000, 204, 1008, 350, 0)) {
            assertEquals("$box", emptyList<Int>(), variants(box))
        }
    }

    @Test
    fun nearest_findsTheSizeWithinTheToleranceAndPrefersTheCloserOne() {
        assertEquals(204, PuzzleSizes.nearest(204, 3)!!.exactCount)
        assertEquals(204, PuzzleSizes.nearest(201, 3)!!.exactCount)
        assertEquals(204, PuzzleSizes.nearest(207, 3)!!.exactCount)
        assertNull(PuzzleSizes.nearest(200, 3))
        assertNull(PuzzleSizes.nearest(208, 3))
        // 1000 and 1008 lie close together: the closer one wins, the smaller one if both are equally near
        assertEquals(1008, PuzzleSizes.nearest(1006, 6)!!.exactCount)
        assertEquals(1000, PuzzleSizes.nearest(1002, 6)!!.exactCount)
        assertEquals(1000, PuzzleSizes.nearest(1004, 6)!!.exactCount)
        // the largest puzzle and a tolerance that grows with the number of photos
        assertEquals(60_000, PuzzleSizes.nearest(59_980, countingTolerance(300))!!.exactCount)
        assertNull(PuzzleSizes.nearest(59_900, countingTolerance(300)))
    }

    @Test
    fun theBrands_areKnown() {
        assertEquals(PuzzleMaker.CLEMENTONI, PuzzleSizes.withExactCount(13224)!!.maker)
        assertEquals(PuzzleMaker.DOWDLE, PuzzleSizes.withExactCount(60_000)!!.maker)
        assertEquals(PuzzleMaker.GRAFIKA, PuzzleSizes.withExactCount(54_000)!!.maker)
        assertNotNull(PuzzleSizes.withExactCount(750))
        assertNull(PuzzleSizes.withExactCount(200))
    }
}
