package com.miclink.viewmodel

import android.app.Application
import android.util.Log
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.miclink.model.AudioQuality
import com.miclink.model.ConnectionMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

/**
 * HomeViewModel 单元测试
 * 
 * 测试范围：状态管理、本地数据验证
 * 不测试：网络连接（需要集成测试或真机测试）
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = mockk<Application>(relaxed = true)
        every { application.applicationContext } returns application
        
        // Mock Android Log 类的所有调用
        mockkStatic(Log::class)
        
        viewModel = HomeViewModel(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be correct`() {
        // Given
        viewModel = HomeViewModel(application)

        // Then - 初始状态验证
        assertNull(viewModel.currentUserId.value)
        assertEquals(ConnectionMode.AUTO, viewModel.connectionMode.value)
        assertEquals(AudioQuality.MEDIUM, viewModel.audioQuality.value)
    }
}
