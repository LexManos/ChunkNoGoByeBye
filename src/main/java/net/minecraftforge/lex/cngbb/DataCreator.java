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
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.TriConsumer;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DirectoryCache;
import net.minecraft.data.IDataProvider;
import net.minecraft.data.IFinishedRecipe;
import net.minecraft.data.RecipeProvider;
import net.minecraft.data.ShapedRecipeBuilder;
import net.minecraft.data.loot.BlockLootTables;
import net.minecraft.item.Items;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.storage.loot.LootParameterSet;
import net.minecraft.world.storage.loot.LootParameterSets;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraft.world.storage.loot.LootTableManager;
import net.minecraft.world.storage.loot.LootTables;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.GatherDataEvent;

@EventBusSubscriber(modid = MODID, bus = Bus.MOD)
public class DataCreator {
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
        String hash = IDataProvider.HASH_FUNCTION.hashUnencodedChars(data).toString();
        if (!Objects.equals(cache.getPreviousHash(target), hash) || !Files.exists(target)) {
           Files.createDirectories(target.getParent());

           try (BufferedWriter bufferedwriter = Files.newBufferedWriter(target)) {
              bufferedwriter.write(data);
           }
        }

        cache.func_208316_a(target, hash);
    }

    private static class Recipes extends RecipeProvider {
        private final DataGenerator gen;
        private final Path ADV_ROOT;

        public Recipes(DataGenerator gen) {
            super(gen);
            this.gen = gen;
            ADV_ROOT = this.gen.getOutputFolder().resolve("data/minecraft/advancements/recipes/root.json");
        }

        @Override
        protected void saveRecipeAdvancement(DirectoryCache cache, JsonObject json, Path path) {
            if (path.equals(ADV_ROOT)) return; //We NEVER care about this.
            super.saveRecipeAdvancement(cache, json, path);
        }

        @Override
        protected void registerRecipes(Consumer<IFinishedRecipe> consumer) {
            ShapedRecipeBuilder.shapedRecipe(LOADER_BLOCK, 10)
                .key('O', Items.ENDER_PEARL).key('E', Blocks.ENCHANTING_TABLE)
                .patternLine("OOO").patternLine("OEO").patternLine("OOO")
                .addCriterion("has_ender_pearl", hasItem(Items.ENDER_PEARL))
                .build(consumer);
        }
    }

    private static class Loots implements IDataProvider {
        private final DataGenerator gen;

        public Loots(DataGenerator gen) {
            this.gen = gen;
        }


        @Override
        public String getName() {
            return "LootTables";
        }

        @Override
        public void act(DirectoryCache cache) {
            Map<ResourceLocation, LootTable> map = Maps.newHashMap();
            TriConsumer<LootParameterSet, ResourceLocation, LootTable.Builder> consumer = (set, key, builder) -> {
                if (map.put(key, builder.setParameterSet(set).build()) != null)
                    throw new IllegalStateException("Duplicate loot table " + key);
            };

            new Blocks().accept((key, builder) -> consumer.accept(LootParameterSets.BLOCK, key, builder));

            map.forEach((key, table) -> {
                Path target = this.gen.getOutputFolder().resolve("data/" + key.getNamespace() + "/loot_tables/" + key.getPath() + ".json");

                try {
                   IDataProvider.save(GSON, cache, LootTableManager.toJson(table), target);
                } catch (IOException ioexception) {
                   LOGGER.error("Couldn't save loot table {}", target, ioexception);
                }
            });
        }

        private class Blocks extends BlockLootTables {
            private Set<Block> knownBlocks = new HashSet<>();

            private void addTables() {
                this.func_218492_c(LOADER_BLOCK);
            }

            @Override
            public void accept(BiConsumer<ResourceLocation, LootTable.Builder> consumer) {
                this.addTables();

                Set<ResourceLocation> visited = Sets.newHashSet();

                for(Block block : knownBlocks) {
                   ResourceLocation tabke_name = block.getLootTable();
                   if (tabke_name != LootTables.EMPTY && visited.add(tabke_name)) {
                      LootTable.Builder builder = this.field_218581_i.remove(tabke_name);
                      if (builder == null)
                         throw new IllegalStateException(String.format("Missing loottable '%s' for '%s'", tabke_name, block.getRegistryName()));

                      consumer.accept(tabke_name, builder);
                   }
                }

                if (!this.field_218581_i.isEmpty())
                   throw new IllegalStateException("Created block loot tables for non-blocks: " + this.field_218581_i.keySet());
            }

            @Override
            public void func_218492_c(Block block) {
                knownBlocks.add(block);
                super.func_218492_c(block);
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
            add(LOADER_BLOCK, "Chunk Loader");
        }

        @Override
        public String getName() {
            return "Languages";
        }

        @Override
        public void act(DirectoryCache cache) throws IOException {
            addTranslations();

            if (!data.isEmpty())
                save(cache, data, this.gen.getOutputFolder().resolve("assets/" + modid + "/lang/en_us.json"));
        }

        private void add(Block block, String name) {
            add(block.getTranslationKey(), name);
        }

        private void add(String key, String value) {
            if (data.put(key, value) != null)
                throw new IllegalStateException("Duplicate translation key " + key);
        }
    }
}
