package com.poker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.dto.ActionRequest;
import com.poker.entity.*;
import com.poker.enums.ActionType;
import com.poker.mapper.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BettingService {

    private static final Logger log = LoggerFactory.getLogger(BettingService.class);

    private final RoomPlayerMapper roomPlayerMapper;
    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final ActionLogMapper actionLogMapper;
    private final UserService userService;
    private final RoomQueryService roomQueryService;

    public void startNewGame(String roomId) {
        Game game = new Game();
        game.setRoomId(roomId);
        game.setPhase("PRE_FLOP");
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

    public void addPlayerToGame(Long gameId, Long roomPlayerId) {
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

        int newTotal = gp.getPendingBet() + amount;
        int phaseCap = getPhaseCap(game);
        if (phaseCap > 0 && (gp.getCurrentRoundBet() + newTotal) > phaseCap) {
            log.warn("⚠️ 超过当前阶段上限: 当前{} + 新{} = {} > 上限{}",
                    gp.getCurrentRoundBet(), newTotal, gp.getCurrentRoundBet() + newTotal, phaseCap);
            return;
        }

        gp.setPendingBet(newTotal);
        gamePlayerMapper.updateById(gp);
        roomQueryService.broadcastRoom(roomId);
    }

    public void handleSetPending(String roomId, String username, int amount) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded() || gp.getIsBetConfirmed()) return;

        if (amount < 0) amount = 0;

        int phaseCap = getPhaseCap(game);
        if (phaseCap > 0 && (gp.getCurrentRoundBet() + amount) > phaseCap) {
            log.warn("⚠️ 超过当前阶段上限: {} > {}", gp.getCurrentRoundBet() + amount, phaseCap);
            return;
        }

        gp.setPendingBet(amount);
        gamePlayerMapper.updateById(gp);
        roomQueryService.broadcastRoom(roomId);
    }

    @Transactional
    public void handleConfirmBet(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) {
            log.warn("🚫 确认下注失败：游戏不存在或已结束");
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

        rp.setBalance(rp.getBalance() - amount);
        roomPlayerMapper.updateById(rp);

        int newRoundBet = gp.getCurrentRoundBet() + amount;
        gp.setCurrentRoundBet(newRoundBet);
        gp.setTotalBet(gp.getTotalBet() + amount);
        gp.setPendingBet(0);
        gp.setIsBetConfirmed(true);
        gamePlayerMapper.updateById(gp);

        game.setPot(game.getPot() + amount);

        if (newRoundBet > game.getCurrentHighestBet()) {
            int oldHighest = game.getCurrentHighestBet();
            game.setCurrentHighestBet(newRoundBet);
            gameMapper.updateById(game);

            log.info("✅ 玩家 {} 加注: {} -> {} (最高从 {} -> {})",
                    user.getUsername(), amount, newRoundBet, oldHighest, newRoundBet);

            List<GamePlayer> allGps = gamePlayerMapper.selectList(new LambdaQueryWrapper<GamePlayer>()
                    .eq(GamePlayer::getGameId, game.getId()));
            for (GamePlayer other : allGps) {
                if (other.getId().equals(gp.getId())) continue;
                if (!other.getIsFolded() && other.getIsBetConfirmed() && other.getCurrentRoundBet() < newRoundBet) {
                    other.setIsBetConfirmed(false);
                    gamePlayerMapper.updateById(other);
                    log.info("✅ 重置玩家(gamePlayerId={})的下注确认状态，等待回应加注", other.getId());
                }
            }
            logAction(game.getId(), user.getId(), ActionType.RAISE, amount, game.getPhase());
        } else {
            gameMapper.updateById(game);
            log.info("✅ 玩家 {} 跟注 {}，本轮下注 {}", user.getUsername(), amount, newRoundBet);
            logAction(game.getId(), user.getId(), ActionType.CONFIRM_BET, amount, game.getPhase());
        }

        checkAndAdvanceRound(game);
        roomQueryService.broadcastRoom(roomId);

        if (game.getIsFinished()) {
            startNewGame(roomId);
            roomQueryService.broadcastRoom(roomId);
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

        int phaseCap = getPhaseCap(game);
        if (phaseCap > 0 && highestBet > phaseCap) {
            log.warn("⚠️ 超过当前阶段上限: {} > {}", highestBet, phaseCap);
            return;
        }

        if (rp.getBalance() < callAmount) {
            log.warn("⚠️ 余额不足无法跟注：需要 {}，余额 {}", callAmount, rp.getBalance());
            throw new RuntimeException("余额不足，无法跟注");
        }

        rp.setBalance(rp.getBalance() - callAmount);
        roomPlayerMapper.updateById(rp);

        gp.setCurrentRoundBet(highestBet);
        gp.setTotalBet(gp.getTotalBet() + callAmount);
        gp.setPendingBet(0);
        gp.setIsBetConfirmed(true);
        gamePlayerMapper.updateById(gp);

        game.setPot(game.getPot() + callAmount);
        gameMapper.updateById(game);

        log.info("✅ 玩家 {} 一键跟注 {}，本轮总计 {}", user.getUsername(), callAmount, gp.getCurrentRoundBet());
        logAction(game.getId(), user.getId(), ActionType.CALL, callAmount, game.getPhase());

        checkAndAdvanceRound(game);
        roomQueryService.broadcastRoom(roomId);

        if (game.getIsFinished()) {
            startNewGame(roomId);
            roomQueryService.broadcastRoom(roomId);
        }
    }

    @Transactional
    public void handleCheck(String roomId, String username) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded() || gp.getIsBetConfirmed()) return;

        // CHECK only allowed when no bet has been made this round
        if (game.getCurrentHighestBet() > 0) {
            log.warn("⚠️ 无法check，本轮已有下注: 最高{}", game.getCurrentHighestBet());
            return;
        }

        gp.setIsBetConfirmed(true);
        gp.setPendingBet(0);
        gamePlayerMapper.updateById(gp);

        log.info("✅ 玩家 {} Check", user.getUsername());
        logAction(game.getId(), user.getId(), ActionType.CHECK, 0, game.getPhase());

        checkAndAdvanceRound(game);
        roomQueryService.broadcastRoom(roomId);

        if (game.getIsFinished()) {
            startNewGame(roomId);
            roomQueryService.broadcastRoom(roomId);
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
        logAction(game.getId(), user.getId(), ActionType.FOLD, 0, game.getPhase());

        List<GamePlayer> gps = gamePlayerMapper.selectList(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getGameId, game.getId()));
        long activeCount = gps.stream().filter(g -> !g.getIsFolded()).count();

        if (activeCount <= 1) {
            // 只剩一人或无人，自动结束本局
            if (game.getPot() > 0) {
                gps.stream().filter(g -> !g.getIsFolded()).findFirst().ifPresent(last -> {
                    RoomPlayer winner = roomPlayerMapper.selectById(last.getRoomPlayerId());
                    if (winner != null) {
                        int pot = game.getPot();
                        winner.setBalance(winner.getBalance() + pot);
                        roomPlayerMapper.updateById(winner);
                        log.info("✅ 玩家 {} (唯一未弃牌) 赢得底池 {}", winner.getUserId(), pot);
                        game.setPot(0);
                        logAction(game.getId(), winner.getUserId(), ActionType.WIN, pot, game.getPhase());
                    }
                });
            }
            game.setIsFinished(true);
            gameMapper.updateById(game);
        } else {
            // 多人存活，弃牌后检查是否满足阶段推进条件
            checkAndAdvanceRound(game);
        }

        roomQueryService.broadcastRoom(roomId);

        if (game.getIsFinished()) {
            startNewGame(roomId);
            roomQueryService.broadcastRoom(roomId);
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
        logAction(game.getId(), user.getId(), ActionType.WIN, pot, game.getPhase());
        roomQueryService.broadcastRoom(roomId);

        startNewGame(roomId);
        roomQueryService.broadcastRoom(roomId);
    }

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
        roomQueryService.broadcastRoom(roomId);
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

        rp.setBalance(rp.getBalance() + currentBet);
        roomPlayerMapper.updateById(rp);
        game.setPot(game.getPot() - currentBet);
        gp.setCurrentRoundBet(0);
        gp.setTotalBet(gp.getTotalBet() - currentBet);
        gp.setPendingBet(0);
        gp.setIsBetConfirmed(false);
        gamePlayerMapper.updateById(gp);

        List<GamePlayer> allGps = gamePlayerMapper.selectList(new LambdaQueryWrapper<GamePlayer>()
                .eq(GamePlayer::getGameId, game.getId()));
        int newHighest = allGps.stream()
                .filter(g -> !g.getIsFolded())
                .mapToInt(GamePlayer::getCurrentRoundBet)
                .max()
                .orElse(0);
        game.setCurrentHighestBet(newHighest);
        gameMapper.updateById(game);

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
        logAction(game.getId(), user.getId(), ActionType.UNDO_CONFIRM, currentBet, game.getPhase());
        roomQueryService.broadcastRoom(roomId);
    }

    public void handleDeduct(String roomId, String username, int amount) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        RoomPlayer rp = getRoomPlayer(roomId, user.getId());
        if (rp == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null || game.getIsFinished()) return;

        GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
        if (gp == null || gp.getIsFolded() || gp.getIsBetConfirmed()) return;

        int newPending = Math.max(0, gp.getPendingBet() - amount);
        gp.setPendingBet(newPending);
        gamePlayerMapper.updateById(gp);

        log.info("✅ 玩家 {} 减少待确认下注 {}，当前待确认: {}", user.getUsername(), amount, newPending);
        roomQueryService.broadcastRoom(roomId);
    }

    public void handleSetPhaseCaps(String roomId, String username, int preFlopCap, int flopCap, int turnCap, int riverCap) {
        User user = userService.findByUsername(username);
        if (user == null) return;

        Game game = getCurrentGame(roomId);
        if (game == null) return;

        game.setPreFlopCap(preFlopCap);
        game.setFlopCap(flopCap);
        game.setTurnCap(turnCap);
        game.setRiverCap(riverCap);
        gameMapper.updateById(game);

        log.info("✅ 玩家 {} 设置阶段上限: PRE_FLOP={}, FLOP={}, TURN={}, RIVER={}",
                user.getUsername(), preFlopCap, flopCap, turnCap, riverCap);
        roomQueryService.broadcastRoom(roomId);
    }

    // ── Game Flow ──

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

        if (nonFolded.size() == 1 && game.getPot() > 0) {
            GamePlayer winner = nonFolded.get(0);
            RoomPlayer winnerRp = roomPlayerMapper.selectById(winner.getRoomPlayerId());
            if (winnerRp != null) {
                int pot = game.getPot();
                winnerRp.setBalance(winnerRp.getBalance() + pot);
                roomPlayerMapper.updateById(winnerRp);
                log.info("✅ 唯一留存玩家(gamePlayerId={}) 赢得底池 {}", winner.getId(), pot);
                game.setPot(0);
                logAction(game.getId(), winnerRp.getUserId(), ActionType.WIN, pot, game.getPhase());
            }
            game.setIsFinished(true);
            gameMapper.updateById(game);
            return;
        }

        if (game.getCurrentHighestBet() <= 0) {
            boolean allConfirmed = nonFolded.stream().allMatch(GamePlayer::getIsBetConfirmed);
            if (allConfirmed) {
                advanceToNextPhase(game, nonFolded);
            }
            return;
        }

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

        advanceToNextPhase(game, nonFolded);
    }

    private void advanceToNextPhase(Game game, List<GamePlayer> nonFolded) {
        if ("RIVER".equals(game.getPhase())) {
            // River结束后不自动结束游戏，等待玩家手动收池(Win)
            log.info("✅ River阶段完成，等待手动收池");
            return;
        }

        String nextPhase = getNextPhase(game.getPhase());
        log.info("✅ {}阶段完成，进入{}阶段", game.getPhase(), nextPhase);

        game.setPhase(nextPhase);
        game.setCurrentHighestBet(0);
        gameMapper.updateById(game);

        for (GamePlayer gp : nonFolded) {
            gp.setCurrentRoundBet(0);
            gp.setPendingBet(0);
            gp.setIsBetConfirmed(false);
            gamePlayerMapper.updateById(gp);
        }
    }

    private String getNextPhase(String phase) {
        switch (phase) {
            case "PRE_FLOP": return "FLOP";
            case "FLOP": return "TURN";
            case "TURN": return "RIVER";
            default: return null;
        }
    }

    private int getPhaseCap(Game game) {
        if (game.getPhase() == null) return 0;
        switch (game.getPhase()) {
            case "PRE_FLOP": return game.getPreFlopCap() != null ? game.getPreFlopCap() : 0;
            case "FLOP": return game.getFlopCap() != null ? game.getFlopCap() : 0;
            case "TURN": return game.getTurnCap() != null ? game.getTurnCap() : 0;
            case "RIVER": return game.getRiverCap() != null ? game.getRiverCap() : 0;
            default: return 0;
        }
    }

    private void logAction(Long gameId, Long userId, ActionType type, Integer amount, String phase) {
        ActionLog actionLog = new ActionLog();
        actionLog.setGameId(gameId);
        actionLog.setUserId(userId);
        actionLog.setActionType(type.name());
        actionLog.setAmount(amount);
        actionLog.setRoundNumber(phaseToNumber(phase));
        actionLog.setCreateTime(LocalDateTime.now());
        actionLogMapper.insert(actionLog);
    }

    private Integer phaseToNumber(String phase) {
        if (phase == null) return 0;
        switch (phase) {
            case "PRE_FLOP": return 1;
            case "FLOP": return 2;
            case "TURN": return 3;
            case "RIVER": return 4;
            default: return 0;
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
}
