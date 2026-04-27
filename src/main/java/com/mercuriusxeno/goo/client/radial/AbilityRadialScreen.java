package com.mercuriusxeno.goo.client.radial;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.network.AbilitySyncHandler.ClientAbility;
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
import java.util.Map;

/**
 * Ability radial menu: shows the abilities available for a specific goo type.
 * Reached by clicking a type wedge in the GooRadialScreen.
 * Left-click an ability to confirm. Center click returns to type radial.
 * Right-click dismisses without changing selection.
 */
public final class AbilityRadialScreen extends Screen {

    private static final int NO_SELECTION = -1;
    private static final int HALF = 2;

    private final GooType gooType;
    private final List<ClientAbility> abilities;
    private final int[] wedgeColors;
    /**
     * Goo availability snapshot, passed through for back-navigation.
     */
    private final Map<GooType, Integer> available;
    private int hoveredIndex = NO_SELECTION;

    private AbilityRadialScreen(GooType gooType, List<ClientAbility> abilities,
                                Map<GooType, Integer> available) {
        super(Component.empty());
        this.gooType = gooType;
        this.abilities = abilities;
        this.available = available;
        this.wedgeColors = new int[abilities.size()];
    }

    /**
     * Opens the ability radial for the given goo type, carrying the
     * availability snapshot for back-navigation to the type radial.
     *
     * @param type      the goo type to show abilities for
     * @param available the goo availability snapshot from the type radial
     */
    public static void open(GooType type, Map<GooType, Integer> available) {
        List<ClientAbility> abilities = AbilitySyncHandler.getAbilitiesForType(type);
        if (abilities.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new AbilityRadialScreen(type, abilities, available));
    }

    private static @Nullable ItemStack findGloveStack() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return null;
        }
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (main.getItem() instanceof GooGloveItem) {
            return main;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        if (off.getItem() instanceof GooGloveItem) {
            return off;
        }
        return null;
    }

    private static void sendSelectionToServer(GloveSelection selection) {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundCustomPayloadPacket(
                    new GloveSelectPayload(selection.gooTypeId(), selection.abilityId())));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        RadialTextures.ensureInitialized();
        int centerX = width / HALF;
        int centerY = height / HALF;
        hoveredIndex = AbilityRadialRenderer.computeHoveredIndex(
                mouseX, mouseY, centerX, centerY, abilities.size());
        AbilityRadialRenderer.computeWedgeColorsFromSync(wedgeColors, abilities, gooType, hoveredIndex);
        AbilityRadialRenderer.renderSlotsFromSync(graphics, centerX, centerY, wedgeColors,
                hoveredIndex, abilities, font);
    }

    /**
     * Left-click on ability confirms selection and closes.
     * Left-click on center returns to type radial.
     * Right-click dismisses without changing selection.
     *
     * @param event       the mouse button click event
     * @param doubleClick true if this is a double-click
     * @return true if the event was handled
     */
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int button = event.button();
        if (button == 1) {
            onClose();
            return true;
        }
        if (button == 0) {
            handleLeftClick();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Processes a left-click: confirm ability or navigate back to types.
     */
    private void handleLeftClick() {
        if (hoveredIndex >= 0 && hoveredIndex < abilities.size()) {
            selectAbility(abilities.get(hoveredIndex));
            onClose();
        } else if (hoveredIndex == NO_SELECTION) {
            GooRadialScreen.openWithSnapshot(available);
        }
    }

    private void selectAbility(ClientAbility ability) {
        ItemStack glove = findGloveStack();
        if (glove == null) {
            return;
        }
        GloveSelection selection = GloveSelection.ofAbility(gooType, ability.id());
        GooGloveItem.setSelection(glove, selection);
        sendSelectionToServer(selection);
    }
}
