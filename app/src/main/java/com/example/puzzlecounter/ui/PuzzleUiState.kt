package com.example.puzzlecounter.ui

import com.example.puzzlecounter.analyzer.QualityResult

data class PuzzleUiState(
    // Settings
    val selectedBackgroundId: Int,
    val backgroundBrightness: Float,

    // Puzzle to check
    val targetPieceCount: Int = 0,
    val calculatedPiles: Int = 0,
    val currentPileIndex: Int = 1,

    // Capture & analysis of the current photo
    val isReviewingCurrentPhoto: Boolean = false,
    val isAnalyzing: Boolean = false,
    /** The preview of the current photo (see [ScanPhotoStore]); the file exists only while the photo is under review. */
    val currentPhotoPath: String? = null,
    val currentQualityResult: QualityResult? = null,
    val currentPieces: Int = 0,
    /** Blobs of the current photo that hold several touching pieces, whose number is estimated. */
    val currentClusters: Int = 0,
    /** Whether the current photo comes from the gallery (it is then chosen again, not taken again). */
    val currentPhotoFromGallery: Boolean = false,

    /** The photos confirmed so far, in the order they were taken. */
    val photos: List<PhotoCount> = emptyList(),

    /** A photo from the gallery on its way to the analysis: it is placed in the square frame first. */
    val pickedPhoto: PickedPhoto = PickedPhoto.None,

    // Lists
    val history: List<PuzzleHistoryEntry> = emptyList(),
) {
    /** Every photo of the check is confirmed: its result is what comes next. */
    val isFinished: Boolean get() = calculatedPiles > 0 && currentPileIndex > calculatedPiles

    /** The pieces of all photos confirmed so far. */
    val countedPieces: Int get() = photos.sumOf { it.pieces }

    /** The places in them where the number was estimated. */
    val countedClusters: Int get() = photos.sumOf { it.clusters }
}
