package com.poker.dto;

import lombok.Data;

@Data
public class PlayerGameDTO {
    private Long gameId;
    private Integer totalBet;
    private Integer wonAmount;
    private Integer result;
}
