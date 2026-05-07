package com.poker.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("poker_game_player")
public class GamePlayer {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long gameId;
    private Long roomPlayerId;
    private Integer pendingBet;
    private Integer currentRoundBet;
    private Integer totalBet;
    private Boolean isFolded;
    private Boolean isBetConfirmed;
}
