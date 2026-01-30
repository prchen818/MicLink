package com.miclink.model

import org.junit.Test
import org.junit.Assert.*

/**
 * 模型测试 - 验证数据类的正确性
 */
class ModelTest {

    @Test
    fun `CallState Idle should be correct type`() {
        // When
        val state = CallState.Idle

        // Then
        assertTrue(state is CallState.Idle)
    }

    @Test
    fun `CallState Ringing outgoing should have correct properties`() {
        // Given
        val peerId = "targetUser"

        // When
        val state = CallState.Ringing(peerId, isIncoming = false)

        // Then
        assertEquals(peerId, state.peerId)
        assertFalse(state.isIncoming)
    }

    @Test
    fun `CallState Ringing incoming should have correct properties`() {
        // Given
        val peerId = "caller"

        // When
        val state = CallState.Ringing(peerId, isIncoming = true)

        // Then
        assertEquals(peerId, state.peerId)
        assertTrue(state.isIncoming)
    }

    @Test
    fun `CallState Connecting should be correct type`() {
        // When
        val state = CallState.Connecting

        // Then
        assertTrue(state is CallState.Connecting)
    }

    @Test
    fun `CallState Connected should have correct properties`() {
        // When
        val state = CallState.Connected(peerId = "peer123", connectionType = "p2p")

        // Then
        assertEquals("peer123", state.peerId)
        assertEquals("p2p", state.connectionType)
    }

    @Test
    fun `CallState Connected relay mode should be correct`() {
        // When
        val state = CallState.Connected(peerId = "peer456", connectionType = "relay")

        // Then
        assertEquals("peer456", state.peerId)
        assertEquals("relay", state.connectionType)
    }

    @Test
    fun `CallState Disconnected should be correct type`() {
        // When
        val state = CallState.Disconnected

        // Then
        assertTrue(state is CallState.Disconnected)
    }

    @Test
    fun `CallState Error should contain message`() {
        // Given
        val errorMessage = "Connection failed"

        // When
        val state = CallState.Error(errorMessage)

        // Then
        assertEquals(errorMessage, state.message)
    }

    @Test
    fun `ConnectionMode AUTO should be available`() {
        // When
        val mode = ConnectionMode.AUTO

        // Then
        assertEquals("AUTO", mode.name)
    }

    @Test
    fun `ConnectionMode P2P_ONLY should be available`() {
        // When
        val mode = ConnectionMode.P2P_ONLY

        // Then
        assertEquals("P2P_ONLY", mode.name)
    }

    @Test
    fun `ConnectionMode RELAY_ONLY should be available`() {
        // When
        val mode = ConnectionMode.RELAY_ONLY

        // Then
        assertEquals("RELAY_ONLY", mode.name)
    }

    @Test
    fun `AudioQuality LOW should have correct properties`() {
        // When & Then
        assertEquals("LOW", AudioQuality.LOW.name)
        assertEquals(32000, AudioQuality.LOW.bitrate)
        assertEquals(8000, AudioQuality.LOW.sampleRate)
    }

    @Test
    fun `AudioQuality MEDIUM should have correct properties`() {
        // When & Then
        assertEquals("MEDIUM", AudioQuality.MEDIUM.name)
        assertEquals(64000, AudioQuality.MEDIUM.bitrate)
        assertEquals(16000, AudioQuality.MEDIUM.sampleRate)
    }

    @Test
    fun `AudioQuality HIGH should have correct properties`() {
        // When & Then
        assertEquals("HIGH", AudioQuality.HIGH.name)
        assertEquals(128000, AudioQuality.HIGH.bitrate)
        assertEquals(48000, AudioQuality.HIGH.sampleRate)
    }

    @Test
    fun `AudioQuality bitrate should increase with quality`() {
        // Then
        assertTrue(AudioQuality.LOW.bitrate < AudioQuality.MEDIUM.bitrate)
        assertTrue(AudioQuality.MEDIUM.bitrate < AudioQuality.HIGH.bitrate)
    }

    @Test
    fun `AudioQuality sample rate should increase with quality`() {
        // Then
        assertTrue(AudioQuality.LOW.sampleRate < AudioQuality.MEDIUM.sampleRate)
        assertTrue(AudioQuality.MEDIUM.sampleRate < AudioQuality.HIGH.sampleRate)
    }
}

