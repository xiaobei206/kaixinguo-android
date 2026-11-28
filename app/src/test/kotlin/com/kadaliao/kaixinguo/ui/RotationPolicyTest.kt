package com.kadaliao.kaixinguo.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RotationPolicyTest {
    @Test
    fun unlockedManualRotationPreservesExistingSensorAwareLandscapeBehavior() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE,
            requestedOrientationForManualTurn(landscape = true, dualLocked = false),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationForManualTurn(landscape = false, dualLocked = false),
        )
    }

    @Test
    fun manualRotationStillWorksWhileDualLockUsesFixedDirections() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            requestedOrientationForManualTurn(landscape = true, dualLocked = true),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            requestedOrientationForManualTurn(landscape = false, dualLocked = true),
        )
    }

    @Test
    fun sensorListenerIsDisabledOnlyWhileDualLockIsEnabled() {
        assertFalse(shouldListenForManualOrientation(held = true, dualLocked = true))
        assertFalse(shouldListenForManualOrientation(held = false, dualLocked = true))
        assertTrue(shouldListenForManualOrientation(held = true, dualLocked = false))
        assertTrue(shouldListenForManualOrientation(held = false, dualLocked = false))
        assertFalse(shouldListenForManualOrientation(held = null, dualLocked = false))
    }
}
