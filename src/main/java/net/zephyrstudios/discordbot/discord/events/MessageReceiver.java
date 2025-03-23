package net.zephyrstudios.discordbot.discord.events;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.zephyrstudios.discordbot.discord.api.events.annotation.EventListener;
import net.zephyrstudios.discordbot.discord.api.events.annotation.EventListenerRegister;

@EventListenerRegister
public class MessageReceiver {
	@EventListener
	public void onMessageReceived(MessageReceivedEvent event) {
	}
}
