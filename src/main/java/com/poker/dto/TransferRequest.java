package com.poker.dto;

import lombok.Data;

@Data
public class TransferRequest {
    private Long toUserId;
    private Integer amount;
}
