package com.huntcore.game;

import org.bukkit.entity.Player;

public final class PlayerVitals {

    public static final int SURVIVAL_MATCH_FOOD_LEVEL = 20;
    public static final float SURVIVAL_MATCH_SATURATION = 20.0f;

    private PlayerVitals() {
    }

    public static void applySurvivalMatchNutrition(Player player) {
        player.setFoodLevel(SURVIVAL_MATCH_FOOD_LEVEL);
        player.setSaturation(SURVIVAL_MATCH_SATURATION);
        player.setExhaustion(0.0f);
    }
}
