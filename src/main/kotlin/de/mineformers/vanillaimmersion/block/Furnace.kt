package de.mineformers.vanillaimmersion.block

import de.mineformers.vanillaimmersion.tileentity.FurnaceLogic
import net.minecraft.block.BlockFurnace
import net.minecraft.block.SoundType
import net.minecraft.block.state.IBlockState
import net.minecraft.creativetab.CreativeTabs
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.SoundEvents
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.ResourceLocation
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.util.Random

/**
 * Immersive Furnace implementation.
 * Derives from the Vanilla furnace to allow substitution later on.
 * Technically, the lit/unlit status could be handled through blockstates,
 * but this mod intends to keep full Vanilla compatibility, metadata can thus not be modified.
 */
open class Furnace(val lit: Boolean) : BlockFurnace(lit) {
    init {
        setHardness(3.5F)
        soundType = SoundType.STONE
        unlocalizedName = "furnace"
        registryName = ResourceLocation("minecraft", if (lit) "lit_furnace" else "furnace")
        if (lit) {
            setLightLevel(0.875F)
        } else {
            setCreativeTab(CreativeTabs.DECORATIONS)
        }
    }

    @Deprecated("Vanilla")
    override fun isOpaqueCube(state: IBlockState) = false

    override fun createNewTileEntity(worldIn: World, meta: Int) = FurnaceLogic() // Return our own implementation

    /**
     * Handles right clicks for the furnace.
     */
    override fun onBlockActivated(world: World, pos: BlockPos, state: IBlockState,
                                  player: EntityPlayer, hand: EnumHand,
                                  side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float) = false

    /**
     * Spawns smoke and fire particles while the furnace is burning.
     * Also plays the crackling sound.
     * Mostly a straight copy from Vanilla, modified to always spawn in the center though.
     */
    @SideOnly(Side.CLIENT)
    override fun randomDisplayTick(state: IBlockState, world: World, pos: BlockPos, rand: Random) {
        if (!lit) {
            return
        }
        val x = pos.x.toDouble() + 0.5
        val y = pos.y.toDouble() + rand.nextDouble() * 6.0 / 16.0
        val z = pos.z.toDouble() + 0.5
        val offset = rand.nextDouble() * 0.6 - 0.3

        world.spawnParticle(EnumParticleTypes.SMOKE_NORMAL, x - offset, y, z + offset, 0.0, 0.0, 0.0)
        world.spawnParticle(EnumParticleTypes.FLAME, x - offset, y, z + offset, 0.0, 0.0, 0.0)

        if (rand.nextDouble() < 0.1) {
            world.playSound(
                pos.x + 0.5,
                pos.y.toDouble(),
                pos.z + 0.5,
                SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE,
                SoundCategory.BLOCKS,
                1.0f,
                1.0f,
                false
            )
        }
    }
}
