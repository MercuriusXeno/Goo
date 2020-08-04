package com.xeno.goop.tiles;

import com.xeno.goop.GoopMod;
import com.xeno.goop.library.GoopMapping;
import com.xeno.goop.network.ChangeSolidifierTargetPacket;
import com.xeno.goop.network.Networking;
import com.xeno.goop.setup.Registry;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.state.properties.BlockStateProperties;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Direction;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;

import java.text.NumberFormat;
import java.util.*;

public class SolidifierTile extends TileEntity implements ITickableTileEntity, ChangeSolidifierTargetPacket.IChangeSolidifierTargetReceiver
{
    private static final int HALF_SECOND_TICKS = 10;
    private static final int ONE_SECOND_TICKS = 20;
    // the item the solidifier currently "targets" is what it tries to make more of
    private Item target;
    private ItemStack targetStack;

    // when switching targets, a safety mechanism is designed to prevent accidental swaps
    // this is the stack the machine will transition to if the player confirms their input.
    private Item newTarget;
    private ItemStack newTargetStack;

    // timer that counts down after a change of target request. Failing to confirm the change reverts the selection.
    private int changeTargetTimer;

    // default timer span of 5 seconds should be plenty of time to swap an input?
    private static final int CHANGE_TARGET_TIMER_DURATION = 100;

    // the internal buffer gets filled when the machine is in the process of solidifying an item
    private Map<String, Double> fluidBuffer;
    public SolidifierTile() {
        super(Registry.SOLIDIFIER_TILE.get());
        target = Items.AIR;
        targetStack = ItemStack.EMPTY;
        newTarget = Items.AIR;
        newTargetStack = ItemStack.EMPTY;
        fluidBuffer = new HashMap<>();
        changeTargetTimer = 0;
    }

    @Override
    public void tick() {
        handleTargetChangingCountdown();
        if (world == null) {
            return;
        }
        
        if (world.isRemote()) {
            return;
        }
        
        resolveTargetChangingCountdown();

        if (hasValidTarget()) {
            handleSolidifying();
        }
    }

    private void handleTargetChangingCountdown()
    {
        if (changeTargetTimer > 0) {
            changeTargetTimer--;
        }
    }

    private void resolveTargetChangingCountdown()
    {
        if (changeTargetTimer <= 0) {
            newTarget = Items.AIR;
            newTargetStack = ItemStack.EMPTY;
            sendTargetUpdate();
        }
    }

    private boolean hasValidTarget()
    {
        return isValidTarget(target);
    }

    private void handleSolidifying()
    {
        // TODO

    }

    public Direction getHorizontalFacing()
    {
        return getBlockState().get(BlockStateProperties.HORIZONTAL_FACING);
    }

    public ItemStack getDisplayedItem()
    {
        return targetStack;
    }

    public void changeTargetItem(Item item) {
        // air is special, it means we're disabling the machine, essentially.
        // skip our returns if we're setting the target to nothing.
        if (!item.equals(Items.AIR)) {
            if (!isValidTarget(item)) {
                return;
            }
        }
        if (isEmptyTarget() || isChangingTargetValid(item)) {
            changeTarget(item);
        } else if (!item.equals(target)) {
            enterTargetSwapMode(item);
        }
    }

    private boolean isValidTarget(Item item)
    {
        if (!GoopMod.mappingHandler.has(item)) {
            return false;
        }
        GoopMapping mapping = GoopMod.mappingHandler.get(item);
        if (mapping.isUnusable()) {
            return false;
        }
        return true;
    }

    private void enterTargetSwapMode(Item item)
    {
        changeTargetTimer = CHANGE_TARGET_TIMER_DURATION;
        newTarget = item;
        newTargetStack = item.getDefaultInstance();

        sendTargetUpdate();
    }

    private void sendTargetUpdate()
    {
        if (world == null || world.isRemote()) {
            return;
        }
        Networking.sendToClientsAround(new ChangeSolidifierTargetPacket(world.dimension.getType(), pos, targetStack, newTargetStack, changeTargetTimer), world.getServer().getWorld(world.dimension.getType()), pos);
    }

    private void changeTarget(Item item)
    {
        changeTargetTimer = 0;
        target = item;
        targetStack = item.getDefaultInstance();
        newTarget = Items.AIR;
        newTargetStack = ItemStack.EMPTY;

        sendTargetUpdate();
    }

    private boolean isChangingTargetValid(Item item)
    {
        return changeTargetTimer > 0 && item.equals(newTarget);
    }

    private boolean isEmptyTarget()
    {
        return target.equals(Items.AIR);
    }

    @Override
    public CompoundNBT write(CompoundNBT tag)
    {
        tag.put("goop", serializeGoop());
        tag.put("items", serializeItems());
        return super.write(tag);
    }

    @Override
    public void read(CompoundNBT tag)
    {
        super.read(tag);
        deserializeGoop(tag);
        deserializeItems(tag);
    }

    @Override
    public CompoundNBT getUpdateTag()
    {
        return this.write(new CompoundNBT());
    }

    private CompoundNBT serializeGoop()  {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("count", fluidBuffer.size());
        int index = 0;
        for(Map.Entry<String, Double> e : fluidBuffer.entrySet()) {
            CompoundNBT goopTag = new CompoundNBT();
            goopTag.putString("key", e.getKey());
            goopTag.putDouble("value", e.getValue());
            tag.put("goop" + index, goopTag);
            index++;
        }
        return tag;
    }

