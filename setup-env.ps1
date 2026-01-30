# 在 PowerShell 配置文件中添加以下内容
# 编辑文件: $PROFILE （通常在 C:\Users\<username>\Documents\PowerShell\profile.ps1）

# MicLink Android 项目环境变量配置
$env:JAVA_HOME = "D:\Android\Android Studio\jbr"
$env:GRADLE_USER_HOME = "$env:USERPROFILE\.gradle"

# MicLink 服务器配置（可选）
# 取消注释以下行以配置服务器环境变量
# $env:API_KEY = "your-api-key-here"
# $env:SERVER_PORT = "8080"
# $env:ENABLE_IP_WHITELIST = "false"
# $env:ALLOWED_IPS = ""

# 添加gradle bin目录到PATH (可选)
# $env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "MicLink Android environment configured successfully!" -ForegroundColor Green
Write-Host "JAVA_HOME: $env:JAVA_HOME"
Write-Host "GRADLE_USER_HOME: $env:GRADLE_USER_HOME"
