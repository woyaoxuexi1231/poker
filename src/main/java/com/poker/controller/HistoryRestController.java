package com.poker.controller;

import com.poker.dto.ActionLogDTO;
import com.poker.service.PokerService;
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

    private final PokerService pokerService;

    @GetMapping("/room/{roomId}/history")
    public List<ActionLogDTO> getHistory(@PathVariable String roomId,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "30") int size) {
        return pokerService.getActionHistory(roomId, page, size);
    }
}
