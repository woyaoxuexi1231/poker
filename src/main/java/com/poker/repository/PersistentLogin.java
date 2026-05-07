package com.poker.repository;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * Remember-Me 持久化登录实体
 */
@Data
public class PersistentLogin {
    private String username;
    private String series;
    private String token;
    private LocalDateTime lastUsed;
}
