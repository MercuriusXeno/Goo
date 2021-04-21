package com.xeno.goo.client.models;

import com.google.common.collect.ImmutableList;
import com.xeno.goo.entities.LightingBug;
import net.minecraft.client.renderer.entity.model.AgeableModel;
import net.minecraft.client.renderer.entity.model.ModelUtils;
import net.minecraft.client.renderer.model.ModelRenderer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.vector.Vector3f;

public class LightingBugModel<T extends LightingBug> extends AgeableModel<T> {

	private final ModelRenderer body;
	private final ModelRenderer rightWing;
	private final ModelRenderer leftWing;
	private final ModelRenderer frontLegs;
	private final ModelRenderer middleLegs;
	private final ModelRenderer backLegs;
	private final ModelRenderer leftAntenna;
	private final ModelRenderer rightAntenna;
	private float bodyPitch;

	public LightingBugModel() {

		super(false, 24.0F, 0.0F);
		this.textureWidth = 64;
		this.textureHeight = 64;
		this.body = new ModelRenderer(this);
		this.body.setRotationPoint(0.0F, 19.0F, 0.0F);

		ModelRenderer torso = new ModelRenderer(this, 0, 0);
		torso.setRotationPoint(0.0F, 0.0F, 0.0F);
		this.body.addChild(torso);
		torso.addBox(-3, -2f, -5, 6, 5, 10, 0.0F);

		this.leftAntenna = new ModelRenderer(this, 2, 0);
		this.leftAntenna.setRotationPoint(0.0F, -2.0F, -5.0F);
		this.leftAntenna.addBox(1.5F, -0.5F, -2.0F, 1.0F, 1.0F, 3.0F, 0.0F);

		this.rightAntenna = new ModelRenderer(this, 2, 3);
		this.rightAntenna.setRotationPoint(0.0F, -2.0F, -5.0F);
		this.rightAntenna.addBox(-2.5F, -0.5F, -2.0F, 1.0F, 1.0F, 3.0F, 0.0F);

		torso.addChild(this.leftAntenna);
		torso.addChild(this.rightAntenna);

		this.rightWing = new ModelRenderer(this, 0, 34);
		this.rightWing.setRotationPoint(-1.5F, -4.0F, -2.5F);
		this.rightWing.rotateAngleX = 0.0F;
		this.rightWing.rotateAngleY = ((float) Math.PI / 6F);
		this.rightWing.rotateAngleZ = 0.0F;
		this.body.addChild(this.rightWing);
		this.rightWing.addBox(-9.0F, 1.75F, 0.0F, 9.0F, 0.05f, 5.0F, 0.001F);

		this.leftWing = new ModelRenderer(this, 0, 34);
		this.leftWing.setRotationPoint(1.5F, -4.0F, -2.5F);
		this.leftWing.rotateAngleX = 0.0F;
		this.leftWing.rotateAngleY = -((float) Math.PI / 6F);
		this.leftWing.rotateAngleZ = 0.0F;
		this.leftWing.mirror = true;
		this.body.addChild(this.leftWing);
		this.leftWing.addBox(0.0F, 1.75F, 0.0F, 9.0F, 0.05f, 5.0F, 0.001F);

		this.frontLegs = new ModelRenderer(this);
		this.frontLegs.setRotationPoint(1.5F, 3.0F, -2.0F);
		this.body.addChild(this.frontLegs);
		this.frontLegs.setTextureOffset(26, 1).addBox(-4.5F, 0.0F, 0.0F, 6, 2, 0.15f, 0.0F);

		this.middleLegs = new ModelRenderer(this);
		this.middleLegs.setRotationPoint(1.5F, 3.0F, 0.0F);
		this.body.addChild(this.middleLegs);
		this.middleLegs.setTextureOffset(26, 3).addBox(-4.5F, 0.0F, 0.0F, 6, 2, 0.15f, 0.0F);

		this.backLegs = new ModelRenderer(this);
		this.backLegs.setRotationPoint(1.5F, 3.0F, 2.0F);
		this.body.addChild(this.backLegs);
		this.backLegs.setTextureOffset(26, 5).addBox(-4.5F, 0.0F, 0.0F, 6, 2, 0.15f, 0.0F);
	}

