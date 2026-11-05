package dev.traderoller;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Pick the wanted trade and the highest price. */
public final class SettingsScreen extends Screen {
    private static final int GOLD = 0xFFFFD36B;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF7CFC7C;

    private static final String[][] QUICK = {
            {"Починка", "mending"}, {"Эффект. V", "efficiency 5"}, {"Прочность III", "unbreaking 3"},
            {"Острота V", "sharpness 5"}, {"Защита IV", "protection 4"}, {"Удача III", "fortune 3"},
            {"Шёлк. касание", "silk_touch"}, {"Бесконечность", "infinity"}, {"Сила V", "power 5"},
    };

    private EditBox targetField;
    private Button minus;
    private Button plus;
    private int top;

    public SettingsScreen() {
        super(Component.literal("Trade Roller"));
    }

    @Override
    protected void init() {
        int left = width / 2 - 120;
        top = height / 2 - 105;

        targetField = new EditBox(font, left, top + 24, 240, 20, Component.literal("Трейд"));
        targetField.setMaxLength(256);
        targetField.setValue(Settings.targets);
        targetField.setResponder(t -> Settings.targets = t);
        addRenderableWidget(targetField);

        for (int i = 0; i < QUICK.length; i++) {
            String value = QUICK[i][1];
            addRenderableWidget(Button.builder(Component.literal(QUICK[i][0]), b -> add(value))
                    .bounds(left + (i % 3) * 81, top + 50 + (i / 3) * 22, 78, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Очистить"), b -> targetField.setValue(""))
                .bounds(left, top + 118, 240, 20).build());

        minus = addRenderableWidget(Button.builder(Component.literal("-"), b -> price(-1))
                .bounds(left, top + 158, 40, 20).build());
        plus = addRenderableWidget(Button.builder(Component.literal("+"), b -> price(1))
                .bounds(left + 200, top + 158, 40, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Готово"), b -> onClose())
                .bounds(left, top + 188, 240, 20).build());
        refresh();
    }

    private void add(String value) {
        String cur = targetField.getValue().trim();
        for (Settings.Target t : Settings.parsed()) {
            if (t.id().equals("minecraft:" + value.split(" ")[0])) return;
        }
        targetField.setValue(cur.isEmpty() ? value : cur + ", " + value);
    }

    private void price(int delta) {
        int cur = Settings.maxPrice;
        int step = (delta > 0 ? cur >= 16 : cur > 16) ? 4 : 1;
        Settings.maxPrice = Math.max(1, Math.min(64, cur + delta * step));
        refresh();
    }

    private void refresh() {
        minus.active = Settings.maxPrice > 1;
        plus.active = Settings.maxPrice < 64;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = width / 2 - 120;
        graphics.centeredText(font, title, width / 2, top - 4, GOLD);
        graphics.text(font, "Что ищем (через запятую, хватит любого):", left, top + 12, WHITE, true);
        graphics.text(font, "Макс. цена в изумрудах:", left, top + 146, WHITE, true);
        graphics.centeredText(font, Component.literal(Settings.maxPrice >= 64 ? "любая" : "до " + Settings.maxPrice),
                width / 2, top + 164, GREEN);
        graphics.centeredText(font, Component.literal("Смотри на лектерн рядом с жителем и жми J."), width / 2, top + 214, GRAY);
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
