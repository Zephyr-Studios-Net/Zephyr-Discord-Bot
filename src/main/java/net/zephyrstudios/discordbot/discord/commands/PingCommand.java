package net.zephyrstudios.discordbot.discord.commands;

import net.zephyrstudios.discordbot.discord.api.commands.annotation.Command;
import net.zephyrstudios.discordbot.discord.api.commands.annotation.CommandRegister;

@CommandRegister
public class PingCommand {
	@Command(name = "ping", description = "Play Ping, Pong with the bot")
	public void execute() {
	}
}
