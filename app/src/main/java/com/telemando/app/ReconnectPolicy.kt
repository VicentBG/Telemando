package com.telemando.app

import com.ryzen.tvremote.protocol.ConnectionStatus

/** Reopen stale sessions after a long absence; never interrupt an in-progress pairing. */
internal fun shouldReconnectOnResume(
    status: ConnectionStatus,
    backgroundedForMs: Long?,
    autoReconnect: Boolean,
    hasSavedHost: Boolean
): Boolean {
    if (backgroundedForMs == null || !autoReconnect || !hasSavedHost) return false
    return when (status) {
        is ConnectionStatus.Disconnected, is ConnectionStatus.Error -> true
        is ConnectionStatus.Connected -> backgroundedForMs > 5 * 60_000L
        is ConnectionStatus.NeedsPairing, is ConnectionStatus.Connecting -> false
    }
}
