package de.mineformers.vanillaimmersion.block

import de.mineformers.vanillaimmersion.tileentity.CraftingTableLogic
import de.mineformers.vanillaimmersion.util.spill
import net.minecraft.block.BlockHorizontal
import net.minecraft.block.BlockWorkbench
import net.minecraft.block.SoundType
import net.minecraft.block.state.BlockStateContainer
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World

/**
 * Immersive Crafting Table implementation.
 * Derives from the Vanilla crafting table to allow substitution later on.
 * Adds a tile entity to the crafting table, but this does not break Vanilla compatibility.
 * Also adds a facing property to the block state, which also does not break compatibility.
 */
open class CraftingTable : BlockWorkbench() {
    companion object {
        /**
         * Facing property indicating the table's front.
         */
        val FACING = BlockHorizontal.FACING
    }

    init {
        setHardness(2.5f)
        soundType = SoundType.WOOD
        unlocalizedName = "workbench"
        defaultState = blockState.baseState.withProperty(FACING, EnumFacing.NORTH)
        registryName = ResourceLocation("minecraft", "crafting_table")
    }

    @Deprecated("Vanilla")
    override fun getBoundingBox(state: IBlockState?, source: IBlockAccess?, pos: BlockPos?) =
        AxisAlignedBB(.0, .0, .0, 1.0, .875, 1.0)

    @Deprecated("Vanilla")
    override fun isFullCube(state: IBlockState) = false

    @Deprecated("Vanilla")
    override fun isOpaqueCube(state: IBlockState) = false

    /**
     * Handles right clicks for the crafting table.
     * Does not do anything since interaction is handled through
     * [CraftingHandler][de.mineformers.vanillaimmersion.immersion.CraftingHandler].
     */
    override fun onBlockActivated(world: World, pos: BlockPos, state: IBlockState,
                                  player: EntityPlayer, hand: EnumHand,
                                  side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float) = false

    /**
     * Makes the crafting table face its placer.
     */
    override fun getStateForPlacement(world: World, pos: BlockPos,
                                      side: EnumFacing, hitX: Float, hitY: Float, hitZ: Float,
                                      meta: Int, placer: EntityLivingBase): IBlockState {
        return this.defaultState.withProperty(FACING, placer.horizontalFacing.opposite)
    }

    /**
     * Makes the crafting table face its placer.
     */
    override fun onBlockPlacedBy(world: World, pos: BlockPos, state: IBlockState,
                                 placer: EntityLivingBase, stack: ItemStack) {
        val tile = world.getTileEntity(pos) as? CraftingTableLogic ?: return
        tile.facing = placer.horizontalFacing.opposite
        world.notifyBlockUpdate(pos, state, state, 3)
    }

    /**
     * Drops the crafting table's contents when it's broken.
     */
    override fun breakBlock(world: World, pos: BlockPos, state: IBlockState) {
        val tile = world.getTileEntity(pos)

        if (tile is CraftingTableLogic) {
            tile.inventory.spill(world, pos, 1 until tile.inventory.slots)
            world.updateComparatorOutputLevel(pos, this)
        }
        super.breakBlock(world, pos, state)
    }

    /**
     * Orientates the crafting table according to its neighbors when it's added to the world.
     * This way, the table will most likely be facing the correct way when generated in some structure.
     */
    override fun onBlockAdded(world: World, pos: BlockPos, state: IBlockState) {
        this.setDefaultFacing(world, pos, state)
    }

    /**
     * Orientates the crafting table according to its neighbors when it's added to the world.
     * Pretty much a straight copy from the Vanilla furnace since it shows similar behavior.
     */
    private fun setDefaultFacing(world: World, pos: BlockPos, state: IBlockState) {
        if (!world.isRemote) {
            val tile = world.getTileEntity(pos) as? CraftingTableLogic ?: return
            val north = world.getBlockState(pos.north())
            val south = world.getBlockState(pos.south())
            val west = world.getBlockState(pos.west())
            val east = world.getBlockState(pos.east())
            val facing = tile.facing

            if (facing == EnumFacing.NORTH && north.isFullBlock && !south.isFullBlock) {
                tile.facing = EnumFacing.SOUTH
            } else if (facing == EnumFacing.SOUTH && south.isFullBlock && !north.isFullBlock) {
                tile.facing = EnumFacing.NORTH
            } else if (facing == EnumFacing.WEST && west.isFullBlock && !east.isFullBlock) {
                tile.facing = EnumFacing.EAST
            } else if (facing == EnumFacing.EAST && east.isFullBlock && !west.isFullBlock) {
                tile.facing = EnumFacing.WEST
            }

            world.notifyBlockUpdate(pos, state, state, 2)
        }
    }

    @Deprecated("Vanilla")
    override fun getStateFromMeta(meta: Int) = defaultState

    @Deprecated("Vanilla")
    override fun getMetaFromState(state: IBlockState) = 0

    override fun getActualState(state: IBlockState, world: IBlockAccess, pos: BlockPos): IBlockState {
        val tile = world.getTileEntity(pos) as? CraftingTableLogic ?: return super.getActualState(state, world, pos)
        return state.withProperty(FACING, tile.facing)
    }

    /**
     * We need to override this since the Vanilla table does not have a TileEntity.
     */
    override fun hasTileEntity(state: IBlockState?) = true

    /**
     * Create the anvil's TileEntity.
     */
    override fun createTileEntity(world: World?, state: IBlockState?) = CraftingTableLogic()

    /**
     * Tells Minecraft that the [FACING] property belongs to the crafting table.
     */
    override fun createBlockState() = BlockStateContainer(this, FACING)
}