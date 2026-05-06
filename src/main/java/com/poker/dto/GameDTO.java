package com.poker.dto;

import lombok.Data;
import java.util.List;

@Data
public class GameDTO {
    private Long id;
    private String phase;
    private Integer currentHighestBet;
    private Integer pot;
    private Boolean isFinished;
    private Integer phaseCap;
    private Integer preFlopCap;
    private Integer flopCap;
    private Integer turnCap;
    private Integer riverCap;
    private List<ActionLogDTO> actionLogs;
}
