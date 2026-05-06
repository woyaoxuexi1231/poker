package com.poker.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("room")
public class Room {
    @TableId
    private String roomId;
    private Long createdBy;
    private LocalDateTime createdTime;
    private String status;
}
