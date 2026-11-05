package com.alikuxac.machinemetrics.client;

import com.alikuxac.machinemetrics.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class InspectionGate {

    private InspectionGate() {}

    public static boolean canInspect(Player player) {
        if (ClientConfig.REQUIRE_INSPECTION_ITEM != null && !ClientConfig.REQUIRE_INSPECTION_ITEM.get()) {
            return true;
        }
        if (player == null) {
            return false;
        }
        return matchesInspectionItem(player.getMainHandItem()) || matchesInspectionItem(player.getOffhandItem());
    }

    public static boolean matchesInspectionItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        List<? extends String> configured = ClientConfig.INSPECTION_ITEM_TAGS != null
                ? ClientConfig.INSPECTION_ITEM_TAGS.get()
                : null;
        if (configured == null || configured.isEmpty()) {
            return false;
        }

        for (String rawEntry : configured) {
            if (rawEntry == null) continue;
            String entry = rawEntry.trim();
            if (entry.isEmpty()) continue;

            if (entry.startsWith("#")) {
                ResourceLocation tagLoc = ResourceLocation.tryParse(entry.substring(1));
                if (tagLoc != null) {
                    TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
                    if (stack.is(tagKey)) {
                        return true;
                    }
                }
            } else {
                ResourceLocation itemLoc = ResourceLocation.tryParse(entry);
                if (itemLoc != null) {
                    Item item = BuiltInRegistries.ITEM.get(itemLoc);
                    if (item != null && item != Items.AIR && stack.is(item)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static HitResult performRaycast(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return mc != null ? mc.hitResult : null;
        }

        if (mc.player.isScoping()) {
            double dist = ClientConfig.SPYGLASS_RAYCAST_DISTANCE != null
                    ? ClientConfig.SPYGLASS_RAYCAST_DISTANCE.get()
                    : 32.0D;
            Vec3 eyePos = mc.player.getEyePosition();
            Vec3 viewVec = mc.player.getViewVector(1.0F);
            Vec3 endPos = eyePos.add(viewVec.scale(dist));
            return mc.level.clip(new ClipContext(
                    eyePos,
                    endPos,
                    ClipContext.Block.OUTLINE,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));
        }

        return mc.hitResult;
    }
}

