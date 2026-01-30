# MicLink 单元测试

## 概述

本项目包含全面的单元测试，覆盖核心业务逻辑，减少调试工作量。

## 测试覆盖

### ✅ ViewModel 测试
- **HomeViewModelTest**: 测试连接、断开、设置等核心功能
  - 用户ID验证（空白、非法字符）
  - 连接成功/失败场景
  - 服务生命周期管理
  - 设置更新（连接模式、音频质量）

- **CallViewModelTest**: 测试通话流程
  - 发起通话
  - 接听/拒绝来电
  - 挂断通话
  - 静音/扬声器切换
  - 通话状态管理

### ✅ 网络层测试
- **NetworkMonitorTest**: 测试网络质量检测
  - WiFi 质量评估
  - 蜂窝网络质量评估
  - 带宽速度评级
  - 网络状态变化监听

- **ConfigTest**: 测试配置有效性
  - 服务器 URL 格式验证
  - API 密钥存在性
  - ICE 服务器配置完整性
  - STUN/TURN 服务器可用性

### ✅ 服务测试
- **MicLinkServiceTest**: 测试前台服务
  - 上线/离线操作
  - 通话状态切换
  - Intent 创建验证

### ✅ 模型测试
- **ModelTest**: 测试数据模型
  - CallState 状态正确性
  - ConnectionMode 枚举值
  - AudioQuality 参数验证

## 运行测试

### 命令行运行所有测试
```bash
cd android
./gradlew test
```

### 运行特定测试类
```bash
./gradlew test --tests HomeViewModelTest
./gradlew test --tests CallViewModelTest
```

### 查看测试报告
测试完成后，在以下位置查看 HTML 报告：
```
android/app/build/reports/tests/testDebugUnitTest/index.html
```

### 在 Android Studio 中运行
1. 右键点击测试类或测试方法
2. 选择 "Run 'TestName'"
3. 查看测试结果面板

## 测试覆盖率

查看测试覆盖率报告：
```bash
./gradlew testDebugUnitTestCoverage
```

报告位置：
```
android/app/build/reports/coverage/test/debug/index.html
```

## 测试框架

- **JUnit 4**: 基础测试框架
- **MockK**: Kotlin Mock 框架
- **Turbine**: Flow 测试工具
- **Coroutines Test**: 协程测试支持
- **Truth**: Google 断言库
- **InstantTaskExecutorRule**: LiveData/ViewModel 测试支持

## 编写新测试

### 基本结构
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyTest {
    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()
    
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        // 初始化
    }
    
    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }
    
    @Test
    fun `test case description`() = runTest {
        // Given - 准备测试数据
        
        // When - 执行操作
        
        // Then - 验证结果
    }
}
```

### Mock 示例
```kotlin
val mockRepo = mockk<SignalingRepository>(relaxed = true)
every { mockRepo.connect(any()) } returns Result.success(Unit)
verify { mockRepo.connect("testUser") }
```

### Flow 测试示例
```kotlin
viewModel.someFlow.test {
    val item = awaitItem()
    assertEquals(expectedValue, item)
}
```

## 持续集成

可以配置 GitHub Actions 自动运行测试：

```yaml
# .github/workflows/test.yml
name: Run Tests
on: [push, pull_request]
jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          distribution: 'temurin'
          java-version: '17'
      - name: Run Tests
        run: cd android && ./gradlew test
```

## 最佳实践

1. **测试命名**: 使用 \`backtick\` 风格描述测试场景
2. **Given-When-Then**: 清晰分离测试步骤
3. **Mock 隔离**: 只 mock 外部依赖，不 mock 被测对象
4. **覆盖边界**: 测试正常、异常、边界情况
5. **异步测试**: 使用 `runTest` 和 `advanceUntilIdle()`
6. **清理资源**: 在 `@After` 中清理 mock 和资源

## 故障排除

### 测试失败常见原因
- **协程未完成**: 添加 `advanceUntilIdle()`
- **LiveData 未更新**: 检查 `InstantTaskExecutorRule`
- **Mock 未配置**: 使用 `relaxed = true` 或显式配置
- **Flow 未收集**: 使用 `test {}` 块收集 Flow

### 调试技巧
```kotlin
// 打印调试信息
println("Debug: ${viewModel.state.value}")

// 延迟查看中间状态
delay(100)
advanceTimeBy(100)

// 验证 mock 调用
verify(exactly = 1) { mockRepo.someMethod() }
```
