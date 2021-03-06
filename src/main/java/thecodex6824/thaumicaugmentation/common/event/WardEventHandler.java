/**
 *  Thaumic Augmentation
 *  Copyright (c) 2019 TheCodex6824.
 *
 *  This file is part of Thaumic Augmentation.
 *
 *  Thaumic Augmentation is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Thaumic Augmentation is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with Thaumic Augmentation.  If not, see <https://www.gnu.org/licenses/>.
 */

package thecodex6824.thaumicaugmentation.common.event;

import java.util.ConcurrentModificationException;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.NextTickListEntry;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.Chunk.EnumCreateEntityType;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.UseHoeEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.BlockEvent.FarmlandTrampleEvent;
import net.minecraftforge.event.world.BlockEvent.FluidPlaceBlockEvent;
import net.minecraftforge.event.world.ChunkWatchEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import thecodex6824.thaumicaugmentation.api.block.property.IWardParticles;
import thecodex6824.thaumicaugmentation.api.event.BlockWardEvent;
import thecodex6824.thaumicaugmentation.api.warded.CapabilityWardStorage;
import thecodex6824.thaumicaugmentation.api.warded.IWardStorage;
import thecodex6824.thaumicaugmentation.api.warded.IWardStorageServer;
import thecodex6824.thaumicaugmentation.api.warded.WardHelper;
import thecodex6824.thaumicaugmentation.api.warded.WardSyncManager;
import thecodex6824.thaumicaugmentation.api.warded.WardSyncManager.DimensionalChunkPos;
import thecodex6824.thaumicaugmentation.api.warded.WardSyncManager.WardUpdateEntry;
import thecodex6824.thaumicaugmentation.common.network.PacketFullWardSync;
import thecodex6824.thaumicaugmentation.common.network.PacketParticleEffect;
import thecodex6824.thaumicaugmentation.common.network.PacketParticleEffect.ParticleEffect;
import thecodex6824.thaumicaugmentation.common.network.PacketWardUpdate;
import thecodex6824.thaumicaugmentation.common.network.TANetwork;

public class WardEventHandler {
    
    protected static void sendWardParticles(World world, BlockPos pos, EnumFacing facing) {
        if (!world.isRemote) {
            TANetwork.INSTANCE.sendToAllTracking(new PacketParticleEffect(ParticleEffect.WARD, pos.getX(), pos.getY(), pos.getZ(), facing.getIndex(),
                    0.5, 0.5, 0.5), new TargetPoint(world.provider.getDimension(), pos.getX(), pos.getY(), pos.getZ(), 64));
        }
    }
    
