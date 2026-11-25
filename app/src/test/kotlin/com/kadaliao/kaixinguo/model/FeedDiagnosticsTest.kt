package com.kadaliao.kaixinguo.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedDiagnosticsTest {
    @Test
    fun removesUrlQueryParametersBeforeCopyingDiagnostics() {
        val raw = "HTTP GET https://api.example.test/v1/discover?device_id=device-SECRET&token=token-SECRET -> 429"
        val safe = sanitizeDiagnosticText(raw)

        assertTrue(safe.contains("https://api.example.test/v1/discover?<query-redacted>"))
        assertFalse(safe.contains("device-SECRET"))
        assertFalse(safe.contains("token-SECRET"))
    }

    @Test
    fun redactsAuthorizationAndDeviceCredentialFields() {
        val raw = "Authorization: Bearer bearer-SECRET; install_id=install-SECRET; signature=sign-SECRET"
        val safe = sanitizeDiagnosticText(raw)

        assertFalse(safe.contains("bearer-SECRET"))
        assertFalse(safe.contains("install-SECRET"))
        assertFalse(safe.contains("sign-SECRET"))
        assertTrue(safe.contains("Authorization: <redacted>"))
    }
}
