package net.zephyrstudios.discordbot.discord.api.commands;

import lombok.Getter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public record CommandObject(SlashCommandData data, Method commandMethod, Object commandClassInstance) {
	public void invoke(Object... args) throws InvocationTargetException, IllegalAccessException {
		commandMethod.invoke(commandClassInstance, args);
	}
}
