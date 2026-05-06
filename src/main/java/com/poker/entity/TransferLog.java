package com.poker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("transfer_log")
public class TransferLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private Long fromUserId;
    private Long toUserId;
    private Integer amount;
    private LocalDateTime createTime;
}
