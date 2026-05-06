package com.poker.dto;

import lombok.Data;

@Data
public class ActionRequest {
    private String actionType;
    private Integer amount;
}
