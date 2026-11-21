package com.kadaliao.kaixinguo.model

import dev.openhongguo.ErrorCode
import dev.openhongguo.OpenhongguoException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackFailureTest {
    @Test
    fun preservesServerBusinessCodeAndMessageFromSdkError() {
        val error = OpenhongguoException(
            ErrorCode.PARAM_INVALID,
            "openhongguo: invalid request (code 100103, PARAM_INVALID)",
        )
        val failure = PlaybackFailure.from(error, "episode-1", emptyList())

        assertEquals("100103", failure.serverBusinessCode)
        assertEquals("PARAM_INVALID", failure.serverErrorName)
        assertEquals("invalid request", failure.serverErrorMessage)
        assertEquals("PARAM_INVALID", failure.sdkCode)
        assertFalse(failure.canRefreshDevice)
    }

    @Test
    fun emptyAnonymousResponseDoesNotInventServerBusinessCode() {
        val error = OpenhongguoException(
            ErrorCode.ANONYMOUS_LIMITED,
            "openhongguo: anonymous response was empty",
        )
        val failure = PlaybackFailure.from(error, "episode-2", emptyList())

        assertNull(failure.serverBusinessCode)
        assertNull(failure.serverErrorMessage)
        assertTrue(failure.canRefreshDevice)
        assertTrue(failure.responseBodyNote.contains("响应体为空"))
    }

    @Test
    fun associatesHttpStatusAndEndpointWithTheMostRecentPlaybackRequest() {
        val trace = SdkApiTrace(
            time = "2026-11-21T01:00:00Z",
            message = "HTTP POST https://api.example/novel/player/video_model/v1/ -> 200",
            method = "POST",
            endpoint = "api.example/novel/player/video_model/v1/",
            httpStatus = 200,
        )
        val failure = PlaybackFailure.from(
            OpenhongguoException(ErrorCode.ANONYMOUS_LIMITED, "openhongguo: anonymous response was empty"),
            "episode-3",
            listOf(trace),
        )

        assertEquals(200, failure.httpStatus)
        assertEquals("api.example/novel/player/video_model/v1/", failure.endpoint)
    }
}
