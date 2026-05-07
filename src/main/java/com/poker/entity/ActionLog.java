package com.poker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("poker_action_log")
public class ActionLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Long userId;
    private String actionType;
    private Integer amount;
    private Integer roundNumber;
    private LocalDateTime createTime;
}
