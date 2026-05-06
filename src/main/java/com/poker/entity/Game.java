package com.poker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("game")
public class Game {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private Integer roundNumber;
    private Integer currentHighestBet;
    private Integer pot;
    private Boolean isFinished;
    private LocalDateTime createdTime;
}
