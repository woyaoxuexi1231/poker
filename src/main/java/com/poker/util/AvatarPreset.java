package com.poker.util;

import java.util.List;

public class AvatarPreset {
    public static final List<String> AVATARS = List.of(
        "🦁", "🐯", "🐸", "🦊",
        "🐰", "🐼", "🐨", "🦄",
        "🐲", "🦅", "🐺", "🐙",
        "🦋", "🐢", "🦖", "🐳"
    );

    public static String random() {
        return AVATARS.get((int) (Math.random() * AVATARS.size()));
    }
}
