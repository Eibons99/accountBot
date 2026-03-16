package com.bot.accounting.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Data
@Configuration
@ConfigurationProperties(prefix = "bot")
public class BotConfig {
    private String username;
    private String token;
    
    // 运行模式：longpolling 或 webhook
    private String mode = "longpolling";  // 默认使用长轮询
    
    // Webhook 配置（当 mode=webhook 时使用）
    private String webhookUrl;  // 外网访问地址，如 https://your-domain.com/webhook
    private String webhookPath = "/webhook";  // 回调路径
    private Integer serverPort = 8443;  // Webhook 服务器端口
    
    // 群组记账配置
    private BigDecimal exchangeRate = new BigDecimal("1");
    private BigDecimal feeRate = new BigDecimal("0");
    private String dayCutoffTime = "00:00";  // 日切时间
    
    // 管理员配置（Telegram用户ID列表）
    private java.util.List<Long> adminIds;
    
    /**
     * 判断是否使用 Webhook 模式
     */
    public boolean isWebhookMode() {
        return "webhook".equalsIgnoreCase(mode);
    }
}
