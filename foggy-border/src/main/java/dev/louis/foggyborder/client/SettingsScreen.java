package dev.louis.foggyborder.client;

import dev.louis.foggyborder.client.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Replaces the YACL screen of the original mod (YACL has no 26.3 build). */
public final class SettingsScreen extends Screen {
    private static final int GOLD = 0xFFFFD36B;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;

    private Button borderBtn;
    private Button behindBtn;
    private int top;
    private final boolean behindAtOpen = FoggyBorder.config.dontRenderBehindBorder;

    public SettingsScreen() {
        super(Component.literal("Foggy Border"));
    }

    @Override
    protected void init() {
        Config c = FoggyBorder.config;
        int left = width / 2 - 120;
        top = height / 2 - 90;

        borderBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            c.disableWorldborder = !c.disableWorldborder;
            refresh();
        }).bounds(left, top + 20, 240, 20).build());

        behindBtn = addRenderableWidget(Button.builder(Component.empty(), b -> {
            c.dontRenderBehindBorder = !c.dontRenderBehindBorder;
            refresh();
        }).bounds(left, top + 44, 240, 20).build());

        // Fog end distance multiplier: 0.1 .. 2.0
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            c.fogEndDistanceMultiplier = round(Math.max(0.1f, c.fogEndDistanceMultiplier - 0.1f));
        }).bounds(left, top + 84, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            c.fogEndDistanceMultiplier = round(Math.min(2f, c.fogEndDistanceMultiplier + 0.1f));
        }).bounds(left + 200, top + 84, 40, 20).build());

        // Minimum fog end distance: 0.1 .. 20
        addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            c.minimumFogEndDistance = round(Math.max(0.1f, c.minimumFogEndDistance - step(c.minimumFogEndDistance, false)));
        }).bounds(left, top + 124, 40, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            c.minimumFogEndDistance = round(Math.min(20f, c.minimumFogEndDistance + step(c.minimumFogEndDistance, true)));
        }).bounds(left + 200, top + 124, 40, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Сбросить"), b -> {
            Config d = new Config();
            c.disableWorldborder = d.disableWorldborder;
            c.dontRenderBehindBorder = d.dontRenderBehindBorder;
            c.fogEndDistanceMultiplier = d.fogEndDistanceMultiplier;
            c.minimumFogEndDistance = d.minimumFogEndDistance;
            refresh();
        }).bounds(left, top + 154, 117, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
                .bounds(left + 123, top + 154, 117, 20).build());
        refresh();
    }

    private static float step(float v, boolean up) {
        return (up ? v >= 2f : v > 2f) ? 1f : 0.1f;
    }

    private static float round(float v) {
        return Math.round(v * 10f) / 10f;
    }

    private void refresh() {
        Config c = FoggyBorder.config;
        borderBtn.setMessage(Component.literal("Синие линии границы: " + (c.disableWorldborder ? "скрыты" : "видны")));
        behindBtn.setMessage(Component.literal("Блоки за границей: " + (c.dontRenderBehindBorder ? "скрыты" : "видны")));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        Config c = FoggyBorder.config;
        int left = width / 2 - 120;
        graphics.centeredText(font, title, width / 2, top + 4, GOLD);
        graphics.text(font, "Множитель дальности тумана:", left, top + 72, WHITE, true);
        graphics.centeredText(font, Component.literal(String.format("%.1f", c.fogEndDistanceMultiplier)), width / 2, top + 90, WHITE);
        graphics.text(font, "Мин. дальность тумана (блоков):", left, top + 112, WHITE, true);
        graphics.centeredText(font, Component.literal(String.format("%.1f", c.minimumFogEndDistance)), width / 2, top + 130, WHITE);
        graphics.centeredText(font, Component.literal("Чем ближе к границе, тем гуще туман."), width / 2, top + 180, GRAY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        FoggyBorder.config.write();
        if (FoggyBorder.config.dontRenderBehindBorder != behindAtOpen) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.levelRenderer != null) mc.levelRenderer.allChanged();
        }
        super.onClose();
    }
}
