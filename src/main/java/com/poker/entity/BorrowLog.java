package com.poker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("borrow_log")
public class BorrowLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String roomId;
    private Long userId;
    private Integer amount;
    private LocalDateTime createTime;
}
