package com.poker.controller;

import com.poker.dto.ActionRequest;
import com.poker.dto.BorrowRequest;
import com.poker.dto.TransferRequest;
import com.poker.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class WsRoomController {

    private final RoomService roomService;
    private final BettingService bettingService;
    private final ChipService chipService;
    private final RoomQueryService roomQueryService;
    private final RoomLockManager roomLockManager;

    @MessageMapping("/room/{roomId}/join")
    public void joinRoom(@DestinationVariable String roomId, Principal principal) {
        if (principal != null) {
            roomQueryService.broadcastRoom(roomId);
        }
    }

    @MessageMapping("/room/{roomId}/bet")
    public void bet(@DestinationVariable String roomId, ActionRequest req, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleBet(roomId, principal.getName(), req));
    }

    @MessageMapping("/room/{roomId}/confirmBet")
    public void confirmBet(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleConfirmBet(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/fold")
    public void fold(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleFold(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/call")
    public void call(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleCall(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/check")
    public void check(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleCheck(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/win")
    public void win(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleWin(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/undoBet")
    public void undoBet(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleUndoBet(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/undoConfirm")
    public void undoConfirm(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleUndoConfirm(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/exit")
    public void exitRoom(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                roomService.leaveRoomPermanent(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/dissolve")
    public void dissolve(@DestinationVariable String roomId, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                roomService.dissolveRoom(roomId, principal.getName()));
    }

    @MessageMapping("/room/{roomId}/transfer")
    public void transfer(@DestinationVariable String roomId, TransferRequest req, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                chipService.handleTransfer(roomId, principal.getName(), req));
    }

    @MessageMapping("/room/{roomId}/borrow")
    public void borrow(@DestinationVariable String roomId, BorrowRequest req, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                chipService.handleBorrow(roomId, principal.getName(), req));
    }

    @MessageMapping("/room/{roomId}/setPending")
    public void setPending(@DestinationVariable String roomId, ActionRequest req, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleSetPending(roomId, principal.getName(), req.getAmount()));
    }

    @MessageMapping("/room/{roomId}/deduct")
    public void deduct(@DestinationVariable String roomId, ActionRequest req, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleDeduct(roomId, principal.getName(), req.getAmount()));
    }

    @MessageMapping("/room/{roomId}/setPhaseCaps")
    public void setPhaseCaps(@DestinationVariable String roomId, ActionRequest req, Principal principal) {
        if (principal == null) return;
        roomLockManager.executeWithLock(roomId, () ->
                bettingService.handleSetPhaseCaps(roomId, principal.getName(),
                        req.getPreFlopCap() != null ? req.getPreFlopCap() : 0,
                        req.getFlopCap() != null ? req.getFlopCap() : 0,
                        req.getTurnCap() != null ? req.getTurnCap() : 0,
                        req.getRiverCap() != null ? req.getRiverCap() : 0));
    }
}
