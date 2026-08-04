package com.otto.cellescanner;

import net.minecraft.util.BlockPos;

/**
 * One mine's worth of settings for Auto Mine: the area to dig and the three
 * places the bot has to walk to when it cannot keep mining.
 *
 * These used to be compiled in, pointing at one mine on one server, so the bot
 * only worked there. Everything here is per profile, so a second mine is a
 * second profile rather than a rebuild.
 *
 * Each position is stored as plain ints with a flag rather than a BlockPos,
 * because this is written to the config as JSON and a null is easier to get
 * wrong than a boolean.
 */
public class MineProfile {

    public String name = "Ny mine";

    /** The box to mine. When false the built-in default box is used. */
    public boolean areaSet = false;
    public int x1, y1, z1;
    public int x2, y2, z2;
    public int dimension = 0;

    /** Sign the bot right-clicks to buy a replacement pickaxe. */
    public boolean shopSet = false;
    public int shopX, shopY, shopZ;

    /** The Skraldespand the bot opens when its inventory is full. */
    public boolean depositSet = false;
    public int depositX, depositY, depositZ;

    /** Where mined iron is handed in. */
    public boolean ironSet = false;
    public int ironX, ironY, ironZ;

    public MineProfile() {
    }

    public MineProfile(String name) {
        this.name = name;
    }

    public BlockPos shopPos() {
        return shopSet ? new BlockPos(shopX, shopY, shopZ) : null;
    }

    public BlockPos depositPos() {
        return depositSet ? new BlockPos(depositX, depositY, depositZ) : null;
    }

    public BlockPos ironPos() {
        return ironSet ? new BlockPos(ironX, ironY, ironZ) : null;
    }

    public void setShop(BlockPos p, int dim) {
        shopX = p.getX(); shopY = p.getY(); shopZ = p.getZ();
        shopSet = true;
        rememberDimension(dim);
    }

    public void setDeposit(BlockPos p, int dim) {
        depositX = p.getX(); depositY = p.getY(); depositZ = p.getZ();
        depositSet = true;
        rememberDimension(dim);
    }

    public void setIron(BlockPos p, int dim) {
        ironX = p.getX(); ironY = p.getY(); ironZ = p.getZ();
        ironSet = true;
        rememberDimension(dim);
    }

    public void setArea(BlockPos a, BlockPos b, int dim) {
        x1 = a.getX(); y1 = a.getY(); z1 = a.getZ();
        x2 = b.getX(); y2 = b.getY(); z2 = b.getZ();
        areaSet = true;
        dimension = dim;
    }

    /**
     * A profile with nothing set yet has no dimension of its own, so the first
     * thing picked decides it. Once the area is set that wins, since the area is
     * the part that must be in the right world.
     */
    private void rememberDimension(int dim) {
        if (!areaSet) {
            dimension = dim;
        }
    }

    /** minX, maxX, minY, maxY, minZ, maxZ, or null when no area is set. */
    public int[] bounds() {
        if (!areaSet) {
            return null;
        }
        return new int[] {
                Math.min(x1, x2), Math.max(x1, x2),
                Math.min(y1, y2), Math.max(y1, y2),
                Math.min(z1, z2), Math.max(z1, z2)
        };
    }

    /** How much of the profile is filled in, for the list in the GUI. */
    public int configuredCount() {
        int n = 0;
        if (areaSet) n++;
        if (shopSet) n++;
        if (depositSet) n++;
        if (ironSet) n++;
        return n;
    }

    public String displayName() {
        return name == null || name.trim().isEmpty() ? "Uden navn" : name.trim();
    }
}
