package de.MCmoderSD.commands;

import com.fasterxml.jackson.databind.JsonNode;

import de.MCmoderSD.core.BotClient;
import de.MCmoderSD.core.MessageHandler;
import de.MCmoderSD.objects.AudioFile;
import de.MCmoderSD.objects.TwitchMessageEvent;
import de.MCmoderSD.utilities.OpenAI.OpenAI;
import de.MCmoderSD.utilities.OpenAI.modules.Speech;
import de.MCmoderSD.utilities.database.MySQL;

import java.util.ArrayList;

public class TTS {

    // Constructor
    public TTS(BotClient botClient, MessageHandler messageHandler, MySQL mySQL, OpenAI openAI) {

        // Syntax
        String syntax = "Syntax: " + botClient.getPrefix() + "prompt <Frage>";

        // About
        String[] name = {"tts", "texttospeech"}; // Command name and aliases
        String description = "Lässt den YEPPBot Sprechen. " + syntax;

        // Get TTS Module and Config
        Speech speech = openAI.getSpeech();
        JsonNode config = speech.getConfig();

        // Get Parameters
        String voice = config.get("voice").asText();
        String format = config.get("format").asText();
        double speed = config.get("speed").asDouble();

        // Register command
        messageHandler.addCommand(new Command(description, name) {

            @Override
            public void execute(TwitchMessageEvent event, ArrayList<String> args) {

                // Check Admin
                if (!botClient.isPermitted(event)) return;

                // Get Voice if available
                String currentVoice = voice;
                for (String arg : args) if (speech.getModel().checkVoice(arg.toLowerCase())) currentVoice = arg;

                String message = event.getMessage();

                AudioFile audioFile = mySQL.getAssetManager().getTTSAudio(message);
                if (audioFile == null) audioFile = speech.tts(event.getMessage(), currentVoice, format, speed);

                // Send Audio
                botClient.sendAudio(event, audioFile);
            }
        });
    }
}