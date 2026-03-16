# Telegram 自动记账机器人

一个基于 Java + Spring Boot 开发的 Telegram 记账机器人，支持自然语言记账、自动分类、收支统计等功能。

## 功能特性

- **自然语言记账**: 直接输入 "花了50买咖啡" 或 "收入5000工资"
- **自动分类**: 自动识别餐饮、交通、购物、娱乐等分类
- **收支统计**: 今日/本月收支统计，分类统计
- **群组支持**: 可在群组中使用，支持多人记账
- **数据持久化**: 使用 SQLite 本地存储，零配置

## 快速开始

### 1. 获取 Bot Token

1. 在 Telegram 中搜索 [@BotFather](https://t.me/botfather)
2. 发送 `/newbot` 创建新机器人
3. 按提示设置机器人名称和用户名
4. 保存获得的 **Bot Token**

### 2. 配置环境变量

```cmd
set BOT_TOKEN=你的BotToken
set BOT_USERNAME=你的Bot用户名
```

### 3. 运行项目

```cmd
# 方式1: 使用启动脚本
start.bat

# 方式2: 直接运行
mvnw spring-boot:run

# 方式3: 打包后运行
mvnw clean package
java -jar target/accounting-bot-1.0.0.jar
```

## 使用指南

### 命令列表

| 命令 | 说明 | 示例 |
|------|------|------|
| `/start` | 开始使用 | - |
| `/help` | 显示帮助 | - |
| `/add` | 记录支出 | `/add 100 午餐` |
| `/income` | 记录收入 | `/income 5000 工资` |
| `/list` | 查看最近记录 | - |
| `/today` | 今日统计 | - |
| `/month` | 本月统计 | - |
| `/category` | 分类统计 | - |
| `/delete [ID]` | 删除记录 | `/delete 1` |

### 自然语言记账

直接发送消息即可记账：
- `花了50买咖啡` - 记录支出
- `收入5000工资` - 记录收入
- `今天交通费20` - 记录支出

## 项目结构

```
accounting-bot/
├── src/main/java/com/bot/accounting/
│   ├── AccountingBotApplication.java
│   ├── config/          # 配置类
│   ├── bot/             # Bot 核心
│   │   ├── AccountingBot.java
│   │   ├── CommandDispatcher.java
│   │   └── command/     # 命令处理器
│   ├── service/         # 业务逻辑
│   ├── repository/      # 数据访问
│   ├── entity/          # 实体类
│   └── dto/             # 数据传输对象
├── src/main/resources/
│   └── application.yml
├── data/                # SQLite 数据库目录
├── pom.xml
└── start.bat
```

## 技术栈

- Java 17
- Spring Boot 3.x
- Spring Data JPA
- SQLite
- Telegram Bots API

## 许可证

MIT License
