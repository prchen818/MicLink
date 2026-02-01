package com.miclink.viewmodel

import com.miclink.model.CallState
import org.junit.Test
import org.junit.Assert.*

/**
 * CallViewModel 单元测试
 * 
 * 注意：CallViewModel依赖WebRTC、网络等复杂组件，单元测试难以完全隔离
 * 因此仅测试CallState模型，真实功能需要集成/真机测试
 */
class CallViewModelTest {

    @Test
    fun `CallState Idle represents initial state`() {
        // When
        val state = CallState.Idle

        // Then
        assertTrue(state is CallState.Idle)
    }

    @Test
    fun `CallState Ringing can represent incoming call`() {
        // When
        val state = CallState.Ringing("caller", isIncoming = true)

        // Then
        assertEquals("caller", state.peerId)
        assertTrue(state.isIncoming)
    }

    @Test
    fun `CallState Ringing can represent outgoing call`() {
        // When
        val state = CallState.Ringing("recipient", isIncoming = false)

        // Then
        assertEquals("recipient", state.peerId)
        assertFalse(state.isIncoming)
    }

    @Test
    fun `CallState Connecting represents mid-setup phase`() {
        // When
        val state = CallState.Connecting(
            peerId = "peer123",
            iceConnectionState = "CHECKING",
            signalingState = "STABLE",
            iceGatheringState = "GATHERING"
        )

        // Then
        assertTrue(state is CallState.Connecting)
        assertEquals("peer123", state.peerId)
    }

    @Test
    fun `CallState Connected can use P2P mode`() {
        // When
        val state = CallState.Connected("peer", "p2p")

        // Then
        assertEquals("peer", state.peerId)
        assertEquals("p2p", state.connectionType)
    }

    @Test
    fun `CallState Connected can use relay mode`() {
        // When
        val state = CallState.Connected("peer", "relay")

        // Then
        assertEquals("peer", state.peerId)
        assertEquals("relay", state.connectionType)
    }

    @Test
    fun `CallState Disconnected represents call end`() {
        // When
        val state = CallState.Disconnected

        // Then
        assertTrue(state is CallState.Disconnected)
    }

    @Test
    fun `CallState Error contains error message`() {
        // When
        val state = CallState.Error("Connection timeout")

        // Then
        assertEquals("Connection timeout", state.message)
    }
}
