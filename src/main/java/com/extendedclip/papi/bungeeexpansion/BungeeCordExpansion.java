/*
 *
 * Bungee-Expansion
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
package com.extendedclip.papi.bungeeexpansion;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import me.clip.placeholderapi.expansion.Cacheable;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

public class BungeeCordExpansion extends PlaceholderExpansion implements PluginMessageListener, Taskable, Cacheable, Configurable {

	private final Map<String, Integer> servers = new ConcurrentHashMap<>();
	
	private int total = 0;
	
	private int count = 0;
	
	private BukkitTask task;
	
	private boolean registered = false;
	
	private final String CHANNEL = "BungeeCord";
	
	public BungeeCordExpansion() {
		if (!registered) {
			Bukkit.getMessenger().registerOutgoingPluginChannel(getPlaceholderAPI(), CHANNEL);
			Bukkit.getMessenger().registerIncomingPluginChannel(getPlaceholderAPI(), CHANNEL, this);
			registered = true;
		}	
	}
	
	@Override
	public boolean canRegister() {
		return true;
	}

	@Override
	public String getAuthor() {
		return "clip";
	}

	@Override
	public String getIdentifier() {
		return "bungee";
	}

	@Override
	public String getPlugin() {
		return null;
	}

	@Override
	public String getVersion() {
		return "1.0.1";
	}
	
	@Override
	public Map<String, Object> getDefaults() {
		final Map<String, Object> defaults = new HashMap<>();
		defaults.put("check_interval", 30);
		return defaults;
	}

	
	private void getServers() {
		
		if (Bukkit.getOnlinePlayers().isEmpty()) {
			return;
		}
		
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF("GetServers");
		Bukkit.getOnlinePlayers().iterator().next().sendPluginMessage(getPlaceholderAPI(), CHANNEL, out.toByteArray());
	}
	
	private void getPlayers(String server) {
		
		if (Bukkit.getOnlinePlayers().isEmpty()) {
			return;
		}
		
		ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF("PlayerCount");
		out.writeUTF(server);
		Bukkit.getOnlinePlayers().iterator().next().sendPluginMessage(getPlaceholderAPI(), CHANNEL, out.toByteArray());
	}

	@Override
	public void onPluginMessageReceived(String channel, Player player, byte[] message) {

		if (!channel.equals(CHANNEL)) {
			return;
		}
		
		DataInputStream in = new DataInputStream(new ByteArrayInputStream(message));
		 
		try {
			
			String subChannel = in.readUTF();
			 
			if (subChannel.equals("PlayerCount")) {
				 
				String server = in.readUTF();				
				
				if (in.available() > 0) {
					
					int count = in.readInt();
					
					if (server.equals("ALL")) {
						total = count;
					} else {
						servers.put(server, count);	
					}
				}
				
				
			} else if (subChannel.equals("GetServers")) {
				
				String[] serverList = in.readUTF().split(", ");
				
				if (serverList.length == 0) {
					return;
				}
				
				for (String server : serverList) {					
					servers.putIfAbsent(server, 0);
				}
			}
		 
		} catch (IOException e) {
			//IGNORE FUCK IT!! Testing
		}
	}

	@Override
	public String onPlaceholderRequest(Player p, String identifier) {

		
		if (identifier.equalsIgnoreCase("total") || identifier.equalsIgnoreCase("all")) {
			return String.valueOf(total);
		}
		
		if (servers.isEmpty()) {
			return "0";
		}
		
		for (Entry<String, Integer> entry : servers.entrySet()) {
			if (entry.getKey().equalsIgnoreCase(identifier)) {
				return String.valueOf(entry.getValue());
			}
		}
	
		return "0";
	
	}

	@Override
	public void start() {

		task = new BukkitRunnable() {

			@Override
			public void run() {
				
				if (servers.isEmpty()) {
					getServers();
					getPlayers("ALL");
					return;
				}
				
				for (String server : servers.keySet()) {
					getPlayers(server);
				}
				
				getPlayers("ALL");
				count++;
				
				if (count == 10) {
					count = 0;
					Bukkit.getScheduler().runTaskLater(getPlaceholderAPI(), new Runnable() {

						@Override
						public void run() {
							getServers();
						}
						
					}, 5L);
				}
				
			}
		}.runTaskTimer(getPlaceholderAPI(), 100L, 20L * getInt("check_interval", 30));
	}

	@Override
	public void stop() {
		if (task != null) {
			try {
				task.cancel();
			} catch (Exception ex) {	
			}
			task = null;
		}
	}

	@Override
	public void clear() {
		servers.clear();
		if (registered) {
			Bukkit.getMessenger().unregisterOutgoingPluginChannel(getPlaceholderAPI(), CHANNEL);
			Bukkit.getMessenger().unregisterIncomingPluginChannel(getPlaceholderAPI(), CHANNEL, this);
			registered = false;
		}
	}
}
