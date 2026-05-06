package com.poker.dto;

import lombok.Data;
import java.util.List;

@Data
public class PlayerDTO {
    private Long id;
    private Long userId;
    private String nickname;
    private String avatar;
    private Integer balance;
    private Integer borrowedTotal;
    private Boolean isActive;

    // Game info if in game
    private Integer pendingBet;
    private Integer currentRoundBet;
    private Integer totalBet;
    private Boolean isFolded;
    private Boolean isBetConfirmed;

    // Per-round breakdown for current game
    private List<RoundBetDTO> roundBets;
}
