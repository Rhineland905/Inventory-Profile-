package dev.portalbreaker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Pick which block breaks and how big the area is. */
public final class SettingsScreen extends Screen {
    private static final int GOLD = 0xFFFFD36B;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GREEN = 0xFF7CFC7C;
    private static final int RED = 0xFFFF6B6B;
    private static final int GRAY = 0xFFAAAAAA;

    private EditBox blockField;
    private Button modeButton;
    private Button minusButton;
    private Button plusButton;
    private boolean blockValid = true;
    private int top;

    public SettingsScreen() {
        super(Component.literal("Portal Breaker"));
    }

    @Override
    protected void init() {
        int left = width / 2 - 110;
        top = height / 2 - 95;

        blockField = new EditBox(font, left + 24, top + 24, 196, 20, Component.literal("Блок"));
        blockField.setMaxLength(128);
        blockField.setValue(Settings.id(Settings.target));
        blockField.setResponder(this::onBlockText);
        addRenderableWidget(blockField);

        addRenderableWidget(Button.builder(Component.literal("Взять блок, на который смотрю"), b -> pickLookedAt())
                .bounds(left, top + 48, 220, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Портал"), b -> setBlock(Blocks.REINFORCED_DEEPSLATE))
                .bounds(left, top + 72, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Обсидиан"), b -> setBlock(Blocks.OBSIDIAN))
                .bounds(left + 74, top + 72, 72, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Бедрок"), b -> setBlock(Blocks.BEDROCK))
                .bounds(left + 148, top + 72, 72, 20).build());

        modeButton = addRenderableWidget(Button.builder(modeLabel(), b -> {
            Settings.radius = Settings.radius < 0 ? 2 : -1;
            refresh();
        }).bounds(left, top + 116, 220, 20).build());
        minusButton = addRenderableWidget(Button.builder(Component.literal("-"), b -> changeRadius(-1))
                .bounds(left, top + 140, 40, 20).build());
        plusButton = addRenderableWidget(Button.builder(Component.literal("+"), b -> changeRadius(1))
                .bounds(left + 180, top + 140, 40, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
                .bounds(left, top + 172, 220, 20).build());
        refresh();
    }

    private void onBlockText(String text) {
        Block b = Settings.parse(text);
        blockValid = b != null;
        if (b != null) Settings.target = b;
    }

    private void setBlock(Block block) {
        Settings.target = block;
        blockField.setValue(Settings.id(block));
    }

    private void pickLookedAt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            Block b = mc.level.getBlockState(hit.getBlockPos()).getBlock();
            if (b != Blocks.AIR) setBlock(b);
        }
    }

    private void changeRadius(int delta) {
        Settings.radius = Math.max(0, Math.min(Settings.MAX_RADIUS, Settings.radius + delta));
        refresh();
    }

    private Component modeLabel() {
        return Component.literal(Settings.radius < 0 ? "Область: вся соединённая группа" : "Область: куб вокруг удара");
    }

    private void refresh() {
        modeButton.setMessage(modeLabel());
        boolean cube = Settings.radius >= 0;
        minusButton.active = cube && Settings.radius > 0;
        plusButton.active = cube && Settings.radius < Settings.MAX_RADIUS;
        minusButton.visible = cube;
        plusButton.visible = cube;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = width / 2 - 110;
        graphics.centeredText(font, title, width / 2, top - 4, GOLD);
        graphics.text(font, "Какой блок ломать:", left, top + 12, WHITE, true);
        graphics.item(new ItemStack(Settings.target.asItem()), left + 2, top + 26);
        if (!blockValid) graphics.text(font, "Нет такого блока", left + 120, top + 12, RED, true);
        graphics.text(font, "Сколько ломать:", left, top + 104, WHITE, true);
        if (Settings.radius >= 0) {
            int size = Settings.radius * 2 + 1;
            graphics.centeredText(font, Component.literal("Радиус " + Settings.radius + "  (" + size + "x" + size + "x" + size + ")"),
                    width / 2, top + 146, GREEN);
        }
        graphics.centeredText(font, Component.literal("Бей киркой. Shift + удар = один блок."), width / 2, top + 198, GRAY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Settings.save();
        super.onClose();
    }
}
