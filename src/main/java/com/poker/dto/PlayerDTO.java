package com.poker.dto;

import lombok.Data;

@Data
public class PlayerDTO {
    private Long id;
    private Long userId;
    private String nickname;
    private Integer balance;
    private Integer borrowedTotal;
    private Boolean isActive;

    // Game info if in game
    private Integer pendingBet;
    private Integer currentRoundBet;
    private Integer totalBet;
    private Boolean isFolded;
    private Boolean isBetConfirmed;
}
