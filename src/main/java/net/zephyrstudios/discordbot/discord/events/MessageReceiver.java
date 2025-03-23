package net.zephyrstudios.discordbot.discord.events;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.zephyrstudios.discordbot.discord.api.events.annotation.SubscribeEvent;
import net.zephyrstudios.discordbot.discord.api.events.annotation.EventListener;

@EventListener
public class MessageReceiver {
	@SubscribeEvent
	public void onMessageReceived(MessageReceivedEvent event) {
	}
}
