package org.kin.rsocket.example.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * @author huangjianqin
 * @date 2021/5/16
 */
@RefreshScope
@Component
public class Config {
    @Value("${A:}")
    private String a;
    @Value("${B:}")
    private String b;

    @Override
    public String toString() {
        return "Config{" +
                "a='" + a + '\'' +
                ", b='" + b + '\'' +
                '}';
    }
}
