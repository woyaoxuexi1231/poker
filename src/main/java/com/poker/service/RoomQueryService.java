package com.poker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.poker.dto.*;
import com.poker.entity.*;
import com.poker.enums.ActionType;
import com.poker.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomQueryService {

    private final RoomMapper roomMapper;
    private final RoomPlayerMapper roomPlayerMapper;
    private final GameMapper gameMapper;
    private final GamePlayerMapper gamePlayerMapper;
    private final ActionLogMapper actionLogMapper;
    private final UserService userService;
    private final WebSocketService webSocketService;

    public RoomDTO getRoomData(String roomId) {
        return buildRoomDTO(roomId);
    }

    public void broadcastRoom(String roomId) {
        RoomDTO dto = getRoomData(roomId);
        if (dto != null) {
            webSocketService.broadcastRoomUpdate(roomId, dto);
        }
    }

    public List<RoomDTO> getMyRooms(Long userId) {
        List<RoomPlayer> myRps = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getUserId, userId)
                .eq(RoomPlayer::getIsActive, true));
        return myRps.stream()
                .map(rp -> buildRoomDTO(rp.getRoomId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public List<RoomDTO> getAvailableRooms(Long userId) {
        // 只排除玩家当前活跃的房间，已退出的房间应该出现在可用列表中
        List<String> myRoomIds = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                        .eq(RoomPlayer::getUserId, userId)
                        .eq(RoomPlayer::getIsActive, true))
                .stream().map(RoomPlayer::getRoomId).collect(Collectors.toList());

        return roomMapper.selectList(new LambdaQueryWrapper<Room>()
                        .ne(Room::getStatus, "DISSOLVED"))
                .stream()
                .map(room -> buildRoomDTO(room.getRoomId()))
                .filter(Objects::nonNull)
                .filter(r -> !myRoomIds.contains(r.getRoomId()))
                .collect(Collectors.toList());
    }

    public List<ActionLogDTO> getActionHistory(String roomId, int page, int size) {
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

    public PlayerOverallStatsDTO getPlayerOverallStats(Long userId) {
        List<RoomPlayer> allRps = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getUserId, userId));
        if (allRps.isEmpty()) return new PlayerOverallStatsDTO();

        int totalGames = 0, totalResult = 0;
        Set<String> roomIds = new HashSet<>();

        for (RoomPlayer rp : allRps) {
            roomIds.add(rp.getRoomId());
            List<Game> games = gameMapper.selectList(new LambdaQueryWrapper<Game>()
                    .eq(Game::getRoomId, rp.getRoomId())
                    .eq(Game::getIsFinished, true));

            for (Game game : games) {
                GamePlayer gp = getGamePlayerByRoomPlayer(game.getId(), rp.getId());
                if (gp == null) continue;
                totalGames++;

                ActionLog winLog = actionLogMapper.selectOne(new LambdaQueryWrapper<ActionLog>()
                        .eq(ActionLog::getGameId, game.getId())
                        .eq(ActionLog::getUserId, userId)
                        .eq(ActionLog::getActionType, ActionType.WIN.name())
                        .last("LIMIT 1"));
                int won = winLog != null ? winLog.getAmount() : 0;
                totalResult += won - gp.getTotalBet();
            }
        }

        PlayerOverallStatsDTO dto = new PlayerOverallStatsDTO();
        dto.setTotalGames(totalGames);
        dto.setTotalResult(totalResult);
        dto.setTotalRooms(roomIds.size());
        return dto;
    }

    private RoomDTO buildRoomDTO(String roomId) {
        Room room = roomMapper.selectById(roomId);
        if (room == null) return null;

        RoomDTO dto = new RoomDTO();
        dto.setRoomId(room.getRoomId());
        dto.setCreatedBy(room.getCreatedBy());
        dto.setStatus(room.getStatus());
        dto.setHasPassword(room.getPassword() != null && !room.getPassword().isEmpty()); // 设置是否有密码

        User creator = userService.findById(room.getCreatedBy());
        dto.setCreatedByNickname(creator != null ? creator.getNickname() : "");

        List<RoomPlayer> roomPlayers = roomPlayerMapper.selectList(new LambdaQueryWrapper<RoomPlayer>()
                .eq(RoomPlayer::getRoomId, roomId)
                .eq(RoomPlayer::getIsActive, true));
        dto.setPlayerCount(roomPlayers.size());

        Game game = getCurrentGame(roomId);
        if (game != null) {
            GameDTO gameDTO = new GameDTO();
            gameDTO.setId(game.getId());
            gameDTO.setPhase(game.getPhase());
            gameDTO.setCurrentHighestBet(game.getCurrentHighestBet());
            gameDTO.setPot(game.getPot());
            gameDTO.setIsFinished(game.getIsFinished());
            gameDTO.setPhaseCap(getPhaseCap(game));
            gameDTO.setPreFlopCap(game.getPreFlopCap());
            gameDTO.setFlopCap(game.getFlopCap());
            gameDTO.setTurnCap(game.getTurnCap());
            gameDTO.setRiverCap(game.getRiverCap());
            dto.setGame(gameDTO);
        }

        List<PlayerDTO> playerDTOs = new ArrayList<>();

        Map<Long, Map<Integer, Integer>> roundBetMap = new HashMap<>();
        if (game != null) {
            List<ActionLog> gameLogs = actionLogMapper.selectList(new LambdaQueryWrapper<ActionLog>()
                    .eq(ActionLog::getGameId, game.getId())
                    .in(ActionLog::getActionType, "CONFIRM_BET", "CALL", "RAISE", "CHECK", "UNDO_CONFIRM")
                    .orderByAsc(ActionLog::getRoundNumber));
            for (ActionLog al : gameLogs) {
                roundBetMap.computeIfAbsent(al.getUserId(), k -> new HashMap<>());
                Map<Integer, Integer> rounds = roundBetMap.get(al.getUserId());
                int rn = al.getRoundNumber();
                if ("UNDO_CONFIRM".equals(al.getActionType())) {
                    rounds.put(rn, rounds.getOrDefault(rn, 0) - al.getAmount());
                } else {
                    rounds.put(rn, rounds.getOrDefault(rn, 0) + al.getAmount());
                }
            }
        }

        for (RoomPlayer rp : roomPlayers) {
            PlayerDTO pDto = new PlayerDTO();
            pDto.setId(rp.getId());
            pDto.setUserId(rp.getUserId());
            User u = userService.findById(rp.getUserId());
            pDto.setNickname(u != null ? u.getNickname() : "");
            pDto.setAvatar(u != null ? u.getAvatar() : "\uD83E\uDD81");
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

                    Map<Integer, Integer> userRounds = roundBetMap.get(rp.getUserId());
                    if (userRounds != null) {
                        List<RoundBetDTO> rbs = userRounds.entrySet().stream()
                                .map(e -> {
                                    RoundBetDTO rb = new RoundBetDTO();
                                    rb.setRoundNumber(e.getKey());
                                    rb.setAmount(e.getValue());
                                    return rb;
                                })
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
}
