package com.xeno.goop.setup;

import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;

import java.util.Comparator;
import java.util.List;

public class GoopValueMapping {
    private String itemResourceLocation;
    private List<GoopValue> goopValues;

    public GoopValueMapping(String itemResourceLocation, List<GoopValue> goopValues) {
        this.itemResourceLocation = itemResourceLocation;
        this.goopValues = goopValues;
    }

    public GoopValueMapping(Item item, List<GoopValue> goopValues) {
        this.itemResourceLocation = item.getRegistryName().toString();
        this.goopValues = goopValues;
    }

    public void setItemResourceLocation(ResourceLocation resLoc) {
        this.itemResourceLocation = resLoc.toString();
    }

    public void setItemResourceLocation(String resLocString) {
        this.itemResourceLocation = resLocString;
    }

    public void setGoopValues(List<GoopValue> goopValues) {
        this.goopValues = goopValues;
    }

    public List<GoopValue> getGoopValues() {
        return this.goopValues;
    }

    public String getItemResourceLocation() {
        return this.itemResourceLocation;
    }

    public static Comparator<GoopValueMapping> registryNameSansNamespaceComparator = new Comparator<GoopValueMapping>() {

        public int compare(GoopValueMapping s1, GoopValueMapping s2) {

            int namespaceIndex1 = s1.getItemResourceLocation().indexOf(':');
            int namespaceIndex2 = s2.getItemResourceLocation().indexOf(':');
            String registryName1 = s1.getItemResourceLocation().substring(namespaceIndex1);
            String registryName2 = s2.getItemResourceLocation().substring(namespaceIndex2);

            return registryName1.compareTo(registryName2);
    }};
}
