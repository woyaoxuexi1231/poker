package com.poker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.dto.BorrowRequest;
import com.poker.dto.TransferRequest;
import com.poker.entity.*;
import com.poker.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChipService {

    private final RoomPlayerMapper roomPlayerMapper;
    private final TransferLogMapper transferLogMapper;
    private final BorrowLogMapper borrowLogMapper;
    private final UserService userService;
    private final RoomQueryService roomQueryService;

    @Transactional
    public void handleTransfer(String roomId, String username, TransferRequest req) {
        User fromUser = userService.findByUsername(username);
        if (fromUser == null) return;

        RoomPlayer fromRp = getRoomPlayer(roomId, fromUser.getId());
        if (fromRp == null) return;

        RoomPlayer toRp = roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, req.getToUserId()));

        if (toRp == null || fromRp.getBalance() < req.getAmount() || req.getAmount() <= 0) return;

        fromRp.setBalance(fromRp.getBalance() - req.getAmount());
        toRp.setBalance(toRp.getBalance() + req.getAmount());
        roomPlayerMapper.updateById(fromRp);
        roomPlayerMapper.updateById(toRp);

        TransferLog lg = new TransferLog();
        lg.setRoomId(roomId);
        lg.setFromUserId(fromUser.getId());
        lg.setToUserId(req.getToUserId());
        lg.setAmount(req.getAmount());
        lg.setCreateTime(LocalDateTime.now());
        transferLogMapper.insert(lg);

        roomQueryService.broadcastRoom(roomId);
    }

    public void handleBorrow(String roomId, String username, BorrowRequest req) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null || req.getAmount() <= 0) return;

        rp.setBalance(rp.getBalance() + req.getAmount());
        rp.setBorrowedTotal(rp.getBorrowedTotal() + req.getAmount());
        roomPlayerMapper.updateById(rp);

        BorrowLog lg = new BorrowLog();
        lg.setRoomId(roomId);
        lg.setUserId(user.getId());
        lg.setAmount(req.getAmount());
        lg.setCreateTime(LocalDateTime.now());
        borrowLogMapper.insert(lg);

        roomQueryService.broadcastRoom(roomId);
    }

    private RoomPlayer getRoomPlayer(String roomId, Long userId) {
        return roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId));
    }
}
