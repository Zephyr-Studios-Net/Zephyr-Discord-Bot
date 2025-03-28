package net.zephyrstudios.discordbot.discord.api.commands;

import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public record CommandObject(SlashCommandData data, Method method, Object instance) {
	public void invoke(Object... args) throws InvocationTargetException, IllegalAccessException {
		method.invoke(instance, args);
	}
}
