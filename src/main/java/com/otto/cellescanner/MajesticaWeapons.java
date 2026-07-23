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

import java.io.*;
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
        weapons.clear();
        weaponMap.clear();

        File manifestFile = MajesticaDownloader.INSTANCE.getManifestFile();
        if (!manifestFile.exists()) {
            return;
        }

        initialized = true;

        try {
            InputStreamReader reader = new InputStreamReader(new FileInputStream(manifestFile), StandardCharsets.UTF_8);
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
            reader.close();
        } catch (Throwable t) {
            System.err.println("[MajesticaWeapons] Failed to load weapons_majestica.json: " + t.getMessage());
        }

        injectResourcePack();

        // Register all variant models with Forge ModelBakery & ModelLoader so textures & models get baked
        try {
            List<ResourceLocation> locs = new ArrayList<ResourceLocation>();
            for (Weapon w : weapons) {
                locs.add(new ResourceLocation("cellescanner", "majestica/" + w.id));
                ModelLoader.setCustomModelResourceLocation(net.minecraft.init.Items.diamond_sword, 0, w.modelLoc);
            }
            if (!locs.isEmpty()) {
                net.minecraft.client.resources.model.ModelBakery.registerItemVariants(
                        net.minecraft.init.Items.diamond_sword, locs.toArray(new ResourceLocation[0]));
            }
        } catch (Throwable t) {
            System.err.println("[MajesticaWeapons] Failed to register variants: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public void injectResourcePack() {
        try {
            File dir = MajesticaDownloader.INSTANCE.getMajesticaDir();
            if (!dir.exists()) {
                return;
            }
            net.minecraft.client.resources.FolderResourcePack pack = new net.minecraft.client.resources.FolderResourcePack(dir);
            
            List<net.minecraft.client.resources.IResourcePack> defaultPacks = null;
            for (java.lang.reflect.Field f : Minecraft.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object val = f.get(Minecraft.getMinecraft());
                    if (val instanceof List) {
                        List<?> l = (List<?>) val;
                        if (!l.isEmpty() && l.get(0) instanceof net.minecraft.client.resources.IResourcePack) {
                            defaultPacks = (List<net.minecraft.client.resources.IResourcePack>) l;
                            break;
                        }
                    }
                }
            }

            if (defaultPacks != null) {
                boolean already = false;
                for (net.minecraft.client.resources.IResourcePack p : defaultPacks) {
                    if (p.getPackName().equals(pack.getPackName())) {
                        already = true;
                        break;
                    }
                }
                if (!already) {
                    defaultPacks.add(pack);
                }
            }
        } catch (Throwable t) {
            System.err.println("[MajesticaWeapons] Resource pack injection error: " + t.getMessage());
        }
    }

    public void hookMesher() {
        try {
            net.minecraft.client.renderer.entity.RenderItem ri = Minecraft.getMinecraft().getRenderItem();
            if (ri == null || ri.getItemModelMesher() instanceof CustomItemModelMesher) {
                return;
            }
            CustomItemModelMesher customMesher = new CustomItemModelMesher(ri.getItemModelMesher().getModelManager());
            java.lang.reflect.Field field = null;
            for (java.lang.reflect.Field f : net.minecraft.client.renderer.entity.RenderItem.class.getDeclaredFields()) {
                if (f.getType() == net.minecraft.client.renderer.ItemModelMesher.class) {
                    field = f;
                    break;
                }
            }
            if (field != null) {
                field.setAccessible(true);
                field.set(ri, customMesher);
            }
        } catch (Throwable ignored) {
        }
    }

    public static class CustomItemModelMesher extends net.minecraft.client.renderer.ItemModelMesher {
        public CustomItemModelMesher(net.minecraft.client.resources.model.ModelManager modelManager) {
            super(modelManager);
        }

        @Override
        public IBakedModel getItemModel(ItemStack stack) {
            IBakedModel custom = MajesticaWeapons.INSTANCE.getCustomModel(stack);
            if (custom != null) {
                return custom;
            }
            return super.getItemModel(stack);
        }
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        bakedCache.clear();
    }

    public List<Weapon> getWeapons() {
        if (weapons.isEmpty() && !initialized) {
            init();
        }
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
