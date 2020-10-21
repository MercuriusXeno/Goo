package com.xeno.goo.client.particle;

import com.xeno.goo.GooMod;
import com.xeno.goo.client.render.FluidCuboidHelper;
import com.xeno.goo.setup.Registry;
import net.minecraft.client.Minecraft;
import net.minecraft.fluid.Fluid;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ParticleColorHelper {
    private static final Map<Fluid, Integer> fluidColorAverages = new HashMap<>();

    public static float red(int tempColor)
    {
        return colorFloat(tempColor, 16);
    }

    public static float green(int tempColor)
    {
        return colorFloat(tempColor, 8);
    }

    public static float blue(int tempColor)
    {
        return colorFloat(tempColor, 0);
    }

    public static float colorFloat(int tempColor, int i)
    {
        return (float)Math.floor(tempColor >> i & 0xff) / 255f;
    }

    public static int getColor(Fluid fluid, boolean isChromatic) {
        return fluidColor(fluid, isChromatic);
    }

    private static int fluidColor(Fluid f, boolean isChromatic)
    {
        if (isChromatic) {
            return FluidCuboidHelper.colorizeChromaticGoo();
        }
        if (!fluidColorAverages.containsKey(f)) {
            cacheFluidColor(f);
        }
        return fluidColorAverages.getOrDefault(f, 0x00ffffff);
    }

    private static void cacheFluidColor(Fluid f)
    {
        InputStream is;
        BufferedImage image = null;
        int[] pixel;
        try {
            String fluidResLoc;

            String fluidStillTexturePath = f.getAttributes().getStillTexture().getPath();
            fluidResLoc = GooMod.MOD_ID + ":textures/" + fluidStillTexturePath + ".png";
            is = Minecraft.getInstance().getResourceManager().getResource((new ResourceLocation(fluidResLoc))).getInputStream();
            image = ImageIO.read(is);
        } catch(IOException e) {
            GooMod.debug("Error loading a texture for rasterization/averaging for particle colors.");
            // eat this
        }
        int w = image.getWidth();
        int h = image.getHeight();
        int[] res = new int[w * h];
        for (int i = 0; i < w; i++) {
            for (int j = 0; j < h; j++) {
                pixel = image.getRaster().getPixel(i, j, new int[4]);
                res[j * w + i] = new Color(pixel[0], pixel[1], pixel[2]).getRGB();
            }
        }

        int avg = interpolateColor(res);;
        fluidColorAverages.put(f, avg);
    }

    private static int interpolateColor(int[] res)
    {
        double divisor = res.length;
        int r = 0;
        int g = 0;
        int b = 0;
        for (int c : res) {
            r += c >> 16 & 0xff;
            g += c >> 8 & 0xff;
            b += c & 0xff;
        }
        r = (int)Math.ceil((double)r / divisor);
        g = (int)Math.ceil((double)g / divisor);
        b = (int)Math.ceil((double)b / divisor);

        return r << 16 | g << 8 | b;
    }
}
