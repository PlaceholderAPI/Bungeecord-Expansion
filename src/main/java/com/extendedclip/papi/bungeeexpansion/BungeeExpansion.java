package com.extendedclip.papi.bungeeexpansion;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import me.clip.placeholderapi.expansion.Configurable;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Taskable;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public final class BungeeExpansion extends PlaceholderExpansion implements PluginMessageListener, Taskable, Configurable {

	private static final String MESSAGE_CHANNEL = "BungeeCord";

	// BungeeCord Subchannels
	private static final String SERVER_SUBCHANNEL = "GetServer";
	private static final String SERVERS_SUBCHANNEL = "GetServers";
	private static final String PLAYERS_SUBCHANNEL = "PlayerCount";

	private static final String CONFIG_INTERVAL = "check_interval";
	private static final Splitter SPLITTER = Splitter.on(",").trimResults();

	private final Map<String, Integer> counts = new HashMap<>();
	private final AtomicReference<BukkitTask> cached = new AtomicReference<>();

	private String serverName = "";

	@Override
	public @NotNull String getIdentifier() {
		return "bungee";
	}

	@Override
	public @NotNull String getAuthor() {
		return "clip";
	}

	@Override
	public @NotNull String getVersion() {
		return "3.0";
	}

	@Override
	public Map<String, Object> getDefaults() {
		return Collections.singletonMap(CONFIG_INTERVAL, 30);
	}

	@Override
	public String onRequest(final OfflinePlayer player, @NotNull String identifier) {
		switch (identifier) {
			case "count":
				return Integer.toString(counts.getOrDefault(serverName, 0));

			case "server_name":
				return serverName.isEmpty() ? "" : serverName;
		}

		if (identifier.startsWith("count_")) {
			final int value;
			switch (identifier) {
				case "count_all":
				case "count_total":
					value = counts.values().stream().mapToInt(Integer::intValue).sum();
					break;

				default:
					String serverName = identifier.substring(identifier.indexOf('_') + 1);
					value = counts.getOrDefault(serverName, 0);
					break;
			}
			return Integer.toString(value);
		}
		return null;
	}

	@Override
	public void start() {
		final BukkitTask task = Bukkit.getScheduler().runTaskTimer(getPlaceholderAPI(), () -> {
			if (counts.isEmpty()) {
				sendServersSubchannelMessage();
			} else {
				counts.keySet().forEach(this::sendPlayersSubchannelMessage);
			}
		}, 20L * 2L, 20L * getLong(CONFIG_INTERVAL, 30));

		final BukkitTask prev = cached.getAndSet(task);
		if (prev != null) {
			prev.cancel();
		} else {
			Bukkit.getMessenger().registerOutgoingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL);
			Bukkit.getMessenger().registerIncomingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL, this);
		}

		sendServerSubchannelMessage();
	}

	@Override
	public void stop() {
		final BukkitTask prev = cached.getAndSet(null);
		if (prev == null) return;

		prev.cancel();
		counts.clear();

		Bukkit.getMessenger().unregisterOutgoingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL);
		Bukkit.getMessenger().unregisterIncomingPluginChannel(getPlaceholderAPI(), MESSAGE_CHANNEL, this);
	}

	@Override
	public void onPluginMessageReceived(final @NotNull String channel, final @NotNull Player player, final byte[] message) {
		if (!MESSAGE_CHANNEL.equals(channel)) return;

		//noinspection UnstableApiUsage
		final ByteArrayDataInput in = ByteStreams.newDataInput(message);
		switch (in.readUTF()) {
			case PLAYERS_SUBCHANNEL:
				counts.put(in.readUTF(), in.readInt());
				break;

			case SERVER_SUBCHANNEL:
				serverName = in.readUTF();
				break;

			case SERVERS_SUBCHANNEL:
				SPLITTER.split(in.readUTF()).forEach(serverName -> counts.putIfAbsent(serverName, 0));
				break;
		}
	}

	private void sendServerSubchannelMessage() {
		sendChanncelMessage(SERVER_SUBCHANNEL, null);
	}

	private void sendServersSubchannelMessage() {
		sendChanncelMessage(SERVERS_SUBCHANNEL, null);
	}

	private void sendPlayersSubchannelMessage(@NotNull final String serverName) {
		sendChanncelMessage(PLAYERS_SUBCHANNEL, out -> out.writeUTF(serverName));
	}

	private void sendChanncelMessage(@NotNull final String channel, @Nullable final Consumer<ByteArrayDataOutput> consumer) {
		final Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
		if (player == null) return;

		final ByteArrayDataOutput out = ByteStreams.newDataOutput();
		out.writeUTF(channel);

		if (consumer != null) consumer.accept(out);

		player.sendPluginMessage(getPlaceholderAPI(), MESSAGE_CHANNEL, out.toByteArray());
	}

}