package com.poker.service;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Component
public class RoomLockManager {

    private final ConcurrentHashMap<String, Object> roomLocks = new ConcurrentHashMap<>();

    public void executeWithLock(String roomId, Runnable action) {
        synchronized (roomLocks.computeIfAbsent(roomId, k -> new Object())) {
            action.run();
        }
    }

    public <T> T executeWithLock(String roomId, Supplier<T> action) {
        synchronized (roomLocks.computeIfAbsent(roomId, k -> new Object())) {
            return action.get();
        }
    }
}
