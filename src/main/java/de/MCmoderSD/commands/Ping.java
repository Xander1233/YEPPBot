package de.MCmoderSD.commands;

import de.MCmoderSD.commands.blueprints.Command;
import de.MCmoderSD.core.BotClient;
import de.MCmoderSD.core.MessageHandler;
import de.MCmoderSD.objects.TwitchMessageEvent;

import java.util.ArrayList;

public class Ping {

    // Constructor
    public Ping(BotClient botClient, MessageHandler messageHandler) {

        // About
        String[] name = {"ping", "latency"};
        String description = "Sendet eine Nachricht mit der Latenz des Bots zurück.";


        // Register command
        messageHandler.addCommand(new Command(description, name) {

            @Override
            public void execute(TwitchMessageEvent event, ArrayList<String> args) {

                // Send Message
                botClient.respond(event, getCommand(), "Pong " + botClient.getChat().getLatency() + "ms");
            }
        });
    }
}