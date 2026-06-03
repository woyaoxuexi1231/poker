package com.poker.util;

import java.util.Arrays;
import java.util.List;

public class AvatarPreset {
    public static final List<String> AVATARS = Arrays.asList(
        "🦁", "🐯", "🐸", "🦊",
        "🐰", "🐼", "🐨", "🦄",
        "🐲", "🦅", "🐺", "🐙",
        "🦋", "🐢", "🦖", "🐳"
    );

    public static String random() {
        return AVATARS.get((int) (Math.random() * AVATARS.size()));
    }
}
