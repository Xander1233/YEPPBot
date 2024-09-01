package de.MCmoderSD.core;

import de.MCmoderSD.UI.Frame;
import de.MCmoderSD.commands.Command;
import de.MCmoderSD.main.Main;
import de.MCmoderSD.objects.Timer;
import de.MCmoderSD.objects.TwitchMessageEvent;
import de.MCmoderSD.utilities.database.MySQL;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.HashMap;
import java.util.NoSuchElementException;
import java.util.ArrayList;

import static de.MCmoderSD.utilities.other.Calculate.*;

public class MessageHandler {

    // Associations
    private final BotClient botClient;
    private final MySQL mySQL;
    private final Frame frame;

    // Attributes
    private final HashMap<String, Command> commandList;
    private final HashMap<String, String> aliasMap;
    private final HashMap<Integer, Integer> lurkList;
    private final HashMap<Integer, HashSet<String>> blackList;
    private final HashMap<Integer, HashMap<String, String>> customCommands;
    private final HashMap<Integer, HashMap<String, String>> customAliases;
    private final HashMap<Integer, HashMap<String, Integer>> counters;
    private final HashMap<Integer, HashSet<Timer>> customTimers;

    // Constructor
    public MessageHandler(BotClient botClient, MySQL mySQL, Frame frame) {

        // Initialize Associations
        this.botClient = botClient;
        this.mySQL = mySQL;
        this.frame = frame;

        // Initialize Attributes
        commandList = new HashMap<>();
        aliasMap = new HashMap<>();
        lurkList = new HashMap<>();
        blackList = new HashMap<>();
        customCommands = new HashMap<>();
        customAliases = new HashMap<>();
        counters = new HashMap<>();
        customTimers = new HashMap<>();

        // Update Lists
        updateLurkList(mySQL.getLurkManager().getLurkList());
        updateBlackList(mySQL.getChannelManager().getBlackList());
        updateCustomCommands(mySQL.getCustomManager().getCustomCommands(), mySQL.getCustomManager().getCustomAliases());
        updateCounters(mySQL.getCustomManager().getCustomCounters());
        updateCustomTimers(mySQL.getCustomManager().getCustomTimers(botClient));
    }

    // Handle Twitch Message
    public void handleMessage(TwitchMessageEvent event) {
        new Thread(() -> {

            // Log Message
            mySQL.getLogManager().logMessage(event);
            event.logToConsole();
            if (!botClient.hasArg(Main.Argument.CLI))
                frame.log(event.getType(), event.getChannel(), event.getUser(), event.getMessage());

            // Handle Timers;
            handleTimers(event);

            // Check for Lurk
            if (lurkList.containsKey(event.getUserId())) handleLurk(event);

            // Check for Command
            if (event.hasCommand()) {
                handleCommand(event);
                return;
            }

            // Reply YEPP
            if (event.hasBotName()) {
                botClient.respond(event, "replyYEPP", tagUser(event) + " YEPP");
                return;
            }

            // Say YEPP
            if (event.hasYEPP()) botClient.respond(event, "YEPP", "YEPP");
        }).start();
    }

    private void handleTimers(TwitchMessageEvent event) {
        new Thread(() -> {
            if (customTimers.containsKey(event.getChannelId())) customTimers.get(event.getChannelId()).forEach(Timer::trigger);
        }).start();
    }

    private void handleLurk(TwitchMessageEvent event) {
        new Thread(() -> {

            // Variables
            var channelID = event.getChannelId();
            var userID = event.getUserId();
            String response;

            HashMap<Timestamp, ArrayList<Integer>> lurker;
            Timestamp start;
            ArrayList<Integer> lurkChannel;

            try {
                lurker = mySQL.getLurkManager().getLurkTime(userID);
                start = lurker.keySet().iterator().next();
                lurkChannel = lurker.get(start);
            } catch (NoSuchElementException e) {
                return;
            }

            if (channelID == lurkChannel.getFirst()) { // Stop lurking

                // Remove user from lurk list
                updateLurkList(mySQL.getLurkManager().removeLurker(userID));

                // Send message
                response = tagUser(event) + " war " + formatLurkTime(start) + " im Lurk!";
                botClient.respond(event, "stoppedLurk", response);

            } else if (!lurkChannel.contains(channelID)) { // Snitch on lurked channel

                // Add user to traitor list
                lurkChannel.add(channelID);
                StringBuilder traitors = new StringBuilder();
                for (var i = 1; i < lurkChannel.size(); i++) traitors.append(lurkChannel.get(i)).append("\t");
                mySQL.getLurkManager().addTraitor(userID, traitors.toString());

                // Send message
                if (lurkChannel.size() < 3) botClient.respond(new TwitchMessageEvent(
                        event.getTimestamp(),
                        lurkChannel.getFirst(),
                        event.getUserId(),
                        mySQL.queryName("channels", lurkChannel.getFirst()),
                        event.getUser(),
                        event.getMessage(),
                        event.getSubMonths(),
                        event.getSubStreak(),
                        event.getSubTier(),
                        event.getBits()
                ), "traitor", tagUser(event) + " ist ein verräter, hab den kek gerade im chat von " + tagChannel(event) + " gesehen!");
            }
        }).start();
    }

