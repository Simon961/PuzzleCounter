package com.example.puzzlecounter.ui

import androidx.annotation.StringRes
import com.example.puzzlecounter.R
import com.example.puzzlecounter.analyzer.PhotoIssue

/** The message that tells the user what is wrong with the photo and what to do. */
@get:StringRes
internal val PhotoIssue.messageRes: Int
    get() = when (this) {
        PhotoIssue.TOO_DARK -> R.string.issue_too_dark
        PhotoIssue.OVEREXPOSED -> R.string.issue_overexposed
        PhotoIssue.BLURRY -> R.string.issue_blurry
        PhotoIssue.PIECES_TOO_SMALL -> R.string.issue_pieces_too_small
        PhotoIssue.STACKED_PIECES -> R.string.issue_stacked
        PhotoIssue.ANALYSIS_FAILED -> R.string.issue_analysis_failed
        PhotoIssue.MODEL_UNAVAILABLE -> R.string.issue_model_unavailable
        PhotoIssue.OUT_OF_MEMORY -> R.string.issue_out_of_memory
    }
