package com.poker.dto;

import lombok.Data;

@Data
public class GameDTO {
    private Long id;
    private Integer roundNumber;
    private Integer pot;
    private Boolean isFinished;
}
