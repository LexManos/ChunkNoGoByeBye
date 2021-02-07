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

import static net.minecraftforge.lex.cngbb.ChunkNoGoByeBye.LOADER_BLOCK;
import static net.minecraftforge.lex.cngbb.ChunkNoGoByeBye.MODID;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.datafixers.util.Pair;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DirectoryCache;
import net.minecraft.data.IDataProvider;
import net.minecraft.data.IFinishedRecipe;
import net.minecraft.data.LootTableProvider;
import net.minecraft.data.RecipeProvider;
import net.minecraft.data.ShapedRecipeBuilder;
import net.minecraft.data.loot.BlockLootTables;
import net.minecraft.item.Items;
import net.minecraft.loot.LootParameterSet;
import net.minecraft.loot.LootParameterSets;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.ValidationTracker;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public class DataCreator {
    @SuppressWarnings("unused")
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().disableHtmlEscaping().create();

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator gen = event.getGenerator();

        if (event.includeServer()) {
            gen.addProvider(new Recipes(gen));
            gen.addProvider(new Loots(gen));
        }
        if (event.includeClient()) {
            //TODO: Generate models when Forge/Someone makes model data generators
            gen.addProvider(new Language(gen, MODID));
        }
    }

    private static void save(DirectoryCache cache, Object object, Path target) throws IOException {
        String data = GSON.toJson(object);
        String hash = IDataProvider.SHA1.hashUnencodedChars(data).toString();
        if (!Objects.equals(cache.getHash(target), hash) || !Files.exists(target)) {
           Files.createDirectories(target.getParent());

           try (BufferedWriter bufferedwriter = Files.newBufferedWriter(target)) {
              bufferedwriter.write(data);
           }
        }

        cache.putNew(target, hash);
    }

    private static class Recipes extends RecipeProvider {
        public Recipes(DataGenerator gen) {
            super(gen);
        }

        @Override
        protected void buildShapelessRecipes(Consumer<IFinishedRecipe> consumer) {
            ShapedRecipeBuilder.shaped(LOADER_BLOCK.get(), 10)
                .define('O', Items.ENDER_PEARL).define('E', Blocks.ENCHANTING_TABLE)
                .pattern("OOO").pattern("OEO").pattern("OOO")
                .unlockedBy("has_ender_pearl", has(Items.ENDER_PEARL))
                .save(consumer);
        }
    }

    private static class Loots extends LootTableProvider {
        public Loots(DataGenerator gen) {
            super(gen);
        }

        @Override
        public String getName() {
            return "LootTables";
        }

        @Override
        protected List<Pair<Supplier<Consumer<BiConsumer<ResourceLocation, LootTable.Builder>>>, LootParameterSet>> getTables() {
           return ImmutableList.of(Pair.of(Blocks::new, LootParameterSets.BLOCK));
        }

        @Override
        protected void validate(Map<ResourceLocation, LootTable> map, ValidationTracker tracker) {} //We don't do validation

        private class Blocks extends BlockLootTables {
            private Set<Block> knownBlocks = new HashSet<>();

            protected void addTables() {
                this.dropSelf(LOADER_BLOCK.get());
            }

            @Override
            public void dropSelf(Block block) {
                knownBlocks.add(block);
                super.dropSelf(block);
            }

            @Override
            protected Iterable<Block> getKnownBlocks() {
                return this.knownBlocks;
            }
        }
    }

    private static class Language implements IDataProvider {
        private final DataGenerator gen;
        private final String modid;
        private final Map<String, String> data = new TreeMap<>();

        public Language(DataGenerator gen, String modid) {
            this.gen = gen;
            this.modid = modid;
        }

        private void addTranslations() {
            add(LOADER_BLOCK.get(), "Single Chunk Loader");
        }

        @Override
        public String getName() {
            return "Languages";
        }

        @Override
        public void run(DirectoryCache cache) throws IOException {
            addTranslations();

            if (!data.isEmpty())
                save(cache, data, this.gen.getOutputFolder().resolve("assets/" + modid + "/lang/en_us.json"));
        }

        private void add(Block block, String name) {
            add(block.getDescriptionId(), name);
        }

        private void add(String key, String value) {
            if (data.put(key, value) != null)
                throw new IllegalStateException("Duplicate translation key " + key);
        }
    }
}
