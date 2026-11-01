package dev.traderoller;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The reroll loop, run on the client every tick:
 * break the workstation, wait until the villager loses its job, put the workstation back,
 * wait until it takes the job again, open its trades and check them. Repeat until a wanted
 * trade shows up. We never trade, so the villager stays rerollable.
 */
public final class Roller {
    private enum State { IDLE, BREAK, WAIT_LOSE, PLACE, WAIT_JOB, OPEN, READ }

    private static State state = State.IDLE;
    private static BlockPos station;
    private static Item stationItem;
    private static int villagerId;
    private static int timer;
    private static int tries;
    private static int attempts;

    private Roller() {}

    public static boolean running() {
        return state != State.IDLE;
    }

    public static void toggle(Minecraft mc) {
        if (running()) {
            stop(mc, Component.literal("Перебор остановлен.").withStyle(ChatFormatting.YELLOW));
        } else {
            start(mc);
        }
    }

    private static void start(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        if (Settings.parsed().isEmpty()) {
            say(mc, Component.literal("Сначала выбери нужный трейд (клавиша U).").withStyle(ChatFormatting.RED));
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            say(mc, Component.literal("Посмотри на рабочее место жителя (лектерн) и нажми J.").withStyle(ChatFormatting.RED));
            return;
        }
        BlockPos pos = hit.getBlockPos();
        BlockState bs = mc.level.getBlockState(pos);
        Item item = bs.getBlock().asItem();
        if (item == Items.AIR) {
            say(mc, Component.literal("Этот блок нельзя поставить обратно.").withStyle(ChatFormatting.RED));
            return;
        }
        Villager villager = nearestVillager(mc, pos);
        if (villager == null) {
            say(mc, Component.literal("Рядом с этим блоком нет жителя.").withStyle(ChatFormatting.RED));
            return;
        }
        if (villager.getVillagerData().level() > 1) {
            say(mc, Component.literal("С этим жителем уже торговали: его трейды больше не меняются.").withStyle(ChatFormatting.RED));
            return;
        }
        station = pos;
        stationItem = item;
        villagerId = villager.getId();
        attempts = 0;
        tries = 0;
        say(mc, Component.literal("Перебор начат. Ищу: " + Settings.targets
                + (Settings.maxPrice < 64 ? " (до " + Settings.maxPrice + " изумрудов)" : "")
                + ". J = стоп.").withStyle(ChatFormatting.GREEN));
        // If the villager already has this job, look at its current trades first.
        go(hasJob(villager) ? State.OPEN : State.BREAK);
    }

    private static void go(State next) {
        state = next;
        timer = 0;
        if (next == State.BREAK || next == State.WAIT_LOSE || next == State.WAIT_JOB) tries = 0;
    }

    private static void stop(Minecraft mc, Component why) {
        state = State.IDLE;
        if (mc.gameMode != null) mc.gameMode.stopDestroyBlock();
        if (why != null) say(mc, why);
    }

    public static void tick(Minecraft mc) {
        if (state == State.IDLE) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) {
            state = State.IDLE;
            return;
        }
        // Pause while another screen (menu, inventory, chat) is open.
        if (mc.screen != null && !(state == State.READ && mc.screen instanceof MerchantScreen)) return;

        if (player.position().distanceTo(Vec3.atCenterOf(station)) > 6) {
            stop(mc, Component.literal("Ты отошёл слишком далеко: перебор остановлен.").withStyle(ChatFormatting.RED));
            return;
        }
        Entity e = mc.level.getEntity(villagerId);
        if (!(e instanceof Villager villager) || !villager.isAlive()) {
            stop(mc, Component.literal("Житель пропал: перебор остановлен.").withStyle(ChatFormatting.RED));
            return;
        }
        timer++;

