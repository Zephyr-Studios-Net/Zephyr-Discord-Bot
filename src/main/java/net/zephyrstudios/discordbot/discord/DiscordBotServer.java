package net.zephyrstudios.discordbot.discord;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.zephyrstudios.discordbot.discord.api.commands.CommandObject;
import net.zephyrstudios.discordbot.discord.api.commands.annotation.Command;
import net.zephyrstudios.discordbot.discord.api.commands.annotation.CommandRegister;
import net.zephyrstudios.discordbot.discord.api.commands.annotation.Option;
import net.zephyrstudios.discordbot.discord.api.events.EventObject;
import net.zephyrstudios.discordbot.discord.api.events.annotation.EventListener;
import net.zephyrstudios.discordbot.discord.api.events.annotation.EventListenerRegister;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("ResultOfMethodCallIgnored")
@Service
@Slf4j
@RequiredArgsConstructor
public class DiscordBotServer extends ListenerAdapter {
	private final ApplicationContext context;

	@Value("${api.discord.token}")
	private String token;

	private final Map<String, CommandObject> registeredCommands = new ConcurrentHashMap<>();
	private final Map<Class<? extends Event>, EventObject> registeredEvents = new ConcurrentHashMap<>();

	@PostConstruct
	@Async
	protected void init() {
		JDABuilder builder = JDABuilder.createDefault(token);
		builder.addEventListeners(this);
		JDA jda = builder.build();

		// Register commands
		CommandListUpdateAction commands = jda.updateCommands();
		int successfulCommandRegistrations = 0;
		int failedCommandRegistrations = 0;

		for (Object commandRegisterObj : context.getBeansWithAnnotation(CommandRegister.class).values()) {
			List<Method> commandMethods = Arrays.stream(commandRegisterObj.getClass().getMethods())
					.filter(method -> method.getAnnotation(Command.class) != null).toList();
			for (Method commandMethod : commandMethods) {
				Command annotation = commandMethod.getAnnotation(Command.class);

				SlashCommandData data = Commands.slash(annotation.name(), annotation.description());
				data.setContexts(InteractionContextType.GUILD);

				try {
					// Registers the paramters as options for the command
					for (Parameter parameter : commandMethod.getParameters()) {
						Option option = parameter.getAnnotation(Option.class);

						if (option != null) {
							if (parameter.getType() == String.class) {
								OptionData optionData = new OptionData(OptionType.STRING, option.name(), option.description(), true);
								data.addOptions(optionData);
							} else {
								throw new IllegalArgumentException(String.format("Option of type '%s' is not supported", parameter.getType().getName()));
							}
						}
					}

					registeredCommands.put(data.getName(), new CommandObject(data, commandMethod, commandRegisterObj));
					commands.addCommands(data);
					successfulCommandRegistrations++;
				} catch (IllegalArgumentException e) {
					log.error("Failed to register command {}", annotation.name(), e);
					failedCommandRegistrations++;
				}
			}
		}
		log.info("Registered {} commands successfully, {} failed", successfulCommandRegistrations, failedCommandRegistrations);
		commands.queue();

		// Register Events
		int successfulEventRegistrations = 0;
		int failedEventRegistrations = 0;

		for (Object eventObj : context.getBeansWithAnnotation(EventListenerRegister.class).values()) {
			for (Method method : eventObj.getClass().getMethods()) {
				try {
					if (method.getAnnotation(EventListener.class) == null) continue;

					if (method.getParameterCount() != 1)
						throw new IllegalArgumentException(String.format("Event listener '%s' Expected 1 parameter, found %d", method.getName(), method.getParameterCount()));
					Parameter parameter = method.getParameters()[0];
					Class<?> parameterType = parameter.getType();
					if (!Event.class.isAssignableFrom(parameterType))
						throw new IllegalArgumentException(String.format("Event parameter on '%s' is not an event", method.getName()));

					registeredEvents.put(parameterType.asSubclass(Event.class), new EventObject(method, eventObj));
					successfulEventRegistrations++;
				} catch (IllegalArgumentException e) {
					log.error("Failed to register event listener {}", method.getName(), e);
					failedEventRegistrations++;
				}
			}
		}
		log.info("Registered {} event listeners successfully, {} failed", successfulEventRegistrations, failedEventRegistrations);
	}

	@Override
	public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
		if (event.getGuild() == null) return; // Cancels usage outside of guilds

		CommandObject command = registeredCommands.get(event.getName());
		if (command == null) {
			event.reply("This command isn't supported").queue();
			return;
		}

		try {
			List<Object> parsedOptions = new ArrayList<>();
			for (Parameter parameter : command.method().getParameters()) {
				Option option = parameter.getAnnotation(Option.class);
				if (option != null) {
					parsedOptions.add(event.getOption(option.name()).getAsString());
					continue;
				}

				if (parameter.getType() == SlashCommandInteractionEvent.class) {
					parsedOptions.add(event);
				}
			}
			command.invoke(parsedOptions.toArray());
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onGenericEvent(GenericEvent event) {
		EventObject eventObject = registeredEvents.get(event.getClass());
		if (eventObject == null) return;
		try {
			eventObject.invoke(event);
		} catch (InvocationTargetException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
	}
}


