package com.legobmw99.allomancy.modules.powers.client;

import com.legobmw99.allomancy.Allomancy;
import com.legobmw99.allomancy.modules.combat.CombatSetup;
import com.legobmw99.allomancy.modules.powers.PowersConfig;
import com.legobmw99.allomancy.modules.powers.client.gui.MetalOverlay;
import com.legobmw99.allomancy.modules.powers.client.gui.MetalSelectScreen;
import com.legobmw99.allomancy.modules.powers.client.particle.SoundParticle;
import com.legobmw99.allomancy.modules.powers.network.ChangeEmotionPacket;
import com.legobmw99.allomancy.modules.powers.network.TryPushPullBlock;
import com.legobmw99.allomancy.modules.powers.network.TryPushPullEntity;
import com.legobmw99.allomancy.modules.powers.util.AllomancyCapability;
import com.legobmw99.allomancy.modules.powers.util.AllomancyUtils;
import com.legobmw99.allomancy.network.Network;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.CreatureEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.opengl.GL11;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class ClientEventHandler {


    private final Minecraft mc = Minecraft.getInstance();

    private Set<Entity> metal_entities = new HashSet<>();
    private Set<BlockPos> metal_blocks = new HashSet<>();
    private Set<PlayerEntity> nearby_allomancers = new HashSet<>();


    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onClientTick(final TickEvent.ClientTickEvent event) {
        // Run once per tick, only if in game, and only if there is a player
        if (event.phase == TickEvent.Phase.END && !this.mc.isGamePaused() && this.mc.player != null && this.mc.player.isAlive()) {

            PlayerEntity player = mc.player;
            AllomancyCapability cap = AllomancyCapability.forPlayer(player);

            if (cap.getAllomancyPower() >= 0) {
                // Handle our input-based powers
                if (this.mc.gameSettings.keyBindAttack.isKeyDown()) {
                    // Ray trace 20 blocks
                    RayTraceResult trace = ClientUtils.getMouseOverExtended(20F);
                    // All iron pulling powers
                    if (cap.getMetalBurning(Allomancy.IRON)) {
                        if (trace != null) {
                            if (trace.getType() == RayTraceResult.Type.ENTITY && AllomancyUtils.isEntityMetal(((EntityRayTraceResult) trace).getEntity())) {
                                Network.sendToServer(new TryPushPullEntity(((EntityRayTraceResult) trace).getEntity().getEntityId(), AllomancyUtils.PULL));
                            }

                            if (trace.getType() == RayTraceResult.Type.BLOCK) {
                                BlockPos bp = ((BlockRayTraceResult) trace).getPos();
                                if (AllomancyUtils.isBlockMetal(this.mc.world.getBlockState(bp).getBlock()) || (player.getHeldItemMainhand().getItem() == CombatSetup.COIN_BAG.get() && player.isCrouching())) {
                                    Network.sendToServer(new TryPushPullBlock(bp, AllomancyUtils.PULL));
                                }
                            }
                        }
                    }
                    // All zinc powers
                    if (cap.getMetalBurning(Allomancy.ZINC)) {
                        Entity entity;
                        if ((trace != null) && (trace.getType() == RayTraceResult.Type.ENTITY)) {
                            entity = ((EntityRayTraceResult) trace).getEntity();
                            if (entity instanceof CreatureEntity) {
                                Network.sendToServer(new ChangeEmotionPacket(entity.getEntityId(), true));
                            }
                        }
                    }
                }
                if (this.mc.gameSettings.keyBindUseItem.isKeyDown()) {
                    // Ray trace 20 blocks
                    RayTraceResult trace = ClientUtils.getMouseOverExtended(20F);
                    // All steel pushing powers
                    if (cap.getMetalBurning(Allomancy.STEEL)) {
                        if (trace != null) {
                            if (trace.getType() == RayTraceResult.Type.ENTITY && AllomancyUtils.isEntityMetal(((EntityRayTraceResult) trace).getEntity())) {
                                Network.sendToServer(new TryPushPullEntity(((EntityRayTraceResult) trace).getEntity().getEntityId(), AllomancyUtils.PUSH));
                            }

                            if (trace.getType() == RayTraceResult.Type.BLOCK) {
                                BlockPos bp = ((BlockRayTraceResult) trace).getPos();
                                if (AllomancyUtils.isBlockMetal(this.mc.world.getBlockState(bp).getBlock()) || (player.getHeldItemMainhand().getItem() == CombatSetup.COIN_BAG.get() && player.isCrouching())) {
                                    Network.sendToServer(new TryPushPullBlock(bp, AllomancyUtils.PUSH));
                                }
                            }
                        }
                    }
                    // All brass powers
                    if (cap.getMetalBurning(Allomancy.BRASS)) {
                        Entity entity;
                        if ((trace != null) && (trace.getType() == RayTraceResult.Type.ENTITY)) {
                            entity = ((EntityRayTraceResult) trace).getEntity();
                            if (entity instanceof CreatureEntity) {
                                Network.sendToServer(new ChangeEmotionPacket(entity.getEntityId(), false));
                            }
                        }
                    }
                }


                // Populate the metal lists
                metal_blocks.clear();
                metal_entities.clear();
                if (cap.getMetalBurning(Allomancy.IRON) || cap.getMetalBurning(Allomancy.STEEL)) {
                    List<Entity> entities;
                    Stream<BlockPos> blocks;
                    int max = PowersConfig.max_metal_detection.get();
                    BlockPos negative = new BlockPos(player).add(-max, -max, -max);
                    BlockPos positive = new BlockPos(player).add(max, max, max);

                    // Add metal entities to metal list
                    entities = player.world.getEntitiesWithinAABB(Entity.class, new AxisAlignedBB(negative, positive));
                    entities.forEach(entity -> {
                        if (AllomancyUtils.isEntityMetal(entity)) {
                            metal_entities.add(entity);
                        }
                    });

                    // Add metal blocks to metal list
                    blocks = BlockPos.getAllInBox(negative, positive);
                    blocks.forEach(bp -> {
                        BlockPos imBlock = bp.toImmutable();
                        if (AllomancyUtils.isBlockMetal(player.world.getBlockState(imBlock).getBlock())) {
                            metal_blocks.add(imBlock);
                        }
                    });

                }
                // Populate our list of nearby allomancy users
                nearby_allomancers.clear();
                if (cap.getMetalBurning(Allomancy.BRONZE) && !cap.getMetalBurning(Allomancy.COPPER)) {
                    List<PlayerEntity> nearby_players;
                    // Add metal burners to a list
                    BlockPos negative = new BlockPos(player).add(-30, -30, -30);
                    BlockPos positive = new BlockPos(player).add(30, 30, 30);
                    // Add entities to metal list
                    nearby_players = player.world.getEntitiesWithinAABB(PlayerEntity.class, new AxisAlignedBB(negative, positive), entity -> entity != null && entity != player);

                    for (PlayerEntity otherPlayer : nearby_players) {
                        AllomancyCapability capOther = AllomancyCapability.forPlayer(otherPlayer);
                        if (capOther.getMetalBurning(Allomancy.COPPER)) { // player is inside a smoker cloud, should not detect
                            nearby_allomancers.clear();
                            return;
                        } else if (capOther.getMetalBurning(Allomancy.IRON) || capOther.getMetalBurning(Allomancy.STEEL) || capOther.getMetalBurning(Allomancy.TIN)
                                || capOther.getMetalBurning(Allomancy.PEWTER) || capOther.getMetalBurning(Allomancy.ZINC) || capOther.getMetalBurning(Allomancy.BRASS)
                                || capOther.getMetalBurning(Allomancy.BRONZE)) {
                            nearby_allomancers.add(otherPlayer);
                        }
                    }
                }
            }
        }
    }


    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onKeyInput(final InputEvent.KeyInputEvent event) {
        if (PowerClientSetup.burn.isPressed()) {
            PlayerEntity player = mc.player;
            AllomancyCapability cap;
            if (mc.currentScreen == null) {
                if (player == null || !this.mc.isGameFocused()) {
                    return;
                }
                cap = AllomancyCapability.forPlayer(player);

                //Mistings only have one metal, so toggle that one
                if (cap.getAllomancyPower() >= 0 && cap.getAllomancyPower() < 8) {
                    ClientUtils.toggleMetalBurn(cap.getAllomancyPower(), cap);
                }

                //If the player is a full Mistborn, display the GUI
                if (cap.getAllomancyPower() == 8) {
                    this.mc.displayGuiScreen(new MetalSelectScreen());
                }
            }
        }
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent event) {

        if (event.isCancelable() || event.getType() != ElementType.EXPERIENCE) {
            return;
        }
        if (!this.mc.isGameFocused() || !this.mc.player.isAlive()) {
            return;
        }
        if (this.mc.currentScreen != null && !(this.mc.currentScreen instanceof ChatScreen) && !(this.mc.currentScreen instanceof MetalSelectScreen)) {
            return;
        }

        MetalOverlay.drawMetalOverlay();
    }

    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onRenderGUIScreen(GuiScreenEvent.DrawScreenEvent event) {
        if (event.getGui() instanceof MetalSelectScreen && !event.isCancelable()) {
            MetalOverlay.drawMetalOverlay();
        }
    }


    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        PlayerEntity player = mc.player;
        if (player == null || !player.isAlive()) {
            return;
        }

        AllomancyCapability cap = AllomancyCapability.forPlayer(player);

        if (cap.getAllomancyPower() < 0) {
            return;
        }


        Vec3d view = mc.gameRenderer.getActiveRenderInfo().getProjectedView();
        MatrixStack stack = event.getMatrixStack();
        stack.func_227861_a_(-view.x, -view.y, -view.z); // translate

        RenderSystem.pushMatrix();
        RenderSystem.multMatrix(stack.func_227866_c_().func_227870_a_());
        RenderSystem.disableTexture();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.polygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_LINE);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableBlend();


        Vec3d playervec = view.add(0, -.1, 0);
        // Iron and Steel lines
        if ((cap.getMetalBurning(Allomancy.IRON) || cap.getMetalBurning(Allomancy.STEEL))) {

            for (Entity entity : metal_entities) {
                ClientUtils.drawMetalLine(playervec, entity.getPositionVec(), 1.5F, 0F, 0.6F, 1F);
            }

            for (BlockPos b : metal_blocks) {
                ClientUtils.drawMetalLine(playervec, new Vec3d(b).add(0.5, 0.5, 0.5), 1.5F, 0F, 0.6F, 1F);
            }
        }

        if ((cap.getMetalBurning(Allomancy.BRONZE) && !cap.getMetalBurning(Allomancy.COPPER))) {
            for (PlayerEntity playerEntity : nearby_allomancers) {
                ClientUtils.drawMetalLine(playervec, playerEntity.getPositionVec(), 3.0F, 0.7F, 0.15F, 0.15F);
            }
        }

        RenderSystem.polygonMode(GL11.GL_FRONT_AND_BACK, GL11.GL_FILL);
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableTexture();
        RenderSystem.popMatrix();
    }


    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public void onSound(PlaySoundEvent event) {
        double motionX, motionY, motionZ, magnitude;

        PlayerEntity player = mc.player;
        ISound sound = event.getSound();
        if ((player == null) || (sound == null) || !player.isAlive()) {
            return;
        }

        AllomancyCapability cap = AllomancyCapability.forPlayer(player);
        if (cap.getMetalBurning(Allomancy.TIN)) {

            magnitude = Math.sqrt(player.getDistanceSq(sound.getX(), sound.getY(), sound.getZ()));

            if (((magnitude) > 25) || ((magnitude) < 3)) {
                return;
            }
            Vec3d vec = player.getPositionVec();
            double posX = vec.getX(), posY = vec.getY(), posZ = vec.getZ();
            // Spawn sound particles
            String soundName = sound.getSoundLocation().toString();
            if (soundName.contains("entity") || soundName.contains("step")) {
                motionX = ((posX - (event.getSound().getX() + .5)) * -0.7) / magnitude;
                motionY = ((posY - (event.getSound().getY() + .2)) * -0.7) / magnitude;
                motionZ = ((posZ - (event.getSound().getZ() + .5)) * -0.7) / magnitude;
                Particle particle = new SoundParticle(player.world, posX + (Math.sin(Math.toRadians(player.getRotationYawHead())) * -.7d), posY + .2, posZ + (Math.cos(Math.toRadians(player.getRotationYawHead())) * .7d), motionX,
                        motionY, motionZ, sound.getCategory());
                this.mc.particles.addEffect(particle);
            }
        }
    }
}