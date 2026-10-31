package dev.portalbreaker;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Hit the chosen block with a pickaxe and blocks of that type around it break.
 * Sneak while hitting to break just one block. The blocks go to your inventory.
 * The block and the area are picked in the settings screen (key K).
 *
 * Runs on the server side (the built-in server in singleplayer),
 * so on a multiplayer server it works only if the server has this mod.
 */
public final class PortalBreaker implements ModInitializer {
    /** Most blocks broken by one hit, so a huge structure cannot freeze the game. */
    private static final int MAX_BLOCKS = 4096;

    @Override
    public void onInitialize() {
        Settings.load();
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS; // client: let the server decide
            Block target = Settings.target;
            if (!serverLevel.getBlockState(pos).is(target)) return InteractionResult.PASS;
            if (player.isSpectator() || player.isCreative() || !player.mayBuild()) return InteractionResult.PASS;
            if (!player.getMainHandItem().is(ItemTags.PICKAXES)) return InteractionResult.PASS;

            int radius = Settings.radius;
            int broken;
            if (player.isShiftKeyDown()) broken = serverLevel.destroyBlock(pos, false, player) ? 1 : 0;
            else if (radius < 0) broken = breakConnected(serverLevel, pos, player, target);
            else broken = breakCube(serverLevel, pos, player, target, radius);
            give(serverLevel, player, target, broken);
            return InteractionResult.SUCCESS;
        });
    }

    private static int breakConnected(ServerLevel level, BlockPos start, Player player, Block target) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        int broken = 0;
        while (!queue.isEmpty() && broken < MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            if (!level.getBlockState(pos).is(target)) continue;
            if (level.destroyBlock(pos, false, player)) broken++;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (seen.add(next) && level.getBlockState(next).is(target)) queue.add(next);
                    }
        }
        return broken;
    }

    private static int breakCube(ServerLevel level, BlockPos center, Player player, Block target, int radius) {
        int broken = 0;
        for (int dx = -radius; dx <= radius; dx++)
            for (int dy = -radius; dy <= radius; dy++)
                for (int dz = -radius; dz <= radius; dz++) {
                    if (broken >= MAX_BLOCKS) return broken;
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (level.getBlockState(pos).is(target) && level.destroyBlock(pos, false, player)) broken++;
                }
        return broken;
    }

    /** Puts the broken blocks into the inventory; what does not fit drops at the player's feet. */
    private static void give(ServerLevel level, Player player, Block target, int count) {
        Item item = target.asItem();
        if (item == Items.AIR) return;
        while (count > 0) {
            int n = Math.min(count, item.getDefaultMaxStackSize());
            count -= n;
            ItemStack stack = new ItemStack(item, n);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) Block.popResource(level, player.blockPosition(), stack);
        }
    }
}
