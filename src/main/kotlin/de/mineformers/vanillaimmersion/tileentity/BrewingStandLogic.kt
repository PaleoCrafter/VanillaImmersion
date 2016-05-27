package de.mineformers.vanillaimmersion.tileentity

import de.mineformers.vanillaimmersion.util.Inventories
import net.minecraft.block.state.IBlockState
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.NetworkManager
import net.minecraft.network.play.server.SPacketUpdateTileEntity
import net.minecraft.tileentity.TileEntityBrewingStand
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * Implements all logic and data storage for the brewing stand.
 */
class BrewingStandLogic : TileEntityBrewingStand() {
    companion object {
        /**
         * Helper enum for meaningful interaction with the inventory.
         */
        enum class Slot {
            /**
             * The first bottle to be modified.
             */
            BOTTLE1,
            /**
             * The second bottle to be modified.
             */
            BOTTLE2,
            /**
             * The third bottle to be modified.
             */
            BOTTLE3,
            /**
             * The ingredient to be infused into each bottle.
             */
            INPUT_INGREDIENT,
            /**
             * Blaze powder used to power the brewing.
             */
            INPUT_POWDER
        }
    }

    /**
     * The brewing stand's block state.
     */
    val blockState: IBlockState
        get() = worldObj.getBlockState(pos)

    /**
     * Gets the ItemStack in a given slot.
     * Marked as operator to allow this: `stand[slot]`
     */
    operator fun get(slot: Slot): ItemStack? = getStackInSlot(slot.ordinal)

    /**
     * Sets the ItemStack in a given slot.
     * Marked as operator to allow this: `stand[slot] = stack`
     */
    operator fun set(slot: Slot, stack: ItemStack?) = setInventorySlotContents(slot.ordinal, stack)

    /**
     * Checks whether a given item stack may be inserted as fuel into this brewing stand.
     */
    fun canInsertFuel(stack: ItemStack?): Boolean {
        // Only actual fuel can be inserted, obviously
        if (stack == null || !isItemValidForSlot(4, stack))
            return false
        val existingIngredient = get(Slot.INPUT_INGREDIENT)
        val existingFuel = get(Slot.INPUT_POWDER)
        // Prefer the ingredient slot, if the stack is a valid ingredient
        if (existingIngredient == null && isItemValidForSlot(3, stack))
            return false
        // Prefer the ingredient slot, if it still has space for the stack
        if (existingIngredient != null && existingIngredient.item === stack.item)
            return existingIngredient.stackSize == existingIngredient.maxStackSize
        // Only allow insertion if there is no fuel already or there is more space
        return existingFuel == null ||
               (existingFuel.item === stack.item && existingFuel.stackSize != existingFuel.maxStackSize)
    }

    /**
     * Composes a tag for updates of the TE (both initial chunk data and later updates).
     */
    override fun getUpdateTag(): NBTTagCompound? {
        val compound = writeToNBT(NBTTagCompound())
        return compound
    }

    /**
     * Creates a packet for updates of the tile entity at runtime.
     */
    override fun getUpdatePacket() = SPacketUpdateTileEntity(this.pos, 0, this.updateTag)

    override fun shouldRefresh(world: World, pos: BlockPos, oldState: IBlockState, newSate: IBlockState) =
        oldState.block !== newSate.block

    /**
     * Reads data from the update packet.
     */
    override fun onDataPacket(net: NetworkManager, pkt: SPacketUpdateTileEntity) {
        Inventories.clear(this)
        val compound = pkt.nbtCompound
        readFromNBT(compound)
    }
}