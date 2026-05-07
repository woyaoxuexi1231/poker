package com.poker.dto;

import lombok.Data;
import java.util.List;

@Data
public class RoomDTO {
    private String roomId;
    private Long createdBy;
    private String createdByNickname;
    private Integer playerCount;
    private String status;
    private Boolean hasPassword; // 是否有密码（不返回密码明文）
    private GameDTO game;
    private List<PlayerDTO> players;
}
