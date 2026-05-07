package com.poker.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("poker_room")
public class Room {
    @TableId
    private String roomId;
    private Long createdBy;
    private LocalDateTime createdTime;
    private String status;
    private String password; // 房间密码，NULL表示无密码
}
