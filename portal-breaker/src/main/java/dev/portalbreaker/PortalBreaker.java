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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Hit reinforced deepslate with a pickaxe and the whole connected frame breaks.
 * Sneak while hitting to break just one block. The blocks go to your inventory.
 *
 * The breaking runs on the server side (the built-in server in singleplayer),
 * so on a multiplayer server it works only if the server has this mod.
 */
public final class PortalBreaker implements ModInitializer {
    /** Most blocks broken by one hit, so a huge structure cannot freeze the game. */
    private static final int MAX_BLOCKS = 512;

    @Override
    public void onInitialize() {
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS; // client: let the server decide
            if (!serverLevel.getBlockState(pos).is(Blocks.REINFORCED_DEEPSLATE)) return InteractionResult.PASS;
            if (player.isSpectator() || player.isCreative() || !player.mayBuild()) return InteractionResult.PASS;
            if (!player.getMainHandItem().is(ItemTags.PICKAXES)) return InteractionResult.PASS;

            int broken = player.isShiftKeyDown()
                    ? breakOne(serverLevel, pos, player)
                    : breakFrame(serverLevel, pos, player);
            give(serverLevel, player, broken);
            return InteractionResult.SUCCESS;
        });
    }

    private static int breakOne(ServerLevel level, BlockPos pos, Player player) {
        return level.destroyBlock(pos, false, player) ? 1 : 0;
    }

    /** Breaks every reinforced deepslate block touching the hit one (also diagonally). */
    private static int breakFrame(ServerLevel level, BlockPos start, Player player) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        int broken = 0;
        while (!queue.isEmpty() && broken < MAX_BLOCKS) {
            BlockPos pos = queue.poll();
            if (!level.getBlockState(pos).is(Blocks.REINFORCED_DEEPSLATE)) continue;
            if (level.destroyBlock(pos, false, player)) broken++;
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = pos.offset(dx, dy, dz);
                        if (seen.add(next) && level.getBlockState(next).is(Blocks.REINFORCED_DEEPSLATE)) queue.add(next);
                    }
        }
        return broken;
    }

    /** Puts the broken blocks into the inventory; what does not fit drops at the player's feet. */
    private static void give(ServerLevel level, Player player, int count) {
        while (count > 0) {
            int n = Math.min(count, 64);
            count -= n;
            ItemStack stack = new ItemStack(Items.REINFORCED_DEEPSLATE, n);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) Block.popResource(level, player.blockPosition(), stack);
        }
    }
}
