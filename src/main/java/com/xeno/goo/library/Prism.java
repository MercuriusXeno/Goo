package com.xeno.goo.library;

import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;

public class Prism
{
    // top
    public Vector3d npp; // x-, z+, y+
    public Vector3d ppp; // x+, z+, y+
    public Vector3d ppn; // x+, z-, y+
    public Vector3d npn; // x-, z-, y+
    public Vector3d nnp; // x-, z+, y-
    public Vector3d pnp; // x+, z+, y-
    public Vector3d pnn; // x+, z-, y-
    public Vector3d nnn; // x-, z-, y-

    public Prism(AxisAlignedBB bb) {
        npp = new Vector3d(bb.minX, bb.maxY, bb.maxZ);
        ppp = new Vector3d(bb.maxX, bb.maxY, bb.maxZ);
        ppn = new Vector3d(bb.maxX, bb.maxY, bb.minZ);
        npn = new Vector3d(bb.minX, bb.maxY, bb.minZ);
        nnp = new Vector3d(bb.minX, bb.minY, bb.maxZ);
        pnp = new Vector3d(bb.maxX, bb.minY, bb.maxZ);
        pnn = new Vector3d(bb.maxX, bb.minY, bb.minZ);
        nnn = new Vector3d(bb.minX, bb.minY, bb.minZ);
    }

    public Vector3d[] top() {
        return new Vector3d[] { ppp, npp, npn, ppn };
    }

    public Vector3d[] bottom() {
        return new Vector3d[] { pnp, nnp, nnn, pnn };
    }

    public Vector3d[] north() {
        return new Vector3d[] { npp, npn, nnn, nnp };
    }

    public Vector3d[] south() {
        return new Vector3d[] { ppn, ppp, pnp, pnn };
    }

    public Vector3d[] east() {
        return new Vector3d[] { npn, ppn, pnn, nnn };
    }

    public Vector3d[] west() {
        return new Vector3d[] { ppp, npp, nnp, pnp };
    }
}
