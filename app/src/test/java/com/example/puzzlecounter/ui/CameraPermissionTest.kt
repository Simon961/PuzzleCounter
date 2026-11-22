package com.example.puzzlecounter.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the app asks for the camera: to scan, once - never because a photo from the gallery was confirmed. */
class CameraPermissionTest {

    private fun state(piles: Int, pileIndex: Int) = PuzzleUiState(
        selectedBackgroundId = 0, backgroundBrightness = 1f, calculatedPiles = piles, currentPileIndex = pileIndex,
    )

    @Test
    fun opening_theScanScreen_withoutPermission_asks() {
        assertTrue(shouldAskForCamera(hasPermission = false, isReviewing = false, checkFinished = false, alreadyAsked = false))
    }

    @Test
    fun withThePermission_orAfterAskingOnce_itDoesNotAsk() {
        assertFalse(shouldAskForCamera(hasPermission = true, isReviewing = false, checkFinished = false, alreadyAsked = false))
        assertFalse(shouldAskForCamera(hasPermission = false, isReviewing = false, checkFinished = false, alreadyAsked = true))
    }

    @Test
    fun aPhotoFromTheGallery_neverAsks_whileReviewed_andNotWhenTheLastOneIsConfirmed() {
        // the screen is opened with the photo from the gallery already under review
        assertFalse(shouldAskForCamera(hasPermission = false, isReviewing = true, checkFinished = false, alreadyAsked = false))
        // the last photo is confirmed: the review ends, the check is finished, the result opens - no question then
        assertFalse(shouldAskForCamera(hasPermission = false, isReviewing = false, checkFinished = true, alreadyAsked = false))
    }

    @Test
    fun theCheck_isFinished_afterTheLastPhoto() {
        assertFalse(state(piles = 2, pileIndex = 1).isFinished)
        assertFalse(state(piles = 2, pileIndex = 2).isFinished)
        assertTrue(state(piles = 2, pileIndex = 3).isFinished)
        assertTrue(state(piles = 1, pileIndex = 2).isFinished)
        assertEquals("a state without a check is not a finished one", false, state(piles = 0, pileIndex = 1).isFinished)
    }
}
