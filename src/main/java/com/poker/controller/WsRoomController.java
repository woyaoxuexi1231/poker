package com.poker.controller;

import com.poker.dto.ActionRequest;
import com.poker.dto.BorrowRequest;
import com.poker.dto.TransferRequest;
import com.poker.service.PokerService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WsRoomController {
    
    private final PokerService pokerService;

    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.broadcastRoom(roomId);
        }
    }

    @MessageMapping("/room/{roomId}/bet")
    public void bet(@DestinationVariable String roomId, ActionRequest req, Principal principal) {
        if (principal != null) {
            pokerService.handleBet(roomId, principal.getName(), req);
        }
    }

    @MessageMapping("/room/{roomId}/confirmBet")
    public void confirmBet(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.handleConfirmBet(roomId, principal.getName());
        }
    }

    @MessageMapping("/room/{roomId}/fold")
    public void fold(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.handleFold(roomId, principal.getName());
        }
    }

    @MessageMapping("/room/{roomId}/call")
    public void call(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.handleCall(roomId, principal.getName());
        }
    }

    @MessageMapping("/room/{roomId}/exit")
    public void exitRoom(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.leaveRoomPermanent(roomId, principal.getName());
        }
    }

    @MessageMapping("/room/{roomId}/win")
    public void win(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.handleWin(roomId, principal.getName());
        }
    }

    @MessageMapping("/room/{roomId}/transfer")
    public void transfer(@DestinationVariable String roomId, TransferRequest req, Principal principal) {
        if (principal != null) {
            pokerService.handleTransfer(roomId, principal.getName(), req);
        }
    }

    @MessageMapping("/room/{roomId}/borrow")
    public void borrow(@DestinationVariable String roomId, BorrowRequest req, Principal principal) {
        if (principal != null) {
            pokerService.handleBorrow(roomId, principal.getName(), req);
        }
    }

    @MessageMapping("/room/{roomId}/undoBet")
    public void undoBet(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.handleUndoBet(roomId, principal.getName());
        }
    }

    @MessageMapping("/room/{roomId}/undoConfirm")
    public void undoConfirm(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.handleUndoConfirm(roomId, principal.getName());
        }
    }
    @MessageMapping("/room/{roomId}/dissolve")
    public void dissolve(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            pokerService.dissolveRoom(roomId, principal.getName());
        }
    }
}
