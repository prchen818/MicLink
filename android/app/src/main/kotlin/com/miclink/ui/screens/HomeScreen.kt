package com.miclink.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miclink.model.AudioQuality
import com.miclink.model.ConnectionMode
import com.miclink.repository.ConnectionState
import com.miclink.viewmodel.HomeViewModel
import kotlinx.coroutines.flow.collect

/**
 * 主界面 - 显示在线用户列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onCallUser: (String) -> Unit
) {
    val currentUserId by viewModel.currentUserId.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val onlineUsers by viewModel.onlineUsers.collectAsState()
    val connectionMode by viewModel.connectionMode.collectAsState()
    val audioQuality by viewModel.audioQuality.collectAsState()
    
    var userIdInput by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    
    // 监听错误消息
    LaunchedEffect(Unit) {
        viewModel.errorMessage.collect { message ->
            // 这里可以显示Snackbar
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MicLink") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, "设置")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (currentUserId == null) {
                // 未连接 - 显示登录界面
                LoginSection(
                    userIdInput = userIdInput,
                    onUserIdChange = { userIdInput = it },
                    onConnect = { viewModel.connect(userIdInput) },
                    isConnecting = connectionState is ConnectionState.Connecting
                )
            } else {
                // 已连接 - 显示在线用户列表
                Column(modifier = Modifier.fillMaxSize()) {
                    // 状态栏
                    ConnectionStatusBar(
                        userId = currentUserId!!,
                        connectionState = connectionState,
                        onDisconnect = { viewModel.disconnect() }
                    )
                    
                    // 用户列表
                    OnlineUserList(
                        users = onlineUsers,
                        onCallUser = onCallUser
                    )
                }
            }
        }
    }
    
    // 设置对话框
    if (showSettings) {
        SettingsDialog(
            connectionMode = connectionMode,
            audioQuality = audioQuality,
            onConnectionModeChange = { viewModel.setConnectionMode(it) },
            onAudioQualityChange = { viewModel.setAudioQuality(it) },
            onDismiss = { showSettings = false }
        )
    }
}

@Composable
fun LoginSection(
    userIdInput: String,
    onUserIdChange: (String) -> Unit,
    onConnect: () -> Unit,
    isConnecting: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(80.dp))
        
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "MicLink",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "语音通话",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(40.dp))
        
        OutlinedTextField(
            value = userIdInput,
            onValueChange = onUserIdChange,
            label = { Text("输入你的ID") },
            placeholder = { Text("字母和数字") },
            singleLine = true,
            enabled = !isConnecting,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onConnect,
            enabled = !isConnecting && userIdInput.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("连接中...")
            } else {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("加入", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun ConnectionStatusBar(
    userId: String,
    connectionState: ConnectionState,
    onDisconnect: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when (connectionState) {
            is ConnectionState.Connected -> MaterialTheme.colorScheme.primaryContainer
            is ConnectionState.Error -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "你的ID: $userId",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = when (connectionState) {
                        is ConnectionState.Connected -> "在线"
                        is ConnectionState.Connecting -> "连接中..."
                        is ConnectionState.Disconnected -> "未连接"
                        is ConnectionState.Error -> "错误: ${connectionState.message}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onDisconnect) {
                Icon(Icons.Default.Close, "断开连接")
            }
        }
    }
}

@Composable
fun OnlineUserList(
    users: List<String>,
    onCallUser: (String) -> Unit
) {
    if (users.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无其他在线用户",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "在线用户 (${users.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            
            items(users) { user ->
                UserItem(
                    userId = user,
                    onClick = { onCallUser(user) }
                )
            }
        }
    }
}

@Composable
fun UserItem(
    userId: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Column {
                    Text(
                        text = userId,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "在线",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF4CAF50)
                    )
                }
            }
            
            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "呼叫",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun SettingsDialog(
    connectionMode: ConnectionMode,
    audioQuality: AudioQuality,
    onConnectionModeChange: (ConnectionMode) -> Unit,
    onAudioQualityChange: (AudioQuality) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通话设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // 连接模式
                Text(
                    text = "连接模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ConnectionMode.values().forEach { mode ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                onConnectionModeChange(mode)
                            }
                        ) {
                            RadioButton(
                                selected = connectionMode == mode,
                                onClick = { onConnectionModeChange(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when (mode) {
                                        ConnectionMode.AUTO -> "自动 (推荐)"
                                        ConnectionMode.P2P_ONLY -> "仅P2P"
                                        ConnectionMode.RELAY_ONLY -> "仅中转"
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
                
                Divider()
                
                // 音频质量
                Text(
                    text = "音频质量",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AudioQuality.values().forEach { quality ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                onAudioQualityChange(quality)
                            }
                        ) {
                            RadioButton(
                                selected = audioQuality == quality,
                                onClick = { onAudioQualityChange(quality) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = quality.displayName,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}
