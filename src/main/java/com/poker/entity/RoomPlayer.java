package com.poker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("poker_room_player")
public class RoomPlayer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private Long userId;
    private Integer balance;
    private Integer borrowedTotal;
    private Boolean isActive;
    private LocalDateTime joinedTime;
}
