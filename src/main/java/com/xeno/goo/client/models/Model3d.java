package com.xeno.goo.client.models;

import com.xeno.goo.client.render.RenderHelper.FluidType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Direction;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;

import java.util.Arrays;

// unabashedly stolen from Mekanism
public class Model3d {

	public float minX, minY, minZ;
	public float maxX, maxY, maxZ;

	private final SpriteInfo[] textures = new SpriteInfo[6];
	private final boolean[] renderSides = new boolean[]{true, true, true, true, true, true};
	public final int[] rotations = new int[] { 0, 0, 0, 0, 0, 0};
	private final boolean[] flowingSides = new boolean[]{false, false, true, true, true, true};

	public void setSideRender(Direction side, boolean value) {
		renderSides[side.ordinal()] = value;
	}

	public Model3d copy() {
		Model3d copy = new Model3d();
		System.arraycopy(textures, 0, copy.textures, 0, textures.length);
		System.arraycopy(renderSides, 0, copy.renderSides, 0, renderSides.length);
		System.arraycopy(rotations, 0, copy.rotations, 0, rotations.length);
		System.arraycopy(flowingSides, 0, copy.flowingSides, 0, flowingSides.length);
		copy.minX = minX;
		copy.minY = minY;
		copy.minZ = minZ;
		copy.maxX = maxX;
		copy.maxY = maxY;
		copy.maxZ = maxZ;
		return copy;
	}

	public SpriteInfo getSpriteToRender(Direction side) {
		int ordinal = side.ordinal();
		if (renderSides[ordinal]) {
			return textures[ordinal];
		}
		return null;
	}

	public void setTexture(Direction side, SpriteInfo spriteInfo) {
		textures[side.ordinal()] = spriteInfo;
	}

	public void setTexture(TextureAtlasSprite tex) {
		setTexture(tex, 16);
	}

	public void setTexture(TextureAtlasSprite tex, int size) {
		Arrays.fill(textures, new SpriteInfo(tex, size));
	}

	public void setTextures(SpriteInfo down, SpriteInfo up, SpriteInfo north, SpriteInfo south, SpriteInfo west, SpriteInfo east) {
		textures[0] = down;
		textures[1] = up;
		textures[2] = north;
		textures[3] = south;
		textures[4] = west;
		textures[5] = east;
	}

	public void setRotations(int down, int up, int north, int south, int west, int east) {
		rotations[0] = down;
		rotations[1] = up;
		rotations[2] = north;
		rotations[3] = south;
		rotations[4] = west;
		rotations[5] = east;
	}

	public void setFlowingSides(boolean down, boolean up, boolean north, boolean south, boolean west, boolean east) {
		flowingSides[0] = down;
		flowingSides[1] = up;
		flowingSides[2] = north;
		flowingSides[3] = south;
		flowingSides[4] = west;
		flowingSides[5] = east;
	}

	public int downRotation() {
		return rotations[0];
	}

	public int upRotation() {
		return rotations[1];
	}

	public int northRotation() {
		return rotations[2];
	}

	public int southRotation() {
		return rotations[3];
	}

	public int westRotation() {
		return rotations[4];
	}

	public int eastRotation() {
		return rotations[5];
	}

	public FluidType downFlowing() {
		return flowingSides[0] ? FluidType.FLOWING : FluidType.STILL;
	}

	public FluidType upFlowing() {
		return flowingSides[1] ? FluidType.FLOWING : FluidType.STILL;
	}

	public FluidType northFlowing() {
		return flowingSides[2] ? FluidType.FLOWING : FluidType.STILL;
	}

	public FluidType southFlowing() {
		return flowingSides[3] ? FluidType.FLOWING : FluidType.STILL;
	}

	public FluidType westFlowing() {
		return flowingSides[4] ? FluidType.FLOWING : FluidType.STILL;
	}

	public FluidType eastFlowing() {
		return flowingSides[5] ? FluidType.FLOWING : FluidType.STILL;
	}

	public static final class SpriteInfo {

		public final TextureAtlasSprite sprite;
		public final int size;

		public SpriteInfo(TextureAtlasSprite sprite, int size) {
			this.sprite = sprite;
			this.size = size;
		}
	}

	public Vector3f minVec() {
		return new Vector3f(minX, minY, minZ);
	}

	public Vector3f maxVec() {
		return new Vector3f(maxX, maxY, maxZ);
	}
}
