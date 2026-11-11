package com.kragnoth.hallmarked.client;

import com.kragnoth.hallmarked.Hallmarked;
import com.kragnoth.hallmarked.menu.AssayAnvilMenu;
import com.kragnoth.hallmarked.network.ReforgePayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class AssayAnvilScreen extends AbstractContainerScreen<AssayAnvilMenu> {
    private static final ResourceLocation BG = Hallmarked.id("textures/gui/assay_anvil.png");

    // Hammer button: lives in the panel, sprites at (176,0) lit, (196,0) lit+hover, (216,0) dark.
    private static final int HAMMER_X = 104, HAMMER_Y = 34, HAMMER_SIZE = 20;

    public AssayAnvilScreen(AssayAnvilMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 176;
        imageHeight = 166;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        renderTooltip(g, mouseX, mouseY);
        if (overHammer(mouseX, mouseY)) {
            Component tip = switch (menu.action()) {
                case AssayAnvilMenu.ACTION_APPRAISE -> Component.translatable("gui.hallmarked.appraise");
                case AssayAnvilMenu.ACTION_REFORGE -> Component.translatable("gui.hallmarked.reforge");
                default -> Component.translatable("gui.hallmarked.reforge.unavailable");
            };
            g.renderTooltip(font, tip, mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics g, float partial, int mouseX, int mouseY) {
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        int u = !menu.canReforge() ? 216 : overHammer(mouseX, mouseY) ? 196 : 176;
        g.blit(BG, leftPos + HAMMER_X, topPos + HAMMER_Y, u, 0, HAMMER_SIZE, HAMMER_SIZE, 256, 256);
    }

    /** Light parchment text: the dark wood panel swallows vanilla's 0x404040. */
    private static final int LABEL_COLOR = 0xFFD9C7A6;

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, titleLabelX, titleLabelY, LABEL_COLOR, false);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, LABEL_COLOR, false);
    }

    private boolean overHammer(double mx, double my) {
        return mx >= leftPos + HAMMER_X && mx < leftPos + HAMMER_X + HAMMER_SIZE
                && my >= topPos + HAMMER_Y && my < topPos + HAMMER_Y + HAMMER_SIZE;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && overHammer(mx, my) && menu.canReforge()) {
            PacketDistributor.sendToServer(new ReforgePayload(menu.containerId));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
