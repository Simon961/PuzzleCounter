package com.example.puzzlecounter.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** The activity a (possibly wrapped) context belongs to, or null if there is none. */
internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