    private void handleCommand(TwitchMessageEvent event) {

        // Variables
        ArrayList<String> parts = formatCommand(event);
        String trigger = parts.getFirst().toLowerCase();

        // Check for Alias
        if (aliasMap.containsKey(trigger)) {
            trigger = aliasMap.get(trigger);
            parts.set(0, trigger);
        }

        // Check for Command
        if (commandList.containsKey(trigger)) {
            if (isBlackListed(event, trigger)) return;
            Command command = commandList.get(trigger);
            parts.removeFirst();

            // Log Command
            mySQL.getLogManager().logCommand(event, trigger, processArgs(parts));

            // Execute Command
            command.execute(event, parts);
            return;
        }

        // Check for Custom Command in channel
        var channelID = event.getChannelId();
        if (customCommands.containsKey(channelID)) {

            // Check for Alias
            if (customAliases.containsKey(channelID) && customAliases.get(channelID).containsKey(trigger)) {
                trigger = customAliases.get(channelID).get(trigger);
                parts.set(0, trigger);
            }

            // Check for Command
            if (customCommands.get(channelID).containsKey(trigger)) {

                // Check for Blacklist
                if (isBlackListed(event, trigger)) return;
                String response = formatCommand(event, parts, customCommands.get(channelID).get(trigger));
                parts.removeFirst();

                // Log Command
                mySQL.getLogManager().logCommand(event, trigger, processArgs(parts));

                // Execute Command
                botClient.respond(event, "Custom: " + trigger, response);
            }
        }

        // Check for Counter
        if (counters.containsKey(channelID)) {

            // Check for Counter
            if (counters.get(channelID).containsKey(trigger)) {

                var currentValue = counters.get(channelID).get(trigger);
                String response = mySQL.getCustomManager().editCounter(event, trigger, currentValue + 1);
                parts.removeFirst();

                // Log Command
                mySQL.getLogManager().logCommand(event, trigger, processArgs(parts));

                // Execute Command
                botClient.respond(event, "Counter: " + trigger, response);
            }
        }
    }

    // Register Command
    public void addCommand(Command command) {

        // Register command
        String name = command.getCommand().toLowerCase();
        commandList.put(name, command);

        // Register aliases
        for (String alias : command.getAlias()) aliasMap.put(alias.toLowerCase(), name);
    }

    // Update Lurk List
    public void updateLurkList(HashMap<Integer, Integer> lurkList) {
        this.lurkList.clear();
        this.lurkList.putAll(lurkList);
    }

    // Update Black List
    public void updateBlackList(HashMap<Integer, HashSet<String>> blackList) {
        this.blackList.clear();
        this.blackList.putAll(blackList);
    }

    // Update Custom Commands
    public void updateCustomCommands(HashMap<Integer, HashMap<String, String>> customCommands, HashMap<Integer, HashMap<String, String>> customAliases) {
        this.customCommands.clear();
        this.customCommands.putAll(customCommands);
        this.customAliases.clear();
        this.customAliases.putAll(customAliases);
    }

    // Update Counters
    public void updateCounters(HashMap<Integer, HashMap<String, Integer>> counters) {
        this.counters.clear();
        this.counters.putAll(counters);
    }

    // Update Custom Timers
    public void updateCustomTimers(HashMap<Integer, HashSet<Timer>> customTimers) {
        this.customTimers.clear();
        this.customTimers.putAll(customTimers);
    }

    public void updateCustomTimers(int channelID, HashSet<Timer> customTimers) {
        this.customTimers.replace(channelID, customTimers);
    }

    // Getter
    public boolean isBlackListed(TwitchMessageEvent event, String command) {
        if (!blackList.containsKey(event.getChannelId())) return false;
        else return blackList.get(event.getChannelId()).contains(command.toLowerCase());
    }

    public boolean checkLurk(TwitchMessageEvent event) {
        return lurkList.containsKey(event.getUserId());
    }

    public boolean checkCommand(String command) {
        return commandList.containsKey(command);
    }

    public boolean checkAlias(String alias) {
        return aliasMap.containsKey(alias);
    }

    public HashMap<String, Command> getCommandList() {
        return commandList;
    }

    public HashMap<String, String> getAliasMap() {
        return aliasMap;
    }
}