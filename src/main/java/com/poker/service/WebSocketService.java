package com.poker.service;

import com.poker.dto.RoomDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WebSocketService {
    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastRoomUpdate(String roomId, RoomDTO roomDTO) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "ROOM_UPDATE");
        payload.put("data", roomDTO);
        messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
    }

    public void broadcastRoomDissolved(String roomId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "ROOM_DISSOLVED");
        messagingTemplate.convertAndSend("/topic/room/" + roomId, payload);
    }
}
