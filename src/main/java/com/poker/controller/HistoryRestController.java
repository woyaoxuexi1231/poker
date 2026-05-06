package com.poker.controller;

import com.poker.dto.ActionLogDTO;
import com.poker.dto.PlayerGameDTO;
import com.poker.service.RoomQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class HistoryRestController {

    private final RoomQueryService roomQueryService;

    @GetMapping("/room/{roomId}/history")
    public List<ActionLogDTO> getHistory(@PathVariable String roomId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "30") int size) {
        return roomQueryService.getActionHistory(roomId, page, size);
    }

    @GetMapping("/room/{roomId}/player/{userId}/games")
    public List<PlayerGameDTO> getPlayerGames(@PathVariable String roomId, @PathVariable Long userId) {
        return roomQueryService.getPlayerGames(roomId, userId);
    }
}
