package com.example.puzzlecounter.ui

import androidx.annotation.StringRes
import com.example.puzzlecounter.R
import kotlin.math.abs

/** The makers of the sizes in [PuzzleSizes]; the names of the brands are not translated. */
internal enum class PuzzleMaker(@param:StringRes val nameRes: Int) {
    RAVENSBURGER(R.string.maker_ravensburger),
    EDUCA(R.string.maker_educa),
    CLEMENTONI(R.string.maker_clementoni),
    GRAFIKA(R.string.maker_grafika),
    DOWDLE(R.string.maker_dowdle),
    US_STANDARD(R.string.maker_us_standard),
    VARIOUS(R.string.maker_various),
}

/** The pieces of a puzzle in [columns] x [rows]. */
internal class PuzzleGrid(val columns: Int, val rows: Int) {
    val pieces: Int get() = columns * rows

    /** The pieces along the border including the 4 corners: 2 x (columns + rows) - 4. */
    val edgePieces: Int get() = 2 * (columns + rows) - 4

    override fun toString() = "$columns × $rows"
}

/**
 * A size in which a puzzle is sold: [boxCount] is the number on the box, [exactCount] the number of pieces that are
 * really in it. [grid] is only known where the count and the measures of the puzzle allow one grid and no other.
 */
internal class PuzzleSize(
    val boxCount: Int,
    val exactCount: Int,
    val maker: PuzzleMaker,
    val grid: PuzzleGrid? = null,
) {
    init {
        require(grid == null || grid.pieces == exactCount) { "$grid hat nicht $exactCount Teile" }
    }
}

/**
 * What the number on the box of a puzzle means in reality. The number on the box is often rounded, because the
 * pieces are cut in a regular grid of columns x rows whose product is rarely round: Ravensburger, for example, says in
 * its puzzle FAQ that a puzzle sold as 100 pieces has 104, one of 1000 has 1008 and one of 3000 has 3168. The scan
 * of the app (see [analyseResult]) looks the number it counted up here to tell whether it fits such a puzzle.
 *
 * Only rectangular standard puzzles are listed: no round, shaped, 3D or panorama puzzles, no triptychs, and no wooden
 * puzzles with irregular pieces - their numbers follow other rules (a Ravensburger 500 piece panorama has 504, for
 * example, a 200 piece panorama exactly 200).
 *
 * Where the data comes from: the counts of Ravensburger from its puzzle FAQ (100 -> 104, 200 -> 204, 500 -> 500,
 * 1000 -> 1008 as 36 x 28, 1500 -> 1530, 2000, 3000 -> 3168, 5000 -> 5040, 9000 -> 9120, 12000 -> 12096, 18000 -> 18240,
 * 32000 -> 32256, 40320), the others from the data of the makers and shops (Educa 4000, 24000, 33600, 42000, Clementoni
 * 6000 and 13200, Grafika 54000, Dowdle 60000). Every row was checked: columns x rows is the exact count and the
 * pieces of the border follow the formula of [PuzzleGrid.edgePieces].
 *
 * The grids are only given where the count and the measures of the puzzle fit one grid and no other (checked by
 * trying every divisor pair of the count for pieces that are about square, 0.8 to 1.25 times as wide as high). For
 * 3168, 5040, 9120, 12096, 13224 and 18240 two or three grids fit (for 9120 both 120 x 76 and 114 x 80, for 5040 84 x 60,
 * 90 x 56 and 80 x 63): there the border is not given rather than guessed. The grids of the sizes above 18240 are
 * not given either, the app does not need them.
 */
internal object PuzzleSizes {

    val all: List<PuzzleSize> = listOf(
        PuzzleSize(100, 104, PuzzleMaker.RAVENSBURGER, PuzzleGrid(13, 8)),
        PuzzleSize(200, 204, PuzzleMaker.RAVENSBURGER, PuzzleGrid(17, 12)),
        PuzzleSize(300, 300, PuzzleMaker.RAVENSBURGER, PuzzleGrid(20, 15)),
        PuzzleSize(500, 500, PuzzleMaker.RAVENSBURGER, PuzzleGrid(25, 20)),
        PuzzleSize(750, 750, PuzzleMaker.US_STANDARD, PuzzleGrid(30, 25)),
        PuzzleSize(1000, 1000, PuzzleMaker.VARIOUS, PuzzleGrid(40, 25)),
        PuzzleSize(1000, 1008, PuzzleMaker.RAVENSBURGER, PuzzleGrid(36, 28)),
        PuzzleSize(1500, 1530, PuzzleMaker.RAVENSBURGER, PuzzleGrid(45, 34)),
        PuzzleSize(2000, 2000, PuzzleMaker.RAVENSBURGER, PuzzleGrid(50, 40)),
        PuzzleSize(3000, 3168, PuzzleMaker.RAVENSBURGER),
        PuzzleSize(4000, 4000, PuzzleMaker.EDUCA, PuzzleGrid(80, 50)),
        PuzzleSize(5000, 5040, PuzzleMaker.RAVENSBURGER),
        PuzzleSize(6000, 6000, PuzzleMaker.CLEMENTONI),
        PuzzleSize(9000, 9120, PuzzleMaker.RAVENSBURGER),
        PuzzleSize(12000, 12096, PuzzleMaker.RAVENSBURGER),
        PuzzleSize(13200, 13224, PuzzleMaker.CLEMENTONI),
        PuzzleSize(18000, 18240, PuzzleMaker.RAVENSBURGER),
        PuzzleSize(24000, 24000, PuzzleMaker.EDUCA),
        PuzzleSize(32000, 32256, PuzzleMaker.RAVENSBURGER),
        PuzzleSize(33600, 33600, PuzzleMaker.EDUCA),
        PuzzleSize(40320, 40320, PuzzleMaker.RAVENSBURGER),
        PuzzleSize(42000, 42000, PuzzleMaker.EDUCA),
        PuzzleSize(54000, 54000, PuzzleMaker.GRAFIKA),
        PuzzleSize(60000, 60000, PuzzleMaker.DOWDLE),
    )

    /** The sizes sold with [boxCount] on the box that really have another number of pieces (200 -> 204). */
    fun variantsOf(boxCount: Int): List<PuzzleSize> = all.filter { it.boxCount == boxCount && it.exactCount != boxCount }

    /** The size that really has [count] pieces, if one is known. */
    fun withExactCount(count: Int): PuzzleSize? = all.firstOrNull { it.exactCount == count }

    /** The size whose exact count is nearest to [count] and at most [tolerance] away from it (the smaller one if two are equally near). */
    fun nearest(count: Int, tolerance: Int): PuzzleSize? =
        all.filter { abs(it.exactCount - count) <= tolerance }
            .minWithOrNull(compareBy({ abs(it.exactCount - count) }, { it.exactCount }))
}
