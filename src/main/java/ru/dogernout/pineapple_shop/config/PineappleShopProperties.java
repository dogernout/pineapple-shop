package ru.dogernout.pineapple_shop.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "pineapple-shop")
public class PineappleShopProperties {

    private String token;
    private Long adminTelegramId;
}
