package com.poker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.dto.ActionLogDTO;
import com.poker.dto.ActionRequest;
import com.poker.dto.BorrowRequest;
import com.poker.dto.GameDTO;
import com.poker.dto.PlayerGameDTO;
import com.poker.dto.PlayerDTO;
import com.poker.dto.RoundBetDTO;
import com.poker.dto.RoomDTO;
import com.poker.dto.TransferRequest;
import com.poker.entity.*;
import com.poker.enums.ActionType;
import com.poker.mapper.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PokerService {
    private static final Logger log = LoggerFactory.getLogger(PokerService.class);
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

        RoomPlayer rp = new RoomPlayer();
        rp.setRoomId(roomId);
        rp.setUserId(userId);
        rp.setBalance(200);
        rp.setBorrowedTotal(0);
        rp.setIsActive(true);
        rp.setJoinedTime(LocalDateTime.now());
        roomPlayerMapper.insert(rp);

        startNewGame(roomId);
        return roomId;
    }

    @Transactional
    public void joinRoom(String roomId, Long userId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) throw new RuntimeException("房间不存在");

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
        game.setCurrentHighestBet(0);
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

        broadcastRoom(roomId);
    }

    @Transactional
    public void handleConfirmBet(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) {
            log.warn("⚠️ 确认下注失败：游戏不存在或已结束");
            return;
        }

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded() || gp.getIsBetConfirmed()) return;

        int amount = gp.getPendingBet();
        if (amount <= 0) return;

        if (rp.getBalance() < amount) {
            log.warn("⚠️ 余额不足：玩家 {} 余额 {}，需要 {}", user.getUsername(), rp.getBalance(), amount);
            throw new RuntimeException("余额不足");
        }

        // Deduct from balance
        rp.setBalance(rp.getBalance() - amount);
        roomPlayerMapper.updateById(rp);

        // Move pending to confirmed
        int newRoundBet = gp.getCurrentRoundBet() + amount;
        gp.setCurrentRoundBet(newRoundBet);
        gp.setTotalBet(gp.getTotalBet() + amount);
        gp.setPendingBet(0);
        gp.setIsBetConfirmed(true);
        gamePlayerMapper.updateById(gp);

        // Add to pot
        game.setPot(game.getPot() + amount);

        if (newRoundBet > game.getCurrentHighestBet()) {
            // This is a RAISE: player bet more than current highest
            int oldHighest = game.getCurrentHighestBet();
            game.setCurrentHighestBet(newRoundBet);
            gameMapper.updateById(game);

            log.info("✅ 玩家 {} 加注: {} -> {} (最高从 {} -> {})",
                    user.getUsername(), amount, newRoundBet, oldHighest, newRoundBet);

            // Reset all OTHER non-folded players' confirmed status
            List<GamePlayer> allGps = gamePlayerMapper.selectList(new LambdaQueryWrapper<GamePlayer>()
                    .eq(GamePlayer::getGameId, game.getId()));
            for (GamePlayer other : allGps) {
                if (other.getId().equals(gp.getId())) continue;
                if (!other.getIsFolded() && other.getIsBetConfirmed()) {
                    other.setIsBetConfirmed(false);
                    gamePlayerMapper.updateById(other);
                    log.info("✅ 重置玩家(gamePlayerId={})的下注确认状态，等待回应加注", other.getId());
                }
            }

            logAction(game.getId(), user.getId(), ActionType.RAISE, amount, game.getRoundNumber());
        } else {
            // This is a call (matching existing highest or less)
            gameMapper.updateById(game);
            log.info("✅ 玩家 {} 跟注 {}，本轮下注 {}", user.getUsername(), amount, newRoundBet);
            logAction(game.getId(), user.getId(), ActionType.CONFIRM_BET, amount, game.getRoundNumber());
        }

        checkAndAdvanceRound(game);
        broadcastRoom(roomId);

        if (game.getIsFinished()) {
            startNewGame(roomId);
            broadcastRoom(roomId);
        }
    }

    @Transactional
    public void handleCall(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded() || gp.getIsBetConfirmed()) return;

        int highestBet = game.getCurrentHighestBet();
        if (highestBet <= 0) return;

        int currentBet = gp.getCurrentRoundBet() + gp.getPendingBet();
        int callAmount = highestBet - currentBet;
        if (callAmount <= 0) return;

        if (rp.getBalance() < callAmount) {
            log.warn("⚠️ 余额不足无法跟注：需要 {}，余额 {}", callAmount, rp.getBalance());
            throw new RuntimeException("余额不足，无法跟注");
        }

        // Deduct from balance
        rp.setBalance(rp.getBalance() - callAmount);
        roomPlayerMapper.updateById(rp);

        // Clear pending and update confirmed
        gp.setCurrentRoundBet(currentBet + callAmount);
        gp.setTotalBet(gp.getTotalBet() + callAmount);
        gp.setPendingBet(0);
        gp.setIsBetConfirmed(true);
        gamePlayerMapper.updateById(gp);

        // Add to pot
        game.setPot(game.getPot() + callAmount);
        gameMapper.updateById(game);

        log.info("✅ 玩家 {} 一键跟注 {}，本轮总计 {}", user.getUsername(), callAmount, gp.getCurrentRoundBet());
        logAction(game.getId(), user.getId(), ActionType.CALL, callAmount, game.getRoundNumber());

        checkAndAdvanceRound(game);
        broadcastRoom(roomId);

        if (game.getIsFinished()) {
            startNewGame(roomId);
            broadcastRoom(roomId);
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

        log.info("✅ 玩家 {} 弃牌", user.getUsername());
        logAction(game.getId(), user.getId(), ActionType.FOLD, 0, game.getRoundNumber());

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
                    log.info("✅ 玩家 {} (唯一未弃牌) 赢得底池 {}", winner.getUserId(), pot);
                    game.setPot(0);
                    logAction(game.getId(), winner.getUserId(), ActionType.WIN, pot, game.getRoundNumber());
                }
            });
            game.setIsFinished(true);
            gameMapper.updateById(game);
        }

        broadcastRoom(roomId);

        if (game.getIsFinished()) {
            startNewGame(roomId);
            broadcastRoom(roomId);
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

        log.info("✅ 玩家 {} 手动领取底池 {}", user.getUsername(), pot);
        logAction(game.getId(), user.getId(), ActionType.WIN, pot, game.getRoundNumber());
        broadcastRoom(roomId);

        startNewGame(roomId);
        broadcastRoom(roomId);
    }

    @Transactional
    public void handleUndoBet(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded() || gp.getIsBetConfirmed()) return;

        if (gp.getPendingBet() <= 0) return;

        int cleared = gp.getPendingBet();
        gp.setPendingBet(0);
        gamePlayerMapper.updateById(gp);

        log.info("✅ 玩家 {} 清空待确认下注 {}", user.getUsername(), cleared);
        broadcastRoom(roomId);
    }

    @Transactional
    public void handleUndoConfirm(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded()) return;

        int currentBet = gp.getCurrentRoundBet();
        if (currentBet <= 0 || !gp.getIsBetConfirmed()) return;

        // Revert: add back to balance, remove from pot
        rp.setBalance(rp.getBalance() + currentBet);
        roomPlayerMapper.updateById(rp);
        game.setPot(game.getPot() - currentBet);
        gp.setCurrentRoundBet(0);
        gp.setPendingBet(0);
        gp.setIsBetConfirmed(false);
        gamePlayerMapper.updateById(gp);

        // Recalculate highest bet from remaining non-folded players
        List<GamePlayer> allGps = gamePlayerMapper.selectList(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getGameId, game.getId()));
        int newHighest = allGps.stream()
                .filter(g -> !g.getIsFolded())
                .mapToInt(GamePlayer::getCurrentRoundBet)
                .max()
                .orElse(0);
        game.setCurrentHighestBet(newHighest);
        gameMapper.updateById(game);

        // If bet changed enough that other players need to respond
        if (newHighest > 0) {
            for (GamePlayer other : allGps) {
                if (other.getId().equals(gp.getId())) continue;
                if (!other.getIsFolded() && other.getIsBetConfirmed()
                        && other.getCurrentRoundBet() < newHighest) {
                    other.setIsBetConfirmed(false);
                    gamePlayerMapper.updateById(other);
                    log.info("⚠️ 重置玩家(gamePlayerId={})的确认状态，因最高下注调整", other.getId());
                }
            }
        }

        log.info("✅ 玩家 {} 撤回本轮下注 {} (当前最高: {})", user.getUsername(), currentBet, newHighest);
        logAction(game.getId(), user.getId(), ActionType.UNDO_CONFIRM, currentBet, game.getRoundNumber());
        broadcastRoom(roomId);
    }

    private void checkAndAdvanceRound(Game game) {
        List<GamePlayer> gps = gamePlayerMapper.selectList(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getGameId, game.getId()));

        List<GamePlayer> nonFolded = gps.stream()
                .filter(gp -> !gp.getIsFolded())
                .collect(Collectors.toList());

        if (nonFolded.isEmpty()) {
            log.warn("⚠️ 所有玩家已弃牌但游戏未结束");
            return;
        }

        // If only one non-folded player, they auto-win
        if (nonFolded.size() == 1 && game.getPot() > 0) {
            GamePlayer winner = nonFolded.get(0);
            RoomPlayer winnerRp = roomPlayerMapper.selectById(winner.getRoomPlayerId());
            if (winnerRp != null) {
                int pot = game.getPot();
                winnerRp.setBalance(winnerRp.getBalance() + pot);
                roomPlayerMapper.updateById(winnerRp);
                log.info("✅ 唯一留存玩家(gamePlayerId={}) 赢得底池 {}", winner.getId(), pot);
                game.setPot(0);
                logAction(game.getId(), winnerRp.getUserId(), ActionType.WIN, pot, game.getRoundNumber());
            }
            game.setIsFinished(true);
            gameMapper.updateById(game);
            return;
        }

        // No bets yet this round
        if (game.getCurrentHighestBet() <= 0) {
            log.info("ℹ️ 本轮尚未有下注，等待中...");
            return;
        }

        // Check all non-folded have confirmed and their current bets equal the highest
        boolean allConfirmed = nonFolded.stream().allMatch(GamePlayer::getIsBetConfirmed);
        boolean allMatchHighest = nonFolded.stream()
                .allMatch(gp -> gp.getCurrentRoundBet() == game.getCurrentHighestBet());

        if (!allConfirmed) {
            log.info("ℹ️ 等待所有玩家确认下注...");
            return;
        }
        if (!allMatchHighest) {
            log.info("ℹ️ 玩家下注金额未一致，等待回应加注...");
            return;
        }

        // All matched and confirmed! Advance to next round
        log.info("✅ 第 {} 轮下注完成，所有玩家下注金额一致 ({})，进入第 {} 轮",
                game.getRoundNumber(), game.getCurrentHighestBet(), game.getRoundNumber() + 1);

        game.setRoundNumber(game.getRoundNumber() + 1);
        game.setCurrentHighestBet(0);
        gameMapper.updateById(game);

        for (GamePlayer gp : nonFolded) {
            gp.setCurrentRoundBet(0);
            gp.setPendingBet(0);
            gp.setIsBetConfirmed(false);
            gamePlayerMapper.updateById(gp);
        }
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
            gameDTO.setCurrentHighestBet(game.getCurrentHighestBet());
            gameDTO.setPot(game.getPot());
            gameDTO.setIsFinished(game.getIsFinished());

            dto.setGame(gameDTO);
        }

        List<PlayerDTO> playerDTOs = new ArrayList<>();

        // Pre-compute per-round bets for all players in current game
        Map<Long, Map<Integer, Integer>> roundBetMap = new HashMap<>();
        if (game != null) {
            List<ActionLog> gameLogs = actionLogMapper.selectList(new LambdaQueryWrapper<ActionLog>()
                    .eq(ActionLog::getGameId, game.getId())
                    .in(ActionLog::getActionType, "CONFIRM_BET", "CALL", "RAISE")
                    .orderByAsc(ActionLog::getRoundNumber));
            for (ActionLog al : gameLogs) {
                roundBetMap.computeIfAbsent(al.getUserId(), k -> new HashMap<>());
                Map<Integer, Integer> rounds = roundBetMap.get(al.getUserId());
                int rn = al.getRoundNumber();
                rounds.put(rn, rounds.getOrDefault(rn, 0) + al.getAmount());
            }
        }

        for (RoomPlayer rp : roomPlayers) {
            PlayerDTO pDto = new PlayerDTO();
            pDto.setId(rp.getId());
            pDto.setUserId(rp.getUserId());
            User u = userService.findById(rp.getUserId());
            pDto.setNickname(u != null ? u.getNickname() : "");
            pDto.setAvatar(u != null ? u.getAvatar() : "🦁");
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

                    // Per-round bets
                    Map<Integer, Integer> userRounds = roundBetMap.get(rp.getUserId());
                    if (userRounds != null) {
                        List<RoundBetDTO> rbs = userRounds.entrySet().stream()
                                .map(e -> { RoundBetDTO rb = new RoundBetDTO(); rb.setRoundNumber(e.getKey()); rb.setAmount(e.getValue()); return rb; })
                                .sorted(Comparator.comparing(RoundBetDTO::getRoundNumber))
                                .collect(Collectors.toList());
                        pDto.setRoundBets(rbs);
                    }
                }
            }
            playerDTOs.add(pDto);
        }
        dto.setPlayers(playerDTOs);
        return dto;
    }

    public List<ActionLogDTO> getActionHistory(String roomId, int page, int size) {
        // Get all game IDs for this room
        List<Game> games = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getRoomId, roomId)
                .orderByDesc(Game::getId));
        if (games.isEmpty()) return List.of();

        List<Long> gameIds = games.stream().map(Game::getId).collect(Collectors.toList());
        int offset = (page - 1) * size;

        List<ActionLog> logs = actionLogMapper.selectList(new LambdaQueryWrapper<ActionLog>()
                .in(ActionLog::getGameId, gameIds)
                .orderByDesc(ActionLog::getId)
                .last("LIMIT " + size + " OFFSET " + offset));

        return logs.stream().map(al -> {
            ActionLogDTO dto = new ActionLogDTO();
            dto.setUserId(al.getUserId());
            User u = userService.findById(al.getUserId());
            dto.setNickname(u != null ? u.getNickname() : "");
            dto.setActionType(al.getActionType());
            dto.setAmount(al.getAmount());
            dto.setRoundNumber(al.getRoundNumber());
            dto.setCreateTime(al.getCreateTime());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<PlayerGameDTO> getPlayerGames(String roomId, Long userId) {
        List<Game> games = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                .eq(Game::getRoomId, roomId)
                .orderByDesc(Game::getId));

        RoomPlayer rp = roomPlayerMapper.selectOne(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getUserId, userId));
        if (rp == null) return List.of();

        List<PlayerGameDTO> result = new ArrayList<>();
        for (Game game : games) {
            if (!game.getIsFinished()) continue;

            GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
            if (gp == null) continue;

            ActionLog winLog = actionLogMapper.selectOne(new LambdaQueryWrapper<ActionLog>()
                    .eq(ActionLog::getGameId, game.getId())
                    .eq(ActionLog::getUserId, userId)
                    .eq(ActionLog::getActionType, ActionType.WIN.name())
                    .last("LIMIT 1"));

            PlayerGameDTO dto = new PlayerGameDTO();
            dto.setGameId(game.getId());
            dto.setTotalBet(gp.getTotalBet());
            dto.setWonAmount(winLog != null ? winLog.getAmount() : 0);
            dto.setResult((winLog != null ? winLog.getAmount() : 0) - gp.getTotalBet());
            result.add(dto);
        }
        return result;
    }

    @Transactional
    public void leaveRoomPermanent(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        // Check chips condition
        int totalPrincipal = 200 + rp.getBorrowedTotal();
        if (rp.getBalance() != totalPrincipal) {
            throw new RuntimeException("请先确保筹码等于本金（初始200 + 借贷" + rp.getBorrowedTotal() + " = " + totalPrincipal + "）");
        }

        // Set inactive
        rp.setIsActive(false);
        roomPlayerMapper.updateById(rp);

        // Remove from current game if any
        Game game = getCurrentGame(roomId);
        if (game != null && !game.getIsFinished()) {
            gamePlayerMapper.delete(new LambdaQueryWrapper<GamePlayer>()
                    .eq(GamePlayer::getGameId, game.getId())
                    .eq(GamePlayer::getRoomPlayerId, rp.getId()));
        }

        broadcastRoom(roomId);
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

    private void logAction(Long gameId, Long userId, ActionType type, Integer amount, Integer roundNumber) {
        ActionLog log = new ActionLog();
        log.setGameId(gameId);
        log.setUserId(userId);
        log.setActionType(type.name());
        log.setAmount(amount);
        log.setRoundNumber(roundNumber);
        log.setCreateTime(LocalDateTime.now());
        actionLogMapper.insert(log);
    }
}
