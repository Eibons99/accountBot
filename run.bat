@echo off
chcp 65001 >nul
echo ==========================================
echo    Telegram 记账机器人
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

:: 检查jar文件是否存在
if not exist "accounting-bot-1.0.0.jar" (
    echo 错误: 找不到 accounting-bot-1.0.0.jar
    echo 请先运行 mvnw clean package 打包
    pause
    exit /b 1
)

:: 检查lib目录是否存在
if not exist "lib" (
    echo 错误: 找不到 lib 目录
    echo 请先运行 mvnw clean package 打包
    pause
    exit /b 1
)

:: 运行应用
echo 正在启动记账机器人...
echo 配置文件: %CONFIG_PATH%\application.yml
echo.

java -jar accounting-bot-1.0.0.jar --spring.config.location=file:%CONFIG_PATH%/application.yml

pause
