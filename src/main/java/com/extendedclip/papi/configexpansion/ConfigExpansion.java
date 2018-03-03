/*
 *
 * Config-Expansion
 * Copyright (C) 2018 Ryan McCarthy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 *
 */
package com.extendedclip.papi.configexpansion;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.Cacheable;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class ConfigExpansion extends PlaceholderExpansion implements Configurable, Cacheable {

	private final Map<String, CachedConfig> configs = new HashMap<String, CachedConfig>();

	@Override
	public boolean canRegister() {
		return true;
	}

	@Override
	public boolean register() {
		load();
		return super.register();
	}

	@Override
	public void clear() {
		configs.clear();
	}

	@Override
	public String getIdentifier() {
		return "config";
	}

	@Override
	public String getPlugin() {
		return null;
	}

	@Override
	public String getAuthor() {
		return "clip";
	}

	@Override
	public String getVersion() {
		return "1.0.1";
	}

	@Override
	public String onPlaceholderRequest(Player p, String identifier) {
		
		int index = identifier.indexOf("_");
		
		if (index == -1) {
			return null;
		}
		
		String fileId = identifier.substring(0, index);
		String pathId = identifier.substring(index + 1);
		
		if (!configs.containsKey(fileId)) {
			return null;
		}

		CachedConfig c = configs.get(fileId);

		if (c == null) {
			return null;
		}

		String path = c.getPaths().get(pathId);

		if (path == null) {
			return null;
		}

		if (PlaceholderAPI.containsPlaceholders(path)) {
			path = PlaceholderAPI.setPlaceholders(p, path);
		}

		String value = c.getConfig().get(path).toString();
		return value == null ? "" : value;
	}

	@Override
	public Map<String, Object> getDefaults() {
		if (this.getConfigSection("configs") == null) {
			Map<String, Object> def = new HashMap<String, Object>();
			def.put("configs.example.file_path", "/plugins/PlaceholderAPI/");
			def.put("configs.example.file_name", "config.yml");
			def.put("configs.example.paths.cloud_enabled", "cloud_enabled");
			def.put("configs.example.paths.boolean_true", "boolean.true");
			return def;	
		}
		return null;
	}

	private void load() {
		
		if (this.getConfigSection("configs") == null) {
			return;
		}
		
		log(Level.INFO, "Loading...");
		int count = 0;
		
		for (String fileId : this.getConfigSection("configs").getKeys(false)) {
			
			if (fileId == null || fileId.isEmpty()) {
				log(Level.INFO, "No configs set!");
				continue;
			}

			String filePath = this.getString("configs." + fileId + ".file_path", null);
			String fileName = this.getString("configs." + fileId + ".file_name", null);
			
			if (filePath == null || fileName == null) {
				continue;
			}
			
			Set<String> paths = this.getConfigSection("configs." + fileId + ".paths").getKeys(false);
			
			if (paths == null || paths.isEmpty()) {
				continue;
			}
			
			Map<String, String> pathMap = new HashMap<String, String>();
			
			for (String name : paths) {
				
				String path = this.getString("configs." + fileId + ".paths." + name, null);
				
				if (path == null || path.isEmpty()) {
					continue;
				}
				
				count = count + 1;
				pathMap.put(name, path);
			}
			
			if (pathMap.isEmpty()) {
				log(Level.WARNING, "No config paths defined for config:" + fileName + ".");
				continue;
			}
			
			CachedConfig c = loadConfig(filePath, fileName, pathMap);
			
			if (c == null) {
				log(Level.WARNING, "Failed to cache config paths for:" + fileName + ".");
				continue;
			}
			
			configs.put(fileId, c);
		}
		
		log(Level.INFO, count + " config path" + (count != 1 ? "s" : "") + " cached.");
	}

	private CachedConfig loadConfig(String filePath, String fileName, Map<String, String> paths) {

		if (!fileName.endsWith(".yml")) {
			log(Level.WARNING, "Filename specified " + fileName + " is not a .yml file!");
			return null;
		}

		File dir = new File("." + File.separator + filePath);

		if (!dir.exists()) {
			log(Level.WARNING, "Directory for fileName:" + fileName + " does not exist!");
			return null;
		}

		File f = new File("." + File.separator + filePath, fileName);

		if (!f.exists()) {
			log(Level.WARNING, dir.getPath() + fileName + " does not exist!");
			return null;
		}

		FileConfiguration cfg = YamlConfiguration.loadConfiguration(f);

		if (cfg == null) {
			log(Level.WARNING, "Failed to retrieve configuration object from " + fileName + "!");
			return null;
		}

		return new CachedConfig(cfg).setPaths(paths);
	}

	private void log(Level lvl, String message) {
		this.getPlaceholderAPI().getLogger().log(lvl, "[Expansion-Config] " + message);
	}

	public class CachedConfig {

		private FileConfiguration c;
		private Map<String, String> paths;

		public CachedConfig(FileConfiguration c) {
			this.setConfig(c);
		}

		public Map<String, String> getPaths() {
			return paths;
		}

		public CachedConfig setPaths(Map<String, String> paths) {
			this.paths = paths;
			return this;
		}

		public FileConfiguration getConfig() {
			return c;
		}

		public CachedConfig setConfig(FileConfiguration c) {
			this.c = c;
			return this;
		}
	}
}
