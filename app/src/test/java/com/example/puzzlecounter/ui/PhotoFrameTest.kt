package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoFrameTest {

    private val landscape = PhotoFrame(4000, 3000)
    private val portrait = PhotoFrame(3000, 4000)

    private fun assertRegion(left: Float, top: Float, side: Float, actual: PhotoRegion) {
        assertEquals("left", left, actual.left, 0.01f)
        assertEquals("top", top, actual.top, 0.01f)
        assertEquals("side", side, actual.side, 0.01f)
    }

    @Test
    fun atFirst_theFrameShowsTheCenteredSquareOfThePhoto() {
        assertRegion(500f, 0f, 3000f, landscape.regionAt(PhotoPlacement()))
        assertRegion(0f, 500f, 3000f, portrait.regionAt(PhotoPlacement()))
        assertRegion(0f, 0f, 1080f, PhotoFrame(1080, 1080).regionAt(PhotoPlacement()))
    }

    @Test
    fun movingTheLongSide_slidesTheFrameOverThePhoto() {
        // the photo moves right by a tenth of the frame: the frame sees 300 px more of the photo's left side
        val moved = landscape.moved(PhotoPlacement(), panX = 0.1f, panY = 0f)
        assertRegion(200f, 0f, 3000f, landscape.regionAt(moved))
    }

    @Test
    fun theShortSide_cannotBeMovedWhileThePhotoFillsTheFrame() {
        val moved = landscape.moved(PhotoPlacement(), panX = 0f, panY = 0.4f)
        assertEquals(0f, moved.offsetY, 0f)
    }

    @Test
    fun aPhotoLargerThanTheFrame_alwaysCoversIt() {
        // pushed far to the right: the photo's left edge stops at the frame's left edge
        val right = landscape.moved(PhotoPlacement(), panX = 5f, panY = 0f)
        assertRegion(0f, 0f, 3000f, landscape.regionAt(right))
        val left = landscape.moved(PhotoPlacement(), panX = -5f, panY = 0f)
        assertRegion(1000f, 0f, 3000f, landscape.regionAt(left))
    }

    @Test
    fun zoomingOut_showsTheWholePhotoAndLeavesRoomToSlideIt() {
        val out = landscape.moved(PhotoPlacement(), panX = 0f, panY = 0f, zoomChange = 0.01f)
        assertEquals(0.75f, out.zoom, 0.0001f)
        // the whole photo fits the frame's width, the frame reaches 500 px above and below it
        assertRegion(0f, -500f, 4000f, landscape.regionAt(out))
        // and the photo can be slid up or down inside the frame, but not out of it
        val down = landscape.moved(out, panX = 0f, panY = 5f)
        assertEquals(0.125f, down.offsetY, 0.0001f)
        assertRegion(0f, -1000f, 4000f, landscape.regionAt(down))
    }

    @Test
    fun zoomingIn_isLimited() {
        val zoomed = landscape.moved(PhotoPlacement(), panX = 0f, panY = 0f, zoomChange = 100f)
        assertEquals(PhotoFrame.MAX_ZOOM, zoomed.zoom, 0f)
        assertRegion(1625f, 1125f, 750f, landscape.regionAt(zoomed)) // still centered on the photo
    }

    @Test
    fun zooming_keepsThePointUnderThePivotInPlace() {
        // pivot at the frame's top right quarter: the photo pixel under it must stay there
        val pivotX = 0.25f
        val pivotY = -0.25f
        val before = landscape.regionAt(PhotoPlacement())
        val zoomed = landscape.moved(PhotoPlacement(), 0f, 0f, zoomChange = 2f, pivotX = pivotX, pivotY = pivotY)
        val after = landscape.regionAt(zoomed)
        fun photoX(region: PhotoRegion) = region.left + (0.5f + pivotX) * region.side
        fun photoY(region: PhotoRegion) = region.top + (0.5f + pivotY) * region.side
        assertEquals(photoX(before), photoX(after), 0.01f)
        assertEquals(photoY(before), photoY(after), 0.01f)
        assertEquals(1500f, after.side, 0.01f)
    }

    @Test
    fun aSquarePhoto_hasNothingToSlideUntilItIsZoomedIn() {
        val square = PhotoFrame(1000, 1000)
        assertEquals(1f, square.minZoom, 0f)
        val moved = square.moved(PhotoPlacement(), panX = 1f, panY = 1f)
        assertEquals(0f, moved.offsetX, 0f)
        assertEquals(0f, moved.offsetY, 0f)
        val zoomed = square.moved(PhotoPlacement(), panX = 1f, panY = -1f, zoomChange = 2f)
        assertRegion(0f, 500f, 500f, square.regionAt(zoomed)) // slid as far as the zoomed photo allows
    }

    @Test
    fun aPortraitPhoto_slidesVertically() {
        val moved = portrait.moved(PhotoPlacement(), panX = 0f, panY = -0.1f)
        assertRegion(0f, 800f, 3000f, portrait.regionAt(moved))
    }
}
