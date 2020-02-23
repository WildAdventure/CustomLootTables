/*
 * Copyright (c) 2020, Wild Adventure
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived from
 *    this software without specific prior written permission.
 * 4. Redistribution of this software in source or binary forms shall be free
 *    of all charges or fees to the recipient of this software.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.gmail.filoghost.customloottables;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_12_R1.CraftWorld;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.common.cache.LoadingCache;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import net.minecraft.server.v1_12_R1.ChatDeserializer;
import net.minecraft.server.v1_12_R1.LootTable;
import net.minecraft.server.v1_12_R1.LootTableRegistry;
import net.minecraft.server.v1_12_R1.MinecraftKey;

public class CustomLootTables extends JavaPlugin implements Listener {

	
	private Config config;
	private Field gsonParserField;
	private Field cacheField;
	
	private Map<MinecraftKey, LootTable> customLootTables;
	
	
	@Override
	public void onEnable() {
		try {
			config = new Config(this, "config.yml");
			config.init();
		} catch (Exception e) {
			getLogger().log(Level.SEVERE, "Cannot load config.yml", e);
			this.setEnabled(false);
			return;
		}
		
		try {
			gsonParserField = LootTableRegistry.class.getDeclaredField("b");
			gsonParserField.setAccessible(true);
			cacheField = LootTableRegistry.class.getDeclaredField("c");
			cacheField.setAccessible(true);
		} catch (Exception e) {
			getLogger().log(Level.SEVERE, "Cannot use reflection", e);
			this.setEnabled(false);
			return;
		}
		
		getLogger().info("Found custom tables config entries:");
		config.customLootTables.forEach((key, file) -> getLogger().info(key + " -> " + file));

		// Load the custom loot tables
		customLootTables = new HashMap<>();
		
		config.customLootTables.forEach((minecraftKey, lootTableFileName) -> {
			LootTable lootTable = loadLootTable(lootTableFileName);
			MinecraftKey key = new MinecraftKey("minecraft", minecraftKey);
			customLootTables.put(key, lootTable);
		});
		
		
		Bukkit.getPluginManager().registerEvents(this, this);
		
		for (World world : Bukkit.getWorlds()) {
			replaceDefaultLootTables(world);
		}
	}
	

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onWorldInit(WorldLoadEvent event) {
		replaceDefaultLootTables(event.getWorld());
	}
	

	private LootTable loadLootTable(String lootTableFileName) {
		File file = new File(getDataFolder(), lootTableFileName);
		if (!file.isFile()) {
			getLogger().log(Level.SEVERE, "Couldn't find custom loot table file: " + lootTableFileName);
			return LootTable.a;
		}
		
		try {
			String json = Files.toString(file, StandardCharsets.UTF_8);
			return (LootTable) ChatDeserializer.a((Gson) gsonParserField.get(null), json, LootTable.class);
		} catch (JsonParseException | IllegalArgumentException e) {
			getLogger().log(Level.SEVERE, "Couldn't parse loot table from " + lootTableFileName, e);
			return LootTable.a;
		} catch (IOException e) {
			getLogger().log(Level.SEVERE, "Couldn't load loot table from " + lootTableFileName, e);
			return LootTable.a;
		} catch (IllegalAccessException e) {
			getLogger().log(Level.SEVERE, "Couldn't access parser field", e);
			return LootTable.a;
		}
	}

	
	private void replaceDefaultLootTables(World world) {
		try {
			LootTableRegistry lootTableRegistry = getLootTableRegistry(world);
			LoadingCache<MinecraftKey, LootTable> cache = getLootTableRegistryCache(lootTableRegistry);
			customLootTables.forEach(cache::put);
			getLogger().info("Applied custom loot tables to world " + world.getName());
		} catch (Exception e) {
			getLogger().log(Level.SEVERE, "Couldn't replace default loot tables in world " + world.getName(), e);
		}
	}
	
	
	private LootTableRegistry getLootTableRegistry(World world) {
		return ((CraftWorld) world).getHandle().getLootTableRegistry();
	}
	
	
	@SuppressWarnings("unchecked")
	private LoadingCache<MinecraftKey, LootTable> getLootTableRegistryCache(LootTableRegistry lootTableRegistry) throws Exception {
		return (LoadingCache<MinecraftKey, LootTable>) cacheField.get(lootTableRegistry);
	}


}
