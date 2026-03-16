@echo off
chcp 65001 >nul
echo ==========================================
echo    Telegram 记账机器人启动脚本
echo ==========================================
echo.

:: 设置配置文件路径
set CONFIG_PATH=config

:: 检查配置文件是否存在
if not exist "%CONFIG_PATH%\application.yml" (
    echo 错误: 配置文件不存在: %CONFIG_PATH%\application.yml
    echo 请确保 config 目录下有 application.yml 配置文件
    pause
    exit /b 1
)

:: 运行应用
echo 正在启动记账机器人...
echo 配置文件: %CONFIG_PATH%\application.yml
echo.

:: 检查是开发模式还是生产模式
if exist "target\accounting-bot-1.0.0.jar" (
    :: 生产模式：运行打包后的jar
    echo 使用生产模式启动...
    java -jar target\accounting-bot-1.0.0.jar --spring.config.location=file:%CONFIG_PATH%/application.yml
) else (
    :: 开发模式：使用maven运行
    echo 使用开发模式启动...
    mvnw spring-boot:run --spring.config.location=file:%CONFIG_PATH%/application.yml
)

pause
