package net.zephyrstudios.discordbot.discord.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.zephyrstudios.discordbot.discord.api.commands.annotation.Command;
import net.zephyrstudios.discordbot.discord.api.commands.annotation.CommandRegister;
import net.zephyrstudios.discordbot.discord.api.commands.annotation.Option;

@CommandRegister
public class HelloCommand {
	@Command(name = "hello", description = "Make the bot say hello")
	public void execute(
			@Option(name = "name", description = "Your Name") String name,
			SlashCommandInteractionEvent event
	) {
		event.reply("Hello " + name).queue();
	}
}
