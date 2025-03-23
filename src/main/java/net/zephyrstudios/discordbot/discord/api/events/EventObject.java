package net.zephyrstudios.discordbot.discord.api.events;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public record EventObject(Method method, Object instance) {
	public void invoke(Object... args) throws InvocationTargetException, IllegalAccessException {
		method.invoke(instance, args);
	}
}
