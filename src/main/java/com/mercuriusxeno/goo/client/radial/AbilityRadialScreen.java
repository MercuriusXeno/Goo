package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.GloveSelectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Ability radial menu: shows the abilities available for a specific goo type.
 * Opens when the player holds use (without shift) while a type is selected.
 * Selecting an ability sets the full GloveSelection (type + ability).
 */
public final class AbilityRadialScreen extends Screen {

    /** Sentinel value for no wedge hovered. */
    private static final int NO_SELECTION = -1;
    /** Divisor for centering. */
    private static final int HALF = 2;

    private final GooType gooType;
    private final List<AbilityDefinition> abilities;
    private final int[] wedgeColors;
    private int hoveredIndex = NO_SELECTION;

    private AbilityRadialScreen(GooType gooType, List<AbilityDefinition> abilities) {
        super(Component.empty());
        this.gooType = gooType;
        this.abilities = abilities;
        this.wedgeColors = new int[abilities.size()];
    }

    /**
     * Opens the ability radial for the given goo type.
     * Falls back to the type radial if the type has no abilities.
     *
     * @param type the goo type to show abilities for
     */
    public static void open(GooType type) {
        List<AbilityDefinition> abilities = AbilityRegistry.getAbilitiesForType(type);
        if (abilities.isEmpty()) { return; }
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new AbilityRadialScreen(type, abilities));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int centerX = width / HALF;
        int centerY = height / HALF;
        hoveredIndex = AbilityRadialRenderer.computeHoveredIndex(
                mouseX, mouseY, centerX, centerY, abilities.size());
        AbilityRadialRenderer.computeWedgeColors(wedgeColors, abilities, gooType, hoveredIndex);
        AbilityRadialRenderer.renderSlots(graphics, centerX, centerY, wedgeColors,
                hoveredIndex, abilities, font);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int button = event.button();
        if (button == 0 || button == 1) {
            confirmSelection();
            return true;
        }
        return super.mouseReleased(event);
    }

    private void confirmSelection() {
        if (hoveredIndex >= 0 && hoveredIndex < abilities.size()) {
            selectAbility(abilities.get(hoveredIndex));
        }
        onClose();
    }

    private void selectAbility(AbilityDefinition ability) {
        ItemStack glove = findGloveStack();
        if (glove == null) { return; }
        GloveSelection selection = GloveSelection.ofAbility(gooType, ability.id());
        GooGloveItem.setSelection(glove, selection);
        sendSelectionToServer(selection);
    }

    private static @Nullable ItemStack findGloveStack() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) { return null; }
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof GooGloveItem) { return main; }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof GooGloveItem) { return off; }
        return null;
    }

    private static void sendSelectionToServer(GloveSelection selection) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(
                    new GloveSelectPayload(selection.gooTypeId(), selection.abilityId())));
        }
    }
}
