package dev.portalbreaker;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Hit the chosen block with a pickaxe and blocks of that type around it break.
 * Sneak while hitting to break just one block. The blocks go to your inventory.
 *
 * Commands:
 *   /pb                      show settings
 *   /pb block <block>        which block breaks (default reinforced_deepslate)
 *   /pb area <radius>        break every such block within a cube of this radius (0..16)
 *   /pb area connected       break the connected group of such blocks (default)
 *
 * Runs on the server side (the built-in server in singleplayer),
 * so on a multiplayer server it works only if the server has this mod.
 */
public final class PortalBreaker implements ModInitializer {
    /** Most blocks broken by one hit, so a huge structure cannot freeze the game. */
    private static final int MAX_BLOCKS = 4096;
    private static final int MAX_RADIUS = 16;
    private static final Path CONFIG = FabricLoader.getInstance().getConfigDir().resolve("portalbreaker.properties");

    private static Block target = Blocks.REINFORCED_DEEPSLATE;
    /** -1 means "connected group", otherwise the cube radius. */
    private static int radius = -1;

    @Override
    public void onInitialize() {
        load();
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS; // client: let the server decide
            if (!serverLevel.getBlockState(pos).is(target)) return InteractionResult.PASS;
            if (player.isSpectator() || player.isCreative() || !player.mayBuild()) return InteractionResult.PASS;
            if (!player.getMainHandItem().is(ItemTags.PICKAXES)) return InteractionResult.PASS;

            int broken;
            if (player.isShiftKeyDown()) broken = serverLevel.destroyBlock(pos, false, player) ? 1 : 0;
            else if (radius < 0) broken = breakConnected(serverLevel, pos, player);
            else broken = breakCube(serverLevel, pos, player);
            give(serverLevel, player, broken);
            return InteractionResult.SUCCESS;
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(Commands.literal("pb")
                .executes(ctx -> show(ctx.getSource()))
                .then(Commands.literal("block")
                    .then(Commands.argument("block", BlockStateArgument.block(registryAccess))
                        .executes(ctx -> {
                            target = BlockStateArgument.getBlock(ctx, "block").getState().getBlock();
                            save();
                            return show(ctx.getSource());
                        })))
                .then(Commands.literal("area")
                    .then(Commands.literal("connected").executes(ctx -> {
                        radius = -1;
                        save();
                        return show(ctx.getSource());
                    }))
                    .then(Commands.argument("radius", IntegerArgumentType.integer(0, MAX_RADIUS))
                        .executes(ctx -> {
                            radius = IntegerArgumentType.getInteger(ctx, "radius");
                            save();
                            return show(ctx.getSource());
                        })))));
    }

    private static int show(CommandSourceStack source) {
        String area = radius < 0
                ? "вся соединённая группа"
                : "куб радиус " + radius + " (" + (radius * 2 + 1) + "x" + (radius * 2 + 1) + "x" + (radius * 2 + 1) + ")";
        source.sendSuccess(() -> Component.literal("Portal Breaker: блок " + id(target) + ", область: " + area
                + ". Бей киркой; Shift = один блок."), false);
        return 1;
    }

    private static int breakConnected(ServerLevel level, BlockPos start, Player player) {
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

    private static int breakCube(ServerLevel level, BlockPos center, Player player) {
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
    private static void give(ServerLevel level, Player player, int count) {
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

    private static String id(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static void load() {
        if (!Files.exists(CONFIG)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(CONFIG)) {
            p.load(r);
        } catch (IOException e) {
            return;
        }
        String blockId = p.getProperty("block", "");
        for (Block b : BuiltInRegistries.BLOCK) {
            if (id(b).equals(blockId)) { target = b; break; }
        }
        try {
            radius = Math.max(-1, Math.min(MAX_RADIUS, Integer.parseInt(p.getProperty("radius", "-1").trim())));
        } catch (NumberFormatException ignored) { }
    }

    private static void save() {
        Properties p = new Properties();
        p.setProperty("block", id(target));
        p.setProperty("radius", Integer.toString(radius));
        try (Writer w = Files.newBufferedWriter(CONFIG)) {
            p.store(w, "Portal Breaker settings; radius -1 = connected group");
        } catch (IOException e) {
            System.err.println("[PortalBreaker] Could not save settings: " + e);
        }
    }
}