    protected static boolean isPlayerInChunkRange(DimensionalChunkPos pos, EntityPlayerMP player) {
        return FMLCommonHandler.instance().getMinecraftServerInstance().getWorld(pos.dim).getPlayerChunkMap().isPlayerWatchingChunk(player, pos.x, pos.z);
    }
    
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == Phase.END) {
            for (Map.Entry<DimensionalChunkPos, WardUpdateEntry> entry : WardSyncManager.getEntries()) {
                DimensionalChunkPos pos = entry.getKey();
                for (EntityPlayerMP player : FMLCommonHandler.instance().getMinecraftServerInstance().getPlayerList().getPlayers()) {
                    if (player.dimension == pos.dim && isPlayerInChunkRange(pos, player)) {
                        byte send = 0;
                        if (entry.getValue().update.equals(player.getUniqueID()))
                            send = 1;
                        else if (!entry.getValue().update.equals(IWardStorageServer.NIL_UUID))
                            send = 2;
                        
                        TANetwork.INSTANCE.sendTo(new PacketWardUpdate(entry.getValue().pos, send), player);
                    }
                }
            }
            
            WardSyncManager.clearEntries();
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTrackChunk(ChunkWatchEvent.Watch event) {
        if (event.getChunkInstance() != null && event.getChunkInstance().hasCapability(CapabilityWardStorage.WARD_STORAGE, null) && 
                event.getChunkInstance().getCapability(CapabilityWardStorage.WARD_STORAGE, null) instanceof IWardStorageServer) {
            IWardStorageServer storage = (IWardStorageServer) event.getChunkInstance().getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            NBTTagCompound sync = storage.fullSyncToClient(event.getChunkInstance(), event.getPlayer().getUniqueID());
            if (sync != null)
                TANetwork.INSTANCE.sendTo(new PacketFullWardSync(sync), event.getPlayer());
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (!event.world.isRemote && event.phase == Phase.START && event.world instanceof WorldServer) {
            WorldServer world = (WorldServer) event.world;
            try {
                Iterator<NextTickListEntry> iterator = world.pendingTickListEntriesHashSet.iterator();
                while (iterator.hasNext()) {
                    NextTickListEntry entry = iterator.next();
                    Chunk chunk = world.getChunk(entry.position);
                    if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
                        IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
                        if (storage.hasWard(entry.position)) {
                            iterator.remove();
                            world.pendingTickListEntriesTreeSet.remove(entry);
                        }
                    }
                }
            }
            catch (ConcurrentModificationException ex) {} // this happened to me once and was never reproduced again :(
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockPos pos = event.getPos();
        Chunk chunk = event.getWorld().getChunk(pos);
        if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
            IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            if (storage.hasWard(pos)) { 
                if (!WardHelper.doesPlayerHaveSpecialPermission(event.getPlayer()))
                    event.setCanceled(true);
                else if (storage instanceof IWardStorageServer)
                    ((IWardStorageServer) storage).clearWard(event.getWorld(), pos);
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockPunch(PlayerInteractEvent.LeftClickBlock event) {
        BlockPos pos = event.getPos();
        Chunk chunk = event.getWorld().getChunk(pos);
        if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
            IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            if (storage.hasWard(pos) && !WardHelper.doesPlayerHaveSpecialPermission(event.getEntityPlayer())) {
                EntityPlayer player = event.getEntityPlayer();
                RayTraceResult ray = player.getEntityWorld().rayTraceBlocks(player.getPositionEyes(1.0F), player.getLookVec().scale(
                        player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue()).add(new Vec3d(pos)), false, false, true);
                sendWardParticles(event.getEntityPlayer().getEntityWorld(), pos, ray.sideHit);
                event.setCanceled(true);
            }
            else if (event.getWorld().getBlockState(pos).getBlock() instanceof IWardParticles && !WardHelper.doesPlayerHaveSpecialPermission(event.getEntityPlayer())) {
                EntityPlayer player = event.getEntityPlayer();
                IBlockState state = event.getWorld().getBlockState(pos);
                if (((IWardParticles) state.getBlock()).shouldAddWardParticles(event.getWorld(), pos, state, player)) {
                    RayTraceResult ray = player.getEntityWorld().rayTraceBlocks(player.getPositionEyes(1.0F), player.getLookVec().scale(
                            player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue()).add(new Vec3d(pos)), false, false, true);
                    sendWardParticles(event.getEntityPlayer().getEntityWorld(), pos, ray.sideHit);
                    if (((IWardParticles) state.getBlock()).shouldCancelEventAndContinueParticles(event.getWorld(), pos, state, player)) 
                        event.setCanceled(true);
                }
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockInteract(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        Chunk chunk = event.getWorld().getChunk(pos);
        if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
            IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            if (storage.hasWard(pos) && !WardHelper.doesPlayerHaveSpecialPermission(event.getEntityPlayer()))
                event.setUseBlock(Result.DENY);
        }
    }
    
    protected static void doAllTheNotifications(World world, BlockPos pos, EnumSet<EnumFacing> notify) {
        for (EnumFacing facing : notify)
            world.neighborChanged(pos.offset(facing), world.getBlockState(pos.offset(facing)).getBlock(), pos);
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.getWorld().isBlockLoaded(event.getPos()) &&
                event.getWorld().isChunkGeneratedAt(event.getPos().getX() >> 4, event.getPos().getZ() >> 4)) {
            BlockPos notifier = event.getPos();
            EnumSet<EnumFacing> sidesToRemove = EnumSet.noneOf(EnumFacing.class);
            for (EnumFacing facing : event.getNotifiedSides()) {
                BlockPos pos = notifier.offset(facing);
                if (event.getWorld().isChunkGeneratedAt(pos.getX() >> 4, pos.getZ() >> 4) && !event.getWorld().isAirBlock(pos)) {
                    Chunk chunk = event.getWorld().getChunk(pos);
                    if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
                        IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
                        // sticky pistons are broken while warded and don't set the piston head to air, relying on the head to do it instead
                        // then again one can say this entire function is broken, so...
                        if (storage.hasWard(pos)) {// && event.getWorld().getBlockState(pos).getBlock() != Blocks.PISTON_HEAD) {
                            event.setCanceled(true);
                            sidesToRemove.add(facing);
                        }
                    }
                }
            }
            if (!sidesToRemove.isEmpty())
                doAllTheNotifications(event.getWorld(), notifier, EnumSet.complementOf(sidesToRemove));
            
            if (event.getWorld().isAirBlock(event.getPos()) || event.getWorld().getChunk(event.getPos()).getTileEntity(
                    event.getPos(), EnumCreateEntityType.CHECK) != null) {
                Chunk chunk = event.getWorld().getChunk(event.getPos());
                if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
                    IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
                    if (storage instanceof IWardStorageServer && storage.hasWard(event.getPos()))
                        ((IWardStorageServer) storage).clearWard(event.getWorld(), event.getPos());
                }
            }
        }
    }
    
    @SuppressWarnings("deprecation") // used for compat with older forge
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!(event instanceof BlockEvent.MultiPlaceEvent)) {
            BlockPos pos = event.getPos();
            Chunk chunk = event.getWorld().getChunk(pos);
            if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
                IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
                if (storage instanceof IWardStorageServer && storage.hasWard(event.getPos()))
                    ((IWardStorageServer) storage).clearWard(event.getWorld(), event.getPos());
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBlockPlaceMulti(BlockEvent.MultiPlaceEvent event) {
        for (BlockSnapshot b : event.getReplacedBlockSnapshots()) {
            BlockPos pos = b.getPos();
            Chunk chunk = event.getWorld().getChunk(pos);
            if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
                IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
                if (storage.hasWard(pos)) {
                    if (!(event.getEntity() instanceof EntityPlayer) || !WardHelper.doesPlayerHaveSpecialPermission((EntityPlayer) event.getEntity())) {
                        event.setCanceled(true);
                        return;
                    }
                    else if (storage instanceof IWardStorageServer)
                        ((IWardStorageServer) storage).clearWard(event.getWorld(), pos);
                }
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onDestruction(LivingDestroyBlockEvent event) {
        BlockPos pos = event.getPos();
        Chunk chunk = event.getEntity().getEntityWorld().getChunk(pos);
        if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
            IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            if (storage.hasWard(pos)) {
                if (!(event.getEntity() instanceof EntityPlayer) || !WardHelper.doesPlayerHaveSpecialPermission((EntityPlayer) event.getEntity()))
                    event.setCanceled(true);
                else if (storage instanceof IWardStorageServer)
                    ((IWardStorageServer) storage).clearWard(event.getEntity().getEntityWorld(), pos);
            }
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onBonemeal(BonemealEvent event) {
        BlockPos pos = event.getPos();
        Chunk chunk = event.getWorld().getChunk(pos);
        if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
            IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            if (storage.hasWard(pos) && !WardHelper.doesPlayerHaveSpecialPermission(event.getEntityPlayer()))
                event.setCanceled(true);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onHoe(UseHoeEvent event) {
        BlockPos pos = event.getPos();
        Chunk chunk = event.getWorld().getChunk(pos);
        if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
            IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            if (storage.hasWard(pos) && !WardHelper.doesPlayerHaveSpecialPermission(event.getEntityPlayer()))
                event.setCanceled(true);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onTrample(FarmlandTrampleEvent event) {
        BlockPos pos = event.getPos();
        Chunk chunk = event.getWorld().getChunk(pos);
        if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
            IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            if (storage.hasWard(pos) && (!(event.getEntity() instanceof EntityPlayer) || !WardHelper.doesPlayerHaveSpecialPermission((EntityPlayer) event.getEntity())))
                event.setCanceled(true);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onFluidPlaceBlock(FluidPlaceBlockEvent event) {
        BlockPos pos = event.getPos();
        Chunk chunk = event.getWorld().getChunk(pos);
        if (chunk.hasCapability(CapabilityWardStorage.WARD_STORAGE, null)) {
            IWardStorage storage = chunk.getCapability(CapabilityWardStorage.WARD_STORAGE, null);
            if (storage.hasWard(pos))
                event.setCanceled(true);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDewardBlock(BlockWardEvent.DewardedServer.Post event) {
        event.getWorld().neighborChanged(event.getPos(), event.getWorld().getBlockState(event.getPos().up()).getBlock(),
                event.getPos().up());
        event.getWorld().scheduleUpdate(event.getPos(), event.getWorld().getBlockState(event.getPos()).getBlock(),
                event.getWorld().getBlockState(event.getPos()).getBlock().tickRate(event.getWorld()));
    }
    
}
