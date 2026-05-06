package com.poker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.dto.ActionRequest;
import com.poker.dto.BorrowRequest;
import com.poker.dto.GameDTO;
import com.poker.dto.PlayerDTO;
import com.poker.dto.RoomDTO;
import com.poker.dto.TransferRequest;
import com.poker.entity.*;
import com.poker.enums.ActionType;
import com.poker.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PokerService {
    private final RoomMapper roomMapper;
    private final RoomPlayerMapper roomPlayerMapper;
    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final ActionLogMapper actionLogMapper;
    private final TransferLogMapper transferLogMapper;
    private final BorrowLogMapper borrowLogMapper;
    private final UserService userService;
    private final WebSocketService webSocketService;

    private String generateRoomId() {
        Random random = new Random();
        String roomId;
        do {
            roomId = String.format("%06d", random.nextInt(1000000));
        } while (roomMapper.selectById(roomId) != null);
        return roomId;
    }

    @Transactional
    public String createRoom(Long userId) {
        String roomId = generateRoomId();
        Room room = new Room();
        room.setRoomId(roomId);
        room.setCreatedBy(userId);
        room.setCreatedTime(LocalDateTime.now());
        roomMapper.insert(room);

        // Auto-join the creator
        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(roomId);
        rp.setUserId(userId);
        rp.setBalance(200);
        rp.setBorrowedTotal(0);
        rp.setIsActive(true);
        rp.setJoinedTime(LocalDateTime.now());
        roomPlayerMapper.insert(rp);

        // Auto-start game
        startNewGame(roomId);

        return roomId;
    }

    @Transactional
    public void joinRoom(String roomId, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) throw new RuntimeException("房间不存在");

        // Check if already in room
        RoomPlayer existing = roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId));
        if (existing != null) {
            if (!existing.getIsActive()) {
                existing.setIsActive(true);
                roomPlayerMapper.updateById(existing);
                Game game = getCurrentGame(roomId);
                if (game != null && !game.getIsFinished()) {
                    addPlayerToGame(game.getId(), existing.getId());
                }
            }
            broadcastRoom(roomId);
            return;
        }

        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(roomId);
        rp.setUserId(userId);
        rp.setBalance(200);
        rp.setBorrowedTotal(0);
        rp.setIsActive(true);
        rp.setJoinedTime(LocalDateTime.now());
        roomPlayerMapper.insert(rp);

        // Add to current game
        Game game = getCurrentGame(roomId);
        if (game != null && !game.getIsFinished()) {
            addPlayerToGame(game.getId(), rp.getId());
        }

        broadcastRoom(roomId);
    }

    @Transactional
    public void startNewGame(String roomId) {
        Game game = new Game();
        game.setRoomId(roomId);
        game.setRoundNumber(1);
        game.setPot(0);
        game.setIsFinished(false);
        game.setCreatedTime(LocalDateTime.now());
        gameMapper.insert(game);

        List<RoomPlayer> activePlayers = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getIsActive, true));

        for (RoomPlayer rp : activePlayers) {
            addPlayerToGame(game.getId(), rp.getId());
        }
    }

    private void addPlayerToGame(Long gameId, Long roomPlayerId) {
        GamePlayer gp = new GamePlayer();
        gp.setGameId(gameId);
        gp.setRoomPlayerId(roomPlayerId);
        gp.setPendingBet(0);
        gp.setCurrentRoundBet(0);
        gp.setTotalBet(0);
        gp.setIsFolded(false);
        gp.setIsBetConfirmed(false);
        gamePlayerMapper.insert(gp);
    }

    @Transactional
    public void handleBet(String roomId, String username, ActionRequest req) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded() || gp.getIsBetConfirmed()) return;

        int amount = req.getAmount();
        if (amount <= 0) return;

        gp.setPendingBet(gp.getPendingBet() + amount);
        gamePlayerMapper.updateById(gp);

        logAction(game.getId(), user.getId(), ActionType.BET, amount);
        broadcastRoom(roomId);
    }

    @Transactional
    public void handleConfirmBet(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded() || gp.getIsBetConfirmed()) return;

        int amount = gp.getPendingBet();
        if (amount <= 0) return;

        if (rp.getBalance() < amount) throw new RuntimeException("余额不足");

        // Deduct from balance
        rp.setBalance(rp.getBalance() - amount);
        roomPlayerMapper.updateById(rp);

        // Move pending to confirmed
        gp.setCurrentRoundBet(gp.getCurrentRoundBet() + amount);
        gp.setTotalBet(gp.getTotalBet() + amount);
        gp.setPendingBet(0);
        gp.setIsBetConfirmed(true);
        gamePlayerMapper.updateById(gp);

        // Add to pot
        game.setPot(game.getPot() + amount);
        gameMapper.updateById(game);

        logAction(game.getId(), user.getId(), ActionType.CONFIRM_BET, amount);

        // Check if should advance round
        checkAndAdvanceRound(game);

        broadcastRoom(roomId);
    }

    private void checkAndAdvanceRound(Game game) {
        List<GamePlayer> gps = gamePlayerMapper.selectList(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getGameId, game.getId()));

        List<GamePlayer> nonFolded = gps.stream()
                .filter(gp -> !gp.getIsFolded())
                .collect(Collectors.toList());

        if (nonFolded.isEmpty()) return;

        // Check all non-folded have confirmed
        boolean allConfirmed = nonFolded.stream().allMatch(GamePlayer::getIsBetConfirmed);
        if (!allConfirmed) return;

        // Check all have same current round bet
        int firstBet = nonFolded.get(0).getCurrentRoundBet();
        boolean sameBet = nonFolded.stream().allMatch(gp -> gp.getCurrentRoundBet() == firstBet);
        if (!sameBet) return;

        // Advance to next round
        game.setRoundNumber(game.getRoundNumber() + 1);
        gameMapper.updateById(game);

        for (GamePlayer gp : nonFolded) {
            gp.setCurrentRoundBet(0);
            gp.setPendingBet(0);
            gp.setIsBetConfirmed(false);
            gamePlayerMapper.updateById(gp);
        }
    }

    @Transactional
    public void handleFold(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded()) return;

        gp.setIsFolded(true);
        gp.setIsBetConfirmed(true);
        gamePlayerMapper.updateById(gp);

        logAction(game.getId(), user.getId(), ActionType.FOLD, 0);

        // Check if only one non-folded player left (auto-win)
        List<GamePlayer> gps = gamePlayerMapper.selectList(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getGameId, game.getId()));
        long activeCount = gps.stream().filter(g -> !g.getIsFolded()).count();

        if (activeCount <= 1 && game.getPot() > 0) {
            gps.stream().filter(g -> !g.getIsFolded()).findFirst().ifPresent(last -> {
                RoomPlayer winner = roomPlayerMapper.selectById(last.getRoomPlayerId());
                if (winner != null) {
                    int pot = game.getPot();
                    winner.setBalance(winner.getBalance() + pot);
                    roomPlayerMapper.updateById(winner);
                    game.setIsFinished(true);
                    game.setPot(0);
                    gameMapper.updateById(game);
                    logAction(game.getId(), winner.getUserId(), ActionType.WIN, pot);
                }
            });
        }

        broadcastRoom(roomId);

        if (game.getIsFinished()) {
            startNewGame(roomId);
        }
    }

    @Transactional
    public void handleWin(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        if (game.getPot() <= 0) return;

        int pot = game.getPot();
        rp.setBalance(rp.getBalance() + pot);
        roomPlayerMapper.updateById(rp);

        game.setIsFinished(true);
        game.setPot(0);
        gameMapper.updateById(game);

        logAction(game.getId(), user.getId(), ActionType.WIN, pot);
        broadcastRoom(roomId);

        // Auto start new game
        startNewGame(roomId);
    }

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

        TransferLog log = new TransferLog();
        log.setRoomId(roomId);
        log.setFromUserId(fromUser.getId());
        log.setToUserId(req.getToUserId());
        log.setAmount(req.getAmount());
        log.setCreateTime(LocalDateTime.now());
        transferLogMapper.insert(log);

        broadcastRoom(roomId);
    }

    @Transactional
    public void handleBorrow(String roomId, String username, BorrowRequest req) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null || req.getAmount() <= 0) return;

        rp.setBalance(rp.getBalance() + req.getAmount());
        rp.setBorrowedTotal(rp.getBorrowedTotal() + req.getAmount());
        roomPlayerMapper.updateById(rp);

        BorrowLog log = new BorrowLog();
        log.setRoomId(roomId);
        log.setUserId(user.getId());
        log.setAmount(req.getAmount());
        log.setCreateTime(LocalDateTime.now());
        borrowLogMapper.insert(log);

        broadcastRoom(roomId);
    }

    public List<RoomDTO> getMyRooms(Long userId) {
        List<RoomPlayer> myRps = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getUserId, userId));

        return myRps.stream()
                .map(rp -> buildRoomDTO(rp.getRoomId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<RoomDTO> getAvailableRooms(Long userId) {
        List<String> myRoomIds = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                        .eq(RoomPlayer::getUserId, userId))
                .stream().map(RoomPlayer::getRoomId).collect(Collectors.toList());

        return roomMapper.selectList(null).stream()
                .map(room -> buildRoomDTO(room.getRoomId()))
                .filter(Objects::nonNull)
                .filter(r -> !myRoomIds.contains(r.getRoomId()))
                .collect(Collectors.toList());
    }

    public RoomDTO getRoomData(String roomId) {
        return buildRoomDTO(roomId);
    }

    private RoomDTO buildRoomDTO(String roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) return null;

        RoomDTO dto = new RoomDTO();
        dto.setRoomId(room.getRoomId());
        dto.setCreatedBy(room.getCreatedBy());

        User creator = userService.findById(room.getCreatedBy());
        dto.setCreatedByNickname(creator != null ? creator.getNickname() : "");

        List<RoomPlayer> roomPlayers = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId));
        dto.setPlayerCount((int) roomPlayers.stream().filter(RoomPlayer::getIsActive).count());

        Game game = getCurrentGame(roomId);
        if (game != null) {
            GameDTO gameDTO = new GameDTO();
            gameDTO.setId(game.getId());
            gameDTO.setRoundNumber(game.getRoundNumber());
            gameDTO.setPot(game.getPot());
            gameDTO.setIsFinished(game.getIsFinished());
            dto.setGame(gameDTO);
        }

        List<PlayerDTO> playerDTOs = new ArrayList<>();
        for (RoomPlayer rp : roomPlayers) {
            PlayerDTO pDto = new PlayerDTO();
            pDto.setId(rp.getId());
            pDto.setUserId(rp.getUserId());
            User u = userService.findById(rp.getUserId());
            pDto.setNickname(u != null ? u.getNickname() : "");
            pDto.setBalance(rp.getBalance());
            pDto.setBorrowedTotal(rp.getBorrowedTotal());
            pDto.setIsActive(rp.getIsActive());

            if (game != null) {
                GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
                if (gp != null) {
                    pDto.setPendingBet(gp.getPendingBet());
                    pDto.setCurrentRoundBet(gp.getCurrentRoundBet());
                    pDto.setTotalBet(gp.getTotalBet());
                    pDto.setIsFolded(gp.getIsFolded());
                    pDto.setIsBetConfirmed(gp.getIsBetConfirmed());
                }
            }
            playerDTOs.add(pDto);
        }
        dto.setPlayers(playerDTOs);
        return dto;
    }

    public void broadcastRoom(String roomId) {
        RoomDTO dto = getRoomData(roomId);
        if (dto != null) {
            webSocketService.broadcastRoomUpdate(roomId, dto);
        }
    }

    private RoomPlayer getRoomPlayer(String roomId, Long userId) {
        return roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId));
    }

    private Game getCurrentGame(String roomId) {
        return gameMapper.selectOne(new LambdaQueryWrapper<Game>()
                .eq(Game::getRoomId, roomId)
                .orderByDesc(Game::getId)
                .last("LIMIT 1"));
    }

    private GamePlayer getGamePlayerByRoomPlayer(Long gameId, Long roomPlayerId) {
        return gamePlayerMapper.selectOne(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getGameId, gameId)
                .eq(GamePlayer::getRoomPlayerId, roomPlayerId));
    }

    private void logAction(Long gameId, Long userId, ActionType type, Integer amount) {
        ActionLog log = new ActionLog();
        log.setGameId(gameId);
        log.setUserId(userId);
        log.setActionType(type.name());
        log.setAmount(amount);
        log.setCreateTime(LocalDateTime.now());
        actionLogMapper.insert(log);
    }
}
