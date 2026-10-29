package com.telemando.app

import com.ryzen.tvremote.protocol.ConnectionStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    private val connected = ConnectionStatus.Connected("192.0.2.1", "TV")

    @Test fun shortBackgroundTripKeepsWorkingSession() {
        assertFalse(shouldReconnectOnResume(connected, 30_000L, true, true))
        assertTrue(shouldReconnectOnResume(connected, 300_001L, true, true))
    }

    @Test fun reconnectsDisconnectedOrFailedSessionOnReturn() {
        assertTrue(shouldReconnectOnResume(ConnectionStatus.Disconnected, 1_000L, true, true))
        assertTrue(shouldReconnectOnResume(ConnectionStatus.Error("offline"), 1_000L, true, true))
    }

    @Test fun doesNotInterruptPairingOrSwitchingTv() {
        assertFalse(shouldReconnectOnResume(ConnectionStatus.NeedsPairing(), 900_000L, true, true))
        assertFalse(shouldReconnectOnResume(ConnectionStatus.Connecting("TV"), 900_000L, true, true))
        assertFalse(shouldReconnectOnResume(connected, 900_000L, false, true))
        assertFalse(shouldReconnectOnResume(connected, 900_000L, true, false))
        assertFalse(shouldReconnectOnResume(connected, null, true, true))
    }
}
