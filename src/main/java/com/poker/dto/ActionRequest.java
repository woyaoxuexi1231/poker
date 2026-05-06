package com.poker.dto;

import lombok.Data;

@Data
public class ActionRequest {
    private String actionType;
    private Integer amount;
    private Integer preFlopCap;
    private Integer flopCap;
    private Integer turnCap;
    private Integer riverCap;
}
