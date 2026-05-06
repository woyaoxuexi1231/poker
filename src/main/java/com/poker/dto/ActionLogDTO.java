package com.poker.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ActionLogDTO {
    private Long userId;
    private String nickname;
    private String actionType;
    private Integer amount;
    private Integer roundNumber;
    private LocalDateTime createTime;
}
