package com.otto.cellescanner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Handles the 120 3D fantasy weapon models from Blades of Majestica.
 * Loads weapons_majestica.json, registers variant models with Forge ModelLoader,
 * and provides IBakedModel lookups for held/inventory swords & axes.
 */
public class MajesticaWeapons {

    public static final MajesticaWeapons INSTANCE = new MajesticaWeapons();

    public static class Weapon {
        public final String id;
        public final String modelPath;
        public final String namePattern;
        public final Set<String> itemTypes;
        public final Pattern compiledPattern;
        public final ModelResourceLocation modelLoc;

        public Weapon(String id, String modelPath, String namePattern, Set<String> itemTypes) {
            this.id = id;
            this.modelPath = modelPath;
            this.namePattern = namePattern;
            this.itemTypes = itemTypes;
            this.modelLoc = new ModelResourceLocation("cellescanner:majestica/" + id, "inventory");

            // Convert ipattern glob (*abominable blade*) to regex
            String regex = "(?i)" + Pattern.quote(namePattern)
                    .replace("*", "\\E.*\\Q")
                    .replace("?", "\\E.\\Q");
            Pattern p;
            try {
                p = Pattern.compile(regex);
            } catch (Throwable t) {
                p = Pattern.compile("(?i)" + Pattern.quote(id));
            }
            this.compiledPattern = p;
        }

        public boolean matchesName(String displayName) {
            if (displayName == null || displayName.isEmpty()) {
                return false;
            }
            String clean = EnumChatFormatting.getTextWithoutFormattingCodes(displayName).trim();
            return compiledPattern.matcher(clean).find();
        }
    }

    private final List<Weapon> weapons = new ArrayList<Weapon>();
    private final Map<String, Weapon> weaponMap = new HashMap<String, Weapon>();
    private final Map<String, IBakedModel> bakedCache = new HashMap<String, IBakedModel>();
    private boolean initialized = false;

    private MajesticaWeapons() {
    }

    public void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        weapons.clear();
        weaponMap.clear();

        try {
            ResourceLocation loc = new ResourceLocation("cellescanner", "weapons_majestica.json");
            IResource res = Minecraft.getMinecraft().getResourceManager().getResource(loc);
            if (res != null) {
                InputStreamReader reader = new InputStreamReader(res.getInputStream(), StandardCharsets.UTF_8);
                JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
                JsonArray list = root.getAsJsonArray("weapons");
                if (list != null) {
                    for (JsonElement elem : list) {
                        JsonObject obj = elem.getAsJsonObject();
                        String id = obj.get("id").getAsString();
                        String model = obj.get("model").getAsString();
                        String pattern = obj.get("namePattern").getAsString();
                        Set<String> items = new HashSet<String>();
                        if (obj.has("items")) {
                            for (JsonElement it : obj.getAsJsonArray("items")) {
                                items.add(it.getAsString());
                            }
                        }
                        Weapon w = new Weapon(id, model, pattern, items);
                        weapons.add(w);
                        weaponMap.put(id, w);
                    }
                }
            }
        } catch (Throwable t) {
            System.err.println("[MajesticaWeapons] Failed to load weapons_majestica.json: " + t.getMessage());
        }

        // Register all variant models with Forge ModelLoader so textures & models get baked
        for (Weapon w : weapons) {
            ModelLoader.setCustomModelResourceLocation(
                    net.minecraft.init.Items.diamond_sword, 0, w.modelLoc);
        }
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        bakedCache.clear();
    }

    public List<Weapon> getWeapons() {
        return Collections.unmodifiableList(weapons);
    }

    public Weapon getWeaponById(String id) {
        return weaponMap.get(id);
    }

    /**
     * Resolves the custom 3D baked model for an ItemStack.
     * Returns null if disabled or stack is not a sword/axe matching a 3D skin.
     */
    public IBakedModel getCustomModel(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        boolean enabled = CelleScannerMod.config.majesticaEnabled == null || CelleScannerMod.config.majesticaEnabled;
        if (!enabled) {
            return null;
        }

        String itemName = Item.itemRegistry.getNameForObject(stack.getItem()).toString().replace("minecraft:", "");

        // 1. Check anvil name pattern match (takes precedence)
        if (stack.hasDisplayName()) {
            String name = stack.getDisplayName();
            for (Weapon w : weapons) {
                if (w.itemTypes.contains(itemName) && w.matchesName(name)) {
                    return getBakedModel(w);
                }
            }
        }

        // 2. Check GUI manual selection
        String selId = CelleScannerMod.config.majesticaSelectedWeaponId;
        if (selId != null && !selId.isEmpty()) {
            Weapon w = weaponMap.get(selId);
            if (w != null && (w.itemTypes.isEmpty() || w.itemTypes.contains(itemName) || itemName.contains("sword") || itemName.contains("axe"))) {
                return getBakedModel(w);
            }
        }

        return null;
    }

    public IBakedModel getBakedModel(Weapon w) {
        if (w == null) {
            return null;
        }
        IBakedModel cached = bakedCache.get(w.id);
        if (cached != null) {
            return cached;
        }
        try {
            IBakedModel model = Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager().getModel(w.modelLoc);
            if (model != null && model != Minecraft.getMinecraft().getRenderItem().getItemModelMesher().getModelManager().getMissingModel()) {
                bakedCache.put(w.id, model);
                return model;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
