package net.zephyrstudios.discordbot.discord;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.events.Event;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
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
import org.jetbrains.annotations.Nullable;
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
						boolean isRequired = parameter.getType().isPrimitive() ||
								parameter.isAnnotationPresent(Nullable.class) ||
								parameter.isAnnotationPresent(org.springframework.lang.Nullable.class) ||
								parameter.isAnnotationPresent(jakarta.annotation.Nullable.class) ||
								parameter.isAnnotationPresent(io.micrometer.common.lang.Nullable.class);

						OptionType type;

						if (option != null) {
							// Registeres the option based on type
							if (parameter.getType() == String.class) {
								type = OptionType.STRING;
							} else if (parameter.getType() == Integer.class || parameter.getType() == int.class) {
								type = OptionType.INTEGER;
							} else if (parameter.getType() == Boolean.class || parameter.getType() == boolean.class) {
								type = OptionType.BOOLEAN;
							} else if (parameter.getType() == Float.class || parameter.getType() == float.class ||
									parameter.getType() == Double.class || parameter.getType() == double.class ||
									parameter.getType() == Long.class || parameter.getType() == long.class) {
								type = OptionType.NUMBER;
							} else if (parameter.getType() == Member.class || parameter.getType() == User.class) {
								type = OptionType.USER;
							} else if (parameter.getType() == Channel.class) {
								type = OptionType.CHANNEL;
							} else if (parameter.getType() == IMentionable.class) {
								type = OptionType.MENTIONABLE;
							} else if (parameter.getType() == Message.Attachment.class) {
								type = OptionType.ATTACHMENT;
							} else {
								throw new IllegalArgumentException(String.format("Option of type '%s' is not supported", parameter.getType().getName()));
							}

							OptionData optionData = new OptionData(type, option.name(), option.description(), isRequired);
							data.addOptions(optionData);
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
				OptionMapping optionMapping;
				if (option != null && (optionMapping = event.getOption(option.name())) != null) {
					parsedOptions.add(switch (optionMapping.getType()) {
						case STRING -> optionMapping.getAsString();
						case INTEGER -> optionMapping.getAsInt();
						case BOOLEAN -> optionMapping.getAsBoolean();
						case NUMBER -> {
							if (parameter.getType() == Float.class || parameter.getType() == float.class) {
								yield (float) optionMapping.getAsDouble();
							} else if (parameter.getType() == Double.class || parameter.getType() == double.class) {
								yield optionMapping.getAsDouble();
							} else if (parameter.getType() == Long.class || parameter.getType() == long.class) {
								yield optionMapping.getAsLong();
							}
							throw new IllegalArgumentException("There was an error parsing the number");
						}
						case USER -> optionMapping.getAsUser();
						case CHANNEL -> optionMapping.getAsChannel();
						case MENTIONABLE -> optionMapping.getAsMentionable();
						case ATTACHMENT -> optionMapping.getAsAttachment();
						default -> throw new IllegalArgumentException("Unsupported option type");
					});
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