        switch (state) {
            case BREAK -> {
                BlockState bs = mc.level.getBlockState(station);
                if (bs.isAir()) {
                    mc.gameMode.stopDestroyBlock();
                    go(State.WAIT_LOSE);
                    return;
                }
                if (timer == 1) selectBestTool(player, bs);
                mc.gameMode.continueDestroyBlock(station, Direction.UP);
                player.swing(InteractionHand.MAIN_HAND);
                if (timer > 400) stop(mc, Component.literal("Не получается сломать блок.").withStyle(ChatFormatting.RED));
            }
            case WAIT_LOSE -> {
                if (!hasJob(villager)) {
                    go(State.PLACE);
                } else if (timer > 200) {
                    stop(mc, Component.literal("Житель не теряет профессию. С ним уже торговали, "
                            + "или рядом есть другое свободное рабочее место.").withStyle(ChatFormatting.RED));
                }
            }
            case PLACE -> {
                if (!mc.level.getBlockState(station).isAir()) {
                    // Placed (by us a few ticks ago).
                    go(State.WAIT_JOB);
                    return;
                }
                int slot = hotbarSlot(player, stationItem);
                if (slot < 0) {
                    if (timer > 60) {
                        stop(mc, Component.literal("Нет блока в хотбаре. Встань вплотную к месту, "
                                + "чтобы подобрать его, или возьми запасной.").withStyle(ChatFormatting.RED));
                    }
                    return;
                }
                if (timer % 5 != 1) return;
                if (++tries > 6) {
                    stop(mc, Component.literal("Не получается поставить блок обратно.").withStyle(ChatFormatting.RED));
                    return;
                }
                player.getInventory().setSelectedSlot(slot);
                BlockPos below = station.below();
                BlockHitResult place = new BlockHitResult(Vec3.atCenterOf(below).add(0, 0.5, 0), Direction.UP, below, false);
                mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, place);
                player.swing(InteractionHand.MAIN_HAND);
            }
            case WAIT_JOB -> {
                if (mc.level.getBlockState(station).isAir()) {
                    go(State.PLACE);
                } else if (hasJob(villager) && timer > 5) {
                    go(State.OPEN);
                } else if (timer > 600) {
                    // Sometimes it just does not pick it up; try again.
                    go(State.BREAK);
                }
            }
            case OPEN -> {
                if (timer % 20 != 1) return;
                if (++tries > 4) {
                    stop(mc, Component.literal("Не получается открыть торговлю. Подойди ближе к жителю.").withStyle(ChatFormatting.RED));
                    return;
                }
                if (player.distanceTo(villager) > 3.0f) {
                    overlay(mc, "Подойди ближе к жителю");
                    return;
                }
                mc.gameMode.interact(player, villager, InteractionHand.MAIN_HAND);
                go(State.READ);
            }
            case READ -> {
                if (mc.screen instanceof MerchantScreen screen) {
                    MerchantOffers offers = screen.getMenu().getOffers();
                    if (offers.isEmpty()) {
                        if (timer > 60) retryOpen();
                        return;
                    }
                    attempts++;
                    String found = findWanted(offers);
                    if (found != null) {
                        stop(mc, null);
                        mc.level.playLocalSound(player.getX(), player.getY(), player.getZ(),
                                SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f, false);
                        say(mc, Component.literal("Нашёл за " + attempts + " " + word(attempts) + ": " + found
                                + ". Можно торговать!").withStyle(ChatFormatting.GOLD));
                        return; // leave the trade screen open
                    }
                    overlay(mc, "Перебор: попытка " + attempts + ", пока не то");
                    player.closeContainer();
                    go(State.BREAK);
                } else if (timer > 60) {
                    retryOpen();
                }
            }
            default -> { }
        }
    }

    private static void retryOpen() {
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().screen != null) {
            Minecraft.getInstance().player.closeContainer();
        }
        go(State.OPEN);
    }

    /** Text of the first wanted offer, or null. */
    private static String findWanted(MerchantOffers offers) {
        List<Settings.Target> targets = Settings.parsed();
        for (MerchantOffer offer : offers) {
            ItemStack costA = offer.getCostA();
            int price = costA.is(Items.EMERALD) ? costA.getCount() : 0;
            if (price > Settings.maxPrice) continue;
            ItemStack result = offer.getResult();
            String priceText = price > 0 ? " за " + price + " изумр." : "";
            ItemEnchantments stored = result.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null) {
                for (Object2IntMap.Entry<Holder<Enchantment>> en : stored.entrySet()) {
                    String id = en.getKey().getRegisteredName();
                    int level = en.getIntValue();
                    for (Settings.Target t : targets) {
                        if (t.id().equals(id) && level >= t.minLevel()) {
                            return "книга " + shortId(id) + " " + level + priceText;
                        }
                    }
                }
            }
            String itemId = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            for (Settings.Target t : targets) {
                if (t.id().equals(itemId)) return shortId(itemId) + priceText;
            }
        }
        return null;
    }

    private static String shortId(String id) {
        return id.startsWith("minecraft:") ? id.substring(10) : id;
    }

    private static String word(int n) {
        int m10 = n % 10, m100 = n % 100;
        if (m10 == 1 && m100 != 11) return "попытку";
        if (m10 >= 2 && m10 <= 4 && (m100 < 12 || m100 > 14)) return "попытки";
        return "попыток";
    }

    private static boolean hasJob(Villager v) {
        Holder<VillagerProfession> p = v.getVillagerData().profession();
        return !p.is(VillagerProfession.NONE) && !p.is(VillagerProfession.NITWIT);
    }

    private static Villager nearestVillager(Minecraft mc, BlockPos pos) {
        Vec3 c = Vec3.atCenterOf(pos);
        Villager best = null;
        double bestDist = Double.MAX_VALUE;
        for (Villager v : mc.level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(8))) {
            if (v.isBaby()) continue;
            double d = v.position().distanceToSqr(c);
            if (d < bestDist) {
                bestDist = d;
                best = v;
            }
        }
        return best;
    }

    private static int hotbarSlot(LocalPlayer player, Item item) {
        int sel = player.getInventory().getSelectedSlot();
        if (player.getInventory().getItem(sel).is(item)) return sel;
        for (int i = 0; i < 9; i++) {
            if (player.getInventory().getItem(i).is(item)) return i;
        }
        return -1;
    }

    /** Use the fastest hotbar tool for this block (an axe for a lectern). */
    private static void selectBestTool(LocalPlayer player, BlockState bs) {
        int best = player.getInventory().getSelectedSlot();
        float bestSpeed = player.getInventory().getItem(best).getDestroySpeed(bs);
        for (int i = 0; i < 9; i++) {
            float s = player.getInventory().getItem(i).getDestroySpeed(bs);
            if (s > bestSpeed) {
                bestSpeed = s;
                best = i;
            }
        }
        player.getInventory().setSelectedSlot(best);
    }

    private static void say(Minecraft mc, Component text) {
        mc.gui.getChat().addMessage(Component.literal("[Trade Roller] ").withStyle(ChatFormatting.AQUA).append(text));
    }

    private static void overlay(Minecraft mc, String text) {
        mc.gui.setOverlayMessage(Component.literal(text), false);
    }
}
