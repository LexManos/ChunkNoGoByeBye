/*
 * ChunkNoGoByeBye
 * Copyright (c) 2019.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package net.minecraftforge.lex.cngbb;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.nbt.INBT;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.server.ServerWorld;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ObjectHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(ChunkNoGoByeBye.MODID)
public class ChunkNoGoByeBye {
    public static final String MODID = "chunknogobyebye";
    public static final String LOADERID = "loader";

    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogManager.getLogger();
    private static List<Supplier<? extends Item>> ITEMS = new ArrayList<>();
    private static List<Supplier<? extends Block>> BLOCKS = new ArrayList<>();

    @CapabilityInject(IChunkLoaderList.class)
    public static Capability<IChunkLoaderList> CAPABILITY = null;

    @ObjectHolder(MODID + ":" + LOADERID)
    public static final Block LOADER_BLOCK = registerBlock(LOADERID, () -> new LoaderBlock(Block.Properties.create(Material.ROCK).hardnessAndResistance(3.5F)));
    @ObjectHolder(MODID + ":" + LOADERID)
    public static final Item LOADER_ITEM = registerItem(LOADERID, () -> new BlockItem(LOADER_BLOCK, new Item.Properties().group(ItemGroup.MISC)));

    public ChunkNoGoByeBye() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::setupClient);
        FMLJavaModLoadingContext.get().getModEventBus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
    private void setupClient(final FMLClientSetupEvent event) {}

    private void setup(final FMLCommonSetupEvent event){
        CapabilityManager.INSTANCE.register(IChunkLoaderList.class, new ChunkLoaderList.Storage(), () -> new ChunkLoaderList(null));
    }

    @SubscribeEvent
    public void attachWorldCaps(AttachCapabilitiesEvent<World> event) {
        if (event.getObject().isRemote) return;
        final LazyOptional<IChunkLoaderList> inst = LazyOptional.of(() -> new ChunkLoaderList((ServerWorld)event.getObject()));
        final ICapabilitySerializable<INBT> provider = new ICapabilitySerializable<INBT>() {
            @Override
            public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
                return CAPABILITY.orEmpty(cap, inst);
            }

            @Override
            public INBT serializeNBT() {
                return CAPABILITY.writeNBT(inst.orElse(null), null);
            }

            @Override
            public void deserializeNBT(INBT nbt) {
                CAPABILITY.readNBT(inst.orElse(null), null, nbt);
            }
        };
        event.addCapability(new ResourceLocation(MODID, LOADERID), provider);
        event.addListener(() -> inst.invalidate());
    }

    /*
     * Helper functions, because I feel like trying out this coding style.
     * We gather things to register in the static initalizer. But since we are using Suppliers,
     * the objects arn't actually run and this can be repeated/executed in the register events as they should be
     */
    private static <T extends Item> T registerItem(final String name, final Supplier<T> sup) {
        ITEMS.add(() -> sup.get().setRegistryName(MODID, name));
        return null;
    }
    private static <T extends Block> T registerBlock(final String name, final Supplier<T> sup) {
        BLOCKS.add(() -> sup.get().setRegistryName(MODID, name));
        return null;
    }
    @SubscribeEvent
    public void registerItems(final RegistryEvent.Register<Item> event) {
        ITEMS.stream().map(Supplier::get).forEach(event.getRegistry()::register);
    }
    @SubscribeEvent
    public void registerBlocks(final RegistryEvent.Register<Block> event) {
        BLOCKS.stream().map(Supplier::get).forEach(event.getRegistry()::register);
    }
}
