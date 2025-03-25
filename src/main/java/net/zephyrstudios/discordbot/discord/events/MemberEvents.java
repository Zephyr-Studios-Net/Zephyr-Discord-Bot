package net.zephyrstudios.discordbot.discord.events;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.zephyrstudios.discordbot.discord.api.events.annotation.EventListener;
import net.zephyrstudios.discordbot.discord.api.events.annotation.EventListenerRegister;

@EventListenerRegister
public class MemberEvents {
	@EventListener
	public void onMemberJoin(GuildMemberJoinEvent event) {
		Guild guild = event.getGuild();
		guild.addRoleToMember(event.getMember(), guild.getRolesByName("Member", true).getFirst()).queue();
	}
}
