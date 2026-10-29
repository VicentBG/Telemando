package com.telemando.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TextModeTest {
    @Test fun restoresPreviouslySelectedMethod() {
        assertEquals(TextMode.PACED, parseTextMode("PACED"))
        assertEquals(TextMode.FAST, parseTextMode("FAST"))
    }

    @Test fun unknownPreferenceFallsBackToKnownWorkingMethod() {
        assertEquals(TextMode.FAST, parseTextMode("PHYSICAL"))
        assertEquals(TextMode.FAST, parseTextMode(null))
    }
}
