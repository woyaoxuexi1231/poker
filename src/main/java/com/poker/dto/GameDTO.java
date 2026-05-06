package com.poker.dto;

import lombok.Data;
import java.util.List;

@Data
public class GameDTO {
    private Long id;
    private Integer roundNumber;
    private Integer currentHighestBet;
    private Integer pot;
    private Boolean isFinished;
    private List<ActionLogDTO> actionLogs;
}