	public void setLivingAnimations(T entityIn, float limbSwing, float limbSwingAmount, float partialTick) {

		super.setLivingAnimations(entityIn, limbSwing, limbSwingAmount, partialTick);
		this.bodyPitch = entityIn.getBodyPitch(partialTick);
	}

	/**
	 * Sets this entity's model rotation angles
	 */
	public void setRotationAngles(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

		this.leftAntenna.rotateAngleX = 0.0F;
		this.rightAntenna.rotateAngleX = 0.0F;
		this.body.rotateAngleX = 0.0F;
		this.body.rotationPointY = 19.0F;
		boolean flag = entityIn.isOnGround() && entityIn.getMotion().lengthSquared() < 1.0E-7D;
		if (flag) {
			this.rightWing.rotateAngleY = ((float) Math.PI / 6F);
			this.rightWing.rotateAngleX = 0.0F;
			this.rightWing.rotateAngleZ = 0.0F;
			this.frontLegs.rotateAngleX = 0.0F;
			this.middleLegs.rotateAngleX = 0.0F;
			this.backLegs.rotateAngleX = 0.0F;
		} else {
			{
				float f = ageInTicks * 2.1F;
				final float cosF = MathHelper.cos(f);
				// rotation around the XY plane (front:back axis).
				// this makes the wings beat up and down, nothing special here
				this.rightWing.rotateAngleZ = cosF * ((float) Math.PI / 16F);
				// rotation around the XZ plane (up:down axis).
				// we want the wings to rotate back and forth, ending 'back' while 'down'
				// and further offset by 1/3rd so they spend 2/3rds of their time more back than forward
				// finally multiplying by the length of the normalized xz motion vector so that a bug that is 'hovering' doesn't do this
				Vector3f motion = new Vector3f(entityIn.getMotion());
				motion.normalize();
				motion.apply(MathHelper::abs);
				this.rightWing.rotateAngleY = (cosF * ((float) Math.PI / -18F) + ((float) Math.PI / 54F)) * (motion.getX() + motion.getZ());
				// rotation around the YZ plane (left:right axis).
				// we want the wings to be swept 'down' while moving 'up' and swept 'up' while moving 'down' and being flat at 'up' and 'down'
				// small offset is added to get timing slightly lagged, so that the back of the wing is still moving up when the front starts to move down & vice versa
				this.rightWing.rotateAngleX = MathHelper.sin(f + ((float) Math.PI / -16F)) * ((float) Math.PI / 48F);
			}
			this.frontLegs.rotateAngleX = ((float) Math.PI / 4F);
			this.middleLegs.rotateAngleX = ((float) Math.PI / 4F);
			this.backLegs.rotateAngleX = ((float) Math.PI / 4F);
			this.body.rotateAngleX = 0.0F;
			this.body.rotateAngleY = 0.0F;
			this.body.rotateAngleZ = 0.0F;
		}
		this.leftWing.rotateAngleX = this.rightWing.rotateAngleX;
		this.leftWing.rotateAngleY = -this.rightWing.rotateAngleY;
		this.leftWing.rotateAngleZ = -this.rightWing.rotateAngleZ;

		if (this.bodyPitch > 0.0F) {
			this.body.rotateAngleX = ModelUtils.func_228283_a_(this.body.rotateAngleX, (float) (Math.PI - 0.05), this.bodyPitch);
		}

	}

	protected Iterable<ModelRenderer> getHeadParts() {

		return ImmutableList.of();
	}

	protected Iterable<ModelRenderer> getBodyParts() {

		return ImmutableList.of(this.body);
	}
}
