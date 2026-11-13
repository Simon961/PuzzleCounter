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
    val currentPhotoPath: String? = null,
    val currentQualityResult: QualityResult? = null,
    val currentPieces: Int = 0,
    /** Blobs of the current photo that hold several touching pieces, whose number is estimated. */
    val currentClusters: Int = 0,
    /** Whether the current photo comes from the gallery (it is then chosen again, not taken again). */
    val currentPhotoFromGallery: Boolean = false,

    /** The pieces of all photos confirmed so far, and the places in them where the number was estimated. */
    val countedPieces: Int = 0,
    val countedClusters: Int = 0,

    /** A photo from the gallery on its way to the analysis: it is placed in the square frame first. */
    val pickedPhoto: PickedPhoto = PickedPhoto.None,

    // Lists
    val history: List<PuzzleHistoryEntry> = emptyList(),
    val debugSessions: List<DebugScanSession> = emptyList(),
)