    private void deserializeGoop(CompoundNBT tag) {
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT goopTag = tag.getCompound("goop" + i);
            String key = goopTag.getString("key");
            double value = goopTag.getDouble("value");
            fluidBuffer.put(key, value);
        }
    }

    private static Map<String, Double> deserializeGoopForDisplay(CompoundNBT tag)
    {
        Map<String, Double> unsorted = new HashMap<>();
        int size = tag.getInt("count");
        for(int i = 0; i < size; i++) {
            CompoundNBT goopTag = tag.getCompound("goop" + i);
            String key = goopTag.getString("key");
            double value = goopTag.getDouble("value");
            unsorted.put(key, value);
        }

        Map<String, Double> sorted = new LinkedHashMap<>();
        unsorted.entrySet().stream().sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(x -> sorted.put(x.getKey(), x.getValue()));

        return sorted;
    }

    private CompoundNBT serializeItems()
    {
        CompoundNBT itemTag = new CompoundNBT();
        NonNullList<ItemStack> targetStackList = NonNullList.withSize(2, ItemStack.EMPTY);
        targetStackList.set(0, targetStack);
        targetStackList.set(1, newTargetStack);
        ItemStackHelper.saveAllItems(itemTag, targetStackList);
        itemTag.putInt("change_target_timer", this.changeTargetTimer);
        return itemTag;
    }

    private void deserializeItems(CompoundNBT tag)
    {
        CompoundNBT itemTag = tag.getCompound("items");
        NonNullList<ItemStack> targetStackList = NonNullList.withSize(2, ItemStack.EMPTY);
        ItemStackHelper.loadAllItems(itemTag, targetStackList);
        this.targetStack = targetStackList.get(0);
        this.target = this.targetStack.getItem();
        this.newTargetStack = targetStackList.get(1);
        this.newTarget = this.newTargetStack.getItem();
        this.changeTargetTimer = itemTag.getInt("change_target_timer");
    }

    public static void addInformation(ItemStack stack, List<ITextComponent> tooltip)
    {
        CompoundNBT stackTag = stack.getTag();
        if (stackTag == null) {
            return;
        }

        if (!stackTag.contains("BlockEntityTag")) {
            return;
        }

        CompoundNBT bulbTag = stackTag.getCompound("BlockEntityTag");


        if (bulbTag.contains("goop")) {
            CompoundNBT goopTag = bulbTag.getCompound("goop");
            Map<String, Double> sortedValues = deserializeGoopForDisplay(goopTag);
            int index = 0;
            int displayIndex = 0;
            ITextComponent fluidAmount = null;

            if (sortedValues.entrySet().stream().anyMatch((kv) -> kv.getValue() > 0)) {
                tooltip.add(new TranslationTextComponent("tooltip.goop.goo_in_buffer"));
            }

            for(Map.Entry<String, Double> v : sortedValues.entrySet()) {
                index++;
                if (v.getValue() == 0D) {
                    continue;
                }
                String decimalValue = " " + NumberFormat.getNumberInstance(Locale.ROOT).format(v.getValue()) + " mB";
                String key = v.getKey();
                String fluidTranslationKey = Registry.getFluidTranslationKey(key);
                if (fluidTranslationKey == null) {
                    continue;
                }
                displayIndex++;
                if (displayIndex % 2 == 1) {
                    fluidAmount = new TranslationTextComponent(fluidTranslationKey).appendText(decimalValue);
                } else {
                    if (fluidAmount != null) {
                        fluidAmount = fluidAmount.appendText(", ").appendSibling(new TranslationTextComponent(fluidTranslationKey).appendText(decimalValue));
                    }
                }
                if (displayIndex % 2 == 0 || index == sortedValues.size()) {
                    tooltip.add(fluidAmount);
                }
            }
        }

        if (bulbTag.contains("items")) {
            CompoundNBT goopTag = bulbTag.getCompound("items");
            NonNullList<ItemStack> targetStacks = NonNullList.withSize(2, ItemStack.EMPTY);
            ItemStackHelper.loadAllItems(goopTag, targetStacks);
            ItemStack tagTargetStack = targetStacks.get(0);

            if (!tagTargetStack.isEmpty()) {
                tooltip.add(new TranslationTextComponent("tooltip.goop.solidifying_target_preface").appendSibling(new TranslationTextComponent(tagTargetStack.getTranslationKey())));
            }
        }
    }

    public ItemStack getSolidifierStack()
    {
        ItemStack stack = new ItemStack(Registry.SOLIDIFIER.get());

        CompoundNBT solidifierTag = new CompoundNBT();
        write(solidifierTag);
        solidifierTag.remove("x");
        solidifierTag.remove("y");
        solidifierTag.remove("z");

        CompoundNBT stackTag = new CompoundNBT();
        stackTag.put("BlockEntityTag", solidifierTag);
        stack.setTag(stackTag);

        return stack;
    }

    @Override
    public void updateSolidifierTarget(ItemStack target, ItemStack newTarget, int changeTargetTimer)
    {
        this.target = target.getItem();
        this.targetStack = target;
        this.newTarget = newTarget.getItem();
        this.newTargetStack = newTarget;
        this.changeTargetTimer = changeTargetTimer;
    }

    public boolean shouldFlashTargetItem()
    {
        // we may as well send the renderer a signal that it shouldn't render the item targeted, because there's nothing
        if (targetStack.isEmpty()) {
            return true;
        }

        if (newTargetStack.isEmpty()) {
            return false;
        }

        if (world == null) {
            return false;
        }

        // half second intervals
        return changeTargetTimer > 0 && changeTargetTimer % ONE_SECOND_TICKS > HALF_SECOND_TICKS;
    }
}
