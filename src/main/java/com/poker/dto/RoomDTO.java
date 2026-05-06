package com.poker.dto;

import lombok.Data;
import java.util.List;

@Data
public class RoomDTO {
    private String roomId;
    private Long createdBy;
    private String createdByNickname;
    private Integer playerCount;
    private GameDTO game;
    private List<PlayerDTO> players;
}
