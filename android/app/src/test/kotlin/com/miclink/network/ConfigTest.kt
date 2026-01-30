package com.miclink.network

import org.junit.Test
import org.junit.Assert.*

/**
 * 配置测试 - 验证 Config 对象的完整性
 */
class ConfigTest {

    @Test
    fun `SERVER_URL should not be empty`() {
        // Then
        assertTrue(Config.SERVER_URL.isNotEmpty())
    }

    @Test
    fun `SERVER_URL should start with ws or wss`() {
        // Then
        assertTrue(
            "SERVER_URL should use WebSocket protocol",
            Config.SERVER_URL.startsWith("ws://") || 
            Config.SERVER_URL.startsWith("wss://")
        )
    }

    @Test
    fun `API_KEY should not be empty`() {
        // Then
        assertTrue("API_KEY should be configured", Config.API_KEY.isNotEmpty())
    }

    @Test
    fun `getIceServers should return non-empty list`() {
        // When
        val iceServers = Config.getIceServers()

        // Then
        assertTrue("ICE servers list should not be empty", iceServers.isNotEmpty())
    }

    @Test
    fun `getIceServers should contain multiple servers`() {
        // When
        val iceServers = Config.getIceServers()

        // Then
        assertTrue("Should have at least 2 ICE servers for redundancy", iceServers.size >= 2)
    }

    @Test
    fun `each ICE server should have valid URLs`() {
        // When
        val iceServers = Config.getIceServers()

        // Then
        iceServers.forEach { server ->
            assertNotNull("ICE server should have URLs", server.urls)
            assertTrue("Each ICE server should have at least one URL", server.urls.isNotEmpty())
        }
    }

    @Test
    fun `ICE server URLs should be in valid format`() {
        // When
        val iceServers = Config.getIceServers()

        // Then
        iceServers.forEach { server ->
            server.urls.forEach { url ->
                val isValidUrl = url.startsWith("stun:") || url.startsWith("turn:")
                assertTrue("ICE URL must start with stun: or turn: , got: $url", isValidUrl)
            }
        }
    }
}
