package com.kadaliao.kaixinguo.ui

import android.content.pm.ActivityInfo

/**
 * Choose an orientation request for the manual rotate button.
 *
 * When the dual lock is enabled, use fixed portrait/landscape requests so
 * the device orientation sensor cannot undo the viewer's manual choice.
 * When unlocked, preserve the existing sensor-aware landscape behavior.
 */
internal fun requestedOrientationForManualTurn(landscape: Boolean, dualLocked: Boolean): Int =
    when {
        dualLocked && landscape -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        dualLocked -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        landscape -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

/** Only listen for the phone catching up to a manual turn while unlocked. */
internal fun shouldListenForManualOrientation(held: Boolean?, dualLocked: Boolean): Boolean =
    held != null && !dualLocked
