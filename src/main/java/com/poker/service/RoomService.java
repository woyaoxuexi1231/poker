package com.poker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.entity.*;
import com.poker.mapper.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final Logger log = LoggerFactory.getLogger(RoomService.class);

    private final RoomMapper roomMapper;
    private final RoomPlayerMapper roomPlayerMapper;
    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final UserService userService;
    private final BettingService bettingService;
    private final RoomQueryService roomQueryService;

    public String createRoom(Long userId, String password) {
        String roomId = generateRoomId();
        Room room = new Room();
        room.setRoomId(roomId);
        room.setCreatedBy(userId);
        room.setCreatedTime(LocalDateTime.now());
        room.setStatus("ACTIVE");
        room.setPassword(password); // 设置密码，可以为 null
        roomMapper.insert(room);

        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(roomId);
        rp.setUserId(userId);
        rp.setBalance(200);
        rp.setBorrowedTotal(0);
        rp.setIsActive(true);
        rp.setJoinedTime(LocalDateTime.now());
        roomPlayerMapper.insert(rp);

        bettingService.startNewGame(roomId);
        return roomId;
    }

    public boolean isPlayerActive(String roomId, Long userId) {
        RoomPlayer rp = roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId)
                .eq(RoomPlayer::getIsActive, true));
        return rp != null;
    }

    public void joinRoom(String roomId, Long userId, String password) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) throw new RuntimeException("房间不存在");
        if ("DISSOLVED".equals(room.getStatus())) throw new RuntimeException("房间已解散");

        // 检查用户是否已经在房间中
        RoomPlayer existing = roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId));
        
        // 如果用户当前是活跃成员（刷新页面等），直接返回
        if (existing != null && existing.getIsActive()) {
            roomQueryService.broadcastRoom(roomId);
            return;
        }

        // 非活跃成员或新用户，都需要验证密码
        if (room.getPassword() != null && !room.getPassword().isEmpty()) {
            if (password == null || password.isEmpty()) {
                throw new RuntimeException("该房间需要密码才能加入");
            }
            if (!room.getPassword().equals(password)) {
                throw new RuntimeException("房间密码错误");
            }
        }

        // 曾经在房间中但已退出的用户，重新激活
        if (existing != null) {
            existing.setIsActive(true);
            roomPlayerMapper.updateById(existing);
            Game game = getCurrentGame(roomId);
            if (game != null && !game.getIsFinished()) {
                bettingService.addPlayerToGame(game.getId(), existing.getId());
            }
            roomQueryService.broadcastRoom(roomId);
            return;
        }

        // 创建新的房间玩家记录
        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(roomId);
        rp.setUserId(userId);
        rp.setBalance(200);
        rp.setBorrowedTotal(0);
        rp.setIsActive(true);
        rp.setJoinedTime(LocalDateTime.now());
        roomPlayerMapper.insert(rp);

        Game game = getCurrentGame(roomId);
        if (game != null && !game.getIsFinished()) {
            bettingService.addPlayerToGame(game.getId(), rp.getId());
        }

        roomQueryService.broadcastRoom(roomId);
    }

    public void leaveRoomPermanent(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        int totalPrincipal = 200 + rp.getBorrowedTotal();
        if (rp.getBalance() != totalPrincipal) {
            throw new RuntimeException("请先确保筹码等于本金（初始200 + 借贷" + rp.getBorrowedTotal() + " = " + totalPrincipal + "）");
        }

        rp.setIsActive(false);
        roomPlayerMapper.updateById(rp);

        Game game = getCurrentGame(roomId);
        if (game != null && !game.getIsFinished()) {
            gamePlayerMapper.delete(new LambdaQueryWrapper<GamePlayer>()
                    .eq(GamePlayer::getGameId, game.getId())
                    .eq(GamePlayer::getRoomPlayerId, rp.getId()));
        }

        roomQueryService.broadcastRoom(roomId);
    }

    public void dissolveRoom(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        Room room = roomMapper.selectById(roomId);
        if (room == null) throw new RuntimeException("房间不存在");
        if (!room.getCreatedBy().equals(user.getId())) throw new RuntimeException("只有房主才能解散房间");

        room.setStatus("DISSOLVED");
        roomMapper.updateById(room);

        List<RoomPlayer> activePlayers = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getIsActive, true));
        for (RoomPlayer rp : activePlayers) {
            rp.setIsActive(false);
            roomPlayerMapper.updateById(rp);
        }

        log.info("✅ 房主 {} 解散了房间 {}", user.getUsername(), roomId);
        roomQueryService.broadcastRoom(roomId);
    }

    private String generateRoomId() {
        Random random = new Random();
        String roomId;
        do {
            roomId = String.format("%06d", random.nextInt(1000000));
        } while (roomMapper.selectById(roomId) != null);
        return roomId;
    }

    private Game getCurrentGame(String roomId) {
        return gameMapper.selectOne(new LambdaQueryWrapper<Game>()
                .eq(Game::getRoomId, roomId)
                .orderByDesc(Game::getId)
                .last("LIMIT 1"));
    }

    private RoomPlayer getRoomPlayer(String roomId, Long userId) {
        return roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId));
    }
}
