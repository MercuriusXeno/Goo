package com.xeno.goop.setup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GoopValueMappingData {
    private List<GoopValueMapping> mappings;

    public GoopValueMappingData() {
        this.mappings = new ArrayList<>();
    }

    public List<GoopValueMapping> getMappings() {
        return this.mappings;
    }

    public static GoopValueMappingData deserializeFromJson(JsonArray mappings) {
        // initialize a new instance and ensure the mapping list is initialized.
        GoopValueMappingData result = new GoopValueMappingData();
        mappings.forEach((i) -> result.mappings.add(deserializeItemMapping(i)));
        return result;
    }

    private static GoopValueMapping deserializeItemMapping(JsonElement i) {
        String itemResourceLocation = i.getAsJsonObject().get("itemResourceLocation").getAsString();
        JsonArray goopValues = i.getAsJsonObject().getAsJsonArray("goopValues");
        return new GoopValueMapping(itemResourceLocation, deserializeGoopMappings(goopValues));
    }

    private static List<GoopValue> deserializeGoopMappings(JsonArray goopValueData) {
        List<GoopValue> goopValues = new ArrayList<>();
        goopValueData.forEach((d) -> goopValues.add(deserializeGoopMapping(d)));
        return goopValues;
    }

    private static GoopValue deserializeGoopMapping(JsonElement d) {
        String fluidResourceLocation = d.getAsJsonObject().get("fluidResourceLocation").getAsString();
        int fluidAmount = d.getAsJsonObject().get("amount").getAsInt();
        return new GoopValue(fluidResourceLocation, fluidAmount);
    }


    public static JsonArray serializeMappingData(GoopValueMappingData data) {
        return GoopValueMappingData.serializeItemValueMappings(data.mappings);
    }

    public static JsonArray serializeItemValueMappings(List<GoopValueMapping> data) {
        JsonArray mappingObjects = new JsonArray();
        data.forEach((d) -> mappingObjects.add(serializeItemValueMapping(d)));
        return mappingObjects;
    }

    public static JsonObject serializeItemValueMapping(GoopValueMapping data) {
        JsonObject mappingObject = new JsonObject();
        mappingObject.addProperty("itemResourceLocation", data.getItemResourceLocation().toString());
        mappingObject.add("goopValues", serializeGoopValues(data.getGoopValues()));
        return mappingObject;
    }

    public static JsonArray serializeGoopValues(List<GoopValue> data) {
        JsonArray valuesObject = new JsonArray();
        data.forEach((d) -> valuesObject.add(serializeGoopValue(d)));
        return valuesObject;
    }

    public static JsonObject serializeGoopValue(GoopValue data) {
        JsonObject mappingEntry = new JsonObject();
        mappingEntry.addProperty("fluidResourceLocation", data.getFluidResourceLocation().toString());
        mappingEntry.addProperty("amount", data.getAmount());
        return mappingEntry;
    }

    public boolean tryAddingDefaultMapping(String m) {
        List<GoopValueMapping> matchingDefaults = DefaultMappings.values.stream().filter(v -> v.getItemResourceLocation().equals(m)).collect(Collectors.toList());
        if (matchingDefaults.size() == 0) {
            return false;
        }
        if (matchingDefaults.size() > 1) {
            System.out.println("Default mapping error: more than one default was found for " + m +". Please report this to Goop! Using first found.");
        }
        mappings.add(matchingDefaults.get(0));
        return true;
    }

    public void addEmptyMapping(String m) {
        mappings.add(new GoopValueMapping(m, new ArrayList<>()));
    }

    public void sortMappings() {
        mappings.sort(GoopValueMapping.registryNameSansNamespaceComparator);
    }

    public void clear() {
        mappings.clear();
    }
}
