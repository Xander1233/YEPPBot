package de.MCmoderSD.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.philippheuer.credentialmanager.CredentialManager;
import com.github.philippheuer.credentialmanager.CredentialManagerBuilder;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.philippheuer.events4j.core.EventManager;
import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.TwitchClientHelper;
import com.github.twitch4j.chat.TwitchChat;
import com.github.twitch4j.helix.TwitchHelix;

import de.MCmoderSD.OpenAI.OpenAI;
import de.MCmoderSD.OpenAI.modules.Chat;
import de.MCmoderSD.commands.Birthday;
import de.MCmoderSD.commands.Conversation;
import de.MCmoderSD.commands.Counter;
import de.MCmoderSD.commands.CustomCommand;
import de.MCmoderSD.commands.CustomTimers;
import de.MCmoderSD.commands.DickDestroyDecember;
import de.MCmoderSD.commands.Fact;
import de.MCmoderSD.commands.Gif;
import de.MCmoderSD.commands.Help;
import de.MCmoderSD.commands.Horoscope;
import de.MCmoderSD.commands.Info;
import de.MCmoderSD.commands.Insult;
import de.MCmoderSD.commands.Joke;
import de.MCmoderSD.commands.Join;
import de.MCmoderSD.commands.Lurk;
import de.MCmoderSD.commands.Match;
import de.MCmoderSD.commands.Moderate;
import de.MCmoderSD.commands.NoNutNovember;
import de.MCmoderSD.commands.Ping;
import de.MCmoderSD.commands.Play;
import de.MCmoderSD.commands.Prompt;
import de.MCmoderSD.commands.Quote;
import de.MCmoderSD.commands.Say;
import de.MCmoderSD.commands.Status;
import de.MCmoderSD.commands.TTS;
import de.MCmoderSD.commands.Translate;
import de.MCmoderSD.commands.Weather;
import de.MCmoderSD.commands.Whitelist;
import de.MCmoderSD.commands.Wiki;

import de.MCmoderSD.UI.Frame;
import de.MCmoderSD.main.Credentials;
import de.MCmoderSD.main.Main;
import de.MCmoderSD.main.Terminal;
import de.MCmoderSD.objects.TwitchMessageEvent;
import de.MCmoderSD.utilities.database.MySQL;
import de.MCmoderSD.utilities.other.Format;
import de.MCmoderSD.utilities.server.AudioBroadcast;
import de.MCmoderSD.utilities.server.Server;

import de.MCmoderSD.JavaAudioLibrary.AudioFile;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.stream.Collectors;

import static de.MCmoderSD.main.Terminal.Argument.*;
import static de.MCmoderSD.utilities.other.Format.*;

@SuppressWarnings("unused")
public class BotClient {

    public static final String PROVIDER = "twitch";
    public static final long RATE_LIMIT = 250L;

    // Associations
    private final MySQL mySQL;
    private final Frame frame;
    private final Server server;
    private final AudioBroadcast audioBroadcast;

    // Constants
    public static String botName;
    public static String[] botNames;
    public static String prefix;
    public static String [] prefixes;
    public static HashSet<String> admins;
    public static HelixHandler.Scope[] requiredScopes = {
            HelixHandler.Scope.USER_READ_EMAIL,
            HelixHandler.Scope.USER_READ_BLOCKED_USERS,
            HelixHandler.Scope.CHAT_READ,
            HelixHandler.Scope.BITS_READ,
            HelixHandler.Scope.CHANNEL_READ_EDITORS,
            HelixHandler.Scope.MODERATOR_READ_FOLLOWERS,
            HelixHandler.Scope.MODERATION_READ,
            HelixHandler.Scope.CHANNEL_READ_VIPS,
            HelixHandler.Scope.CHANNEL_READ_SUBSCRIPTIONS,
            HelixHandler.Scope.CHANNEL_MANAGE_RAIDS,
    };

    // Credentials
    private final JsonNode botConfig;
    private final Integer botId;
    private final String botToken;
    private final String clientId;
    private final String clientSecret;
    private final OAuth2Credential defaultAuthToken;
    private final CredentialManager credentialManager;

    // Client Modules
    private final TwitchClient client;
    private final TwitchClientHelper helper;
    private final TwitchChat chat;
    private final TwitchHelix helix;
    private final EventManager eventManager;

    // Handler
    private final EventHandler eventHandler;
    private final HelixHandler helixHandler;
    private final MessageHandler messageHandler;

    // Attributes
    private boolean cli;
    private boolean log;

    // Constructor
    public BotClient(Credentials credentials, MySQL mySQL, @Nullable Frame frame, @Nullable OpenAI openAI) {

        // Init Associations
        this.mySQL = mySQL;
        this.frame = frame;

        // Init Bot Credentials
        botConfig = credentials.getBotConfig();
        botId = botConfig.get("botId").asInt();
        botToken = botConfig.get("botToken").asText();
        clientId = botConfig.get("clientId").asText();
        clientSecret = botConfig.get("clientSecret").asText();

        // Init Credential Manager
        defaultAuthToken = new OAuth2Credential(PROVIDER, botToken);
        credentialManager = CredentialManagerBuilder.builder().build();

        // Init Bot Settings
        botNames = botConfig.get("botName").asText().toLowerCase().split(", ");
        botName = botNames[0];
        prefixes = botConfig.get("prefix").asText().split(" ");
        prefix = prefixes[0];
        admins = new HashSet<>(Arrays.asList(botConfig.get("admins").asText().toLowerCase().split("; ")));

        // Init Attributes
        cli = Main.terminal.hasArg(CLI);
        log = Main.terminal.hasArg(NO_LOG);

        // Init HTTPS Server
        JsonNode httpsServerConfig = credentials.getHttpsServerConfig();
        if (!Main.terminal.hasArg(DEV) && httpsServerConfig.get("hostname").asText().contains(".")) server = new Server(this, httpsServerConfig); // Default
        else { // Custom or Dev Mode
            String hostname = Main.terminal.hasArg(HOST) ? Main.terminal.getArgs()[0] : httpsServerConfig.get("hostname").asText();
            int port = Main.terminal.hasArg(PORT) ? Integer.parseInt(Main.terminal.getArgs()[1]) : httpsServerConfig.get("port").asInt();
            server = new Server(this, hostname, port, httpsServerConfig.get("keystore").asText(), botConfig);
        }

        // Init Client
        client = TwitchClientBuilder.builder()
                .withBotOwnerId(botId.toString())
                .withClientId(clientId)
                .withClientSecret(clientSecret)
                .withCredentialManager(credentialManager)
                .withDefaultAuthToken(defaultAuthToken)
                .withChatAccount(defaultAuthToken)
                .withChatCommandsViaHelix(true)
                .withEnableHelix(true)
                .withEnableChat(true)
                .build();

        // Init Modules
        chat = client.getChat();
        helper = client.getClientHelper();
        helix = client.getHelix();
        eventManager = client.getEventManager();

        // Init Helix Handler
        helixHandler = new HelixHandler(this, mySQL, server);

        // Init Audio Broadcast
        audioBroadcast = new AudioBroadcast(server);

        // Init Handlers
        messageHandler = new MessageHandler(this, mySQL);
        eventHandler = new EventHandler(this, frame, mySQL.getLogManager(), eventManager, messageHandler);

        // Check Modules
        boolean astrology = credentials.hasAstrology();
        boolean weather = credentials.hasOpenWeatherMap();
        boolean giphy = credentials.hasGiphy();
        boolean openAIChat = credentials.validateOpenAIChatConfig();
        boolean openAIImage = credentials.validateOpenAIImageConfig();
        boolean openAITTS = credentials.validateOpenAITTSConfig();

        // Loading Standard Commands
        new Join(this, messageHandler);
        new Ping(this, messageHandler);
        new Play(this, messageHandler);
        new Say(this, messageHandler);
        new Status(this, messageHandler);

        // Loading Database Commands
        new Counter(this, messageHandler, mySQL);
        new CustomCommand(this, messageHandler, mySQL);
        new CustomTimers(this, messageHandler, mySQL);
        new Fact(this, messageHandler, mySQL);
        new Help(this, messageHandler, mySQL);
        new Insult(this, messageHandler, mySQL);
        new Joke(this, messageHandler, mySQL);
        new Lurk(this, messageHandler, mySQL);
        new Quote(this, messageHandler, mySQL);
        new Whitelist(this, messageHandler, mySQL);

        // Loading Twitch API Commands
        new Birthday(this, messageHandler, mySQL, helixHandler);
        new DickDestroyDecember(this, messageHandler, mySQL, helixHandler);
        new Info(this, messageHandler, helixHandler);
        new Moderate(this, messageHandler, mySQL, helixHandler);
        new NoNutNovember(this, messageHandler, mySQL, helixHandler);

        // Loading OpenAI Chat Commands
        if (openAIChat) {
            Chat chatModule = Objects.requireNonNull(openAI).getChat();
            new Conversation(this, messageHandler, chatModule);
            new Match(this, messageHandler, mySQL, helixHandler, chatModule);
            new Translate(this, messageHandler, chatModule);
            new Prompt(this, messageHandler, chatModule);
            new Wiki(this, messageHandler, chatModule);
        }

        // Loading OpenAI TTS Commands
        if (openAITTS) new TTS(this, messageHandler, mySQL, Objects.requireNonNull(openAI).getSpeech());

        // API & OpenAI Commands
        if (giphy || astrology || weather) {
            JsonNode apiConfig = credentials.getAPIConfig();
            if (giphy) new Gif(this, messageHandler, apiConfig);
            if (openAIChat && astrology) new Horoscope(this, messageHandler, mySQL, helixHandler, openAI.getChat(), apiConfig);
            if (openAIChat && weather) new Weather(this, messageHandler, openAI.getChat(), apiConfig);
        }

        // Show UI
        setUI(!cli);

        // Print Startup Complete
        System.out.printf("Start-Up Compleate! Took: %dms%s%s%s", (System.nanoTime() - Terminal.startTime) / 1_000_000, UNBOLD, BREAK, BREAK);

        // Join Channels
        HashSet<String> channelList = new HashSet<>();
        if (credentials.validateChannelList()) channelList.addAll(credentials.getChannelList());
        if (!Main.terminal.hasArg(DEV)) channelList.addAll(mySQL.getChannelManager().getActiveChannels());
        channelList = (HashSet<String>) channelList.stream().map(String::toLowerCase).collect(Collectors.toSet());
        channelList.remove(botName);
        joinChannel(channelList);
    }

    // UI
    private void setUI(boolean visible) {
        if (frame == null || !visible) return;
        frame.setVisible(true);
        frame.requestFocusInWindow();
    }

    // Credential
    public void addCredential(String accessToken) {
        credentialManager.addCredential(PROVIDER, new OAuth2Credential(PROVIDER, accessToken));
    }

    // Write
    public void write(String channel, String message) {

        // Check Message
        if (message.isEmpty() || message.isBlank()) return;

        // Update Frame
        if (!cli) frame.log(USER, channel, botName, message);

        // Log
        if (log) mySQL.getLogManager().logResponse(channel, botName, message, helixHandler);
        System.out.printf("%s %s <%s> %s: %s%s", Format.getFormattedTimestamp(), USER, channel, botName, message, BREAK);

        // Send Message
        chat.sendMessage(channel, message);
    }

    // Respond
    public void respond(TwitchMessageEvent event, String command, String message) {

        // Variables
        var channel = event.getChannel();

        // Update Frame
        if (!(message.isEmpty() || message.isBlank()) && !cli) frame.log(RESPONSE, channel, botName, message);

        // Log
        if (log) mySQL.getLogManager().logResponse(event, command, message);
        System.out.printf("%s%s %s <%s> Executed: %s%s%s", BOLD, Format.getFormattedTimestamp(), COMMAND, channel, command + ": " + event.getMessage(), BREAK, UNBOLD);
        if (!(message.isEmpty() || message.isBlank())) System.out.printf("%s%s %s <%s> %s: %s%s%s", BOLD, Format.getFormattedTimestamp(), RESPONSE, channel, botName, message, UNBOLD, BREAK);

        // Send Message
        if (!(message.isEmpty() || message.isBlank())) chat.sendMessage(channel, message);
    }

    // Send Audio
    public void sendAudio(TwitchMessageEvent event, AudioFile audioFile) {

        // Log
        mySQL.getLogManager().logTTS(event, audioFile);

        // Play Audio
        audioBroadcast.play(event.getChannel(), audioFile);

        // Update Frame
        if (!cli) audioFile.play();
    }

    // Join Channel
    public void joinChannel(String channel) {

        // Format Channel
        channel = channel.toLowerCase();

        // Check Channel
        if (channel.isEmpty() || channel.isBlank()) return;
        if (channel.length() < 3 || channel.length() > 25) return;
        if (channel.contains(" ") || chat.isChannelJoined(channel) || botName.equals(channel)) return;

        // Get Channel ID
        var id = helixHandler.getUser(channel).getId();

        // Log
        System.out.printf("%s%s %s Joined Channel: %s%s%s", BOLD, Format.getFormattedTimestamp(), SYSTEM, channel.toLowerCase(), BREAK, UNBOLD);

        // Join Channel
        chat.joinChannel(channel);

        // Enable Additional Event Listeners
        helper.enableStreamEventListener(channel);
        if (helixHandler.checkScope(id, HelixHandler.Scope.MODERATOR_READ_FOLLOWERS)) helper.enableFollowEventListener(channel);    // moderator:read:followers
        helper.enableClipEventListener(channel);

        // Register Broadcast
        audioBroadcast.registerBroadcast(channel);
    }

    // Bulk Join
    public void joinChannel(HashSet<String> channels) {
        new Thread(() -> {
            try {
                for (String channel : channels) {
                    joinChannel(channel);
                    Thread.sleep(RATE_LIMIT); // Prevent rate limit
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }

    // Leave Channel
    public void leaveChannel(String channel) {

        // Format Channel
        channel = channel.toLowerCase();

        // Check Channel
        if (channel.isEmpty() || channel.isBlank()) return;
        if (channel.length() < 3 || channel.length() > 25) return;
        if (channel.contains(" ") || !chat.isChannelJoined(channel) || botName.equals(channel)) return;

        // Leave Channel
        if (chat.leaveChannel(channel)) System.out.printf("%s%s %s Left Channel: %s%s%s", BOLD, Format.getFormattedTimestamp(), SYSTEM, channel.toLowerCase(), BREAK, UNBOLD);
        else System.out.printf("%s%s %s Failed to Leave Channel: %s%s%s", BOLD, Format.getFormattedTimestamp(), SYSTEM, channel.toLowerCase(), BREAK, UNBOLD);

        // Unregister Broadcast
        if (audioBroadcast.unregisterBroadcast(channel)) System.out.printf("%s%s %s Unregistered Broadcast: %s%s%s", BOLD, Format.getFormattedTimestamp(), SYSTEM, channel.toLowerCase(), BREAK, UNBOLD);
        else System.out.printf("%s%s %s Failed to Unregister Broadcast: %s%s%s", BOLD, Format.getFormattedTimestamp(), SYSTEM, channel.toLowerCase(), BREAK, UNBOLD);
    }

    // Bulk Leave
    public void leaveChannel(HashSet<String> channels) {
        new Thread(() -> {
            try {
                for (String channel : channels) {
                    leaveChannel(channel);
                    Thread.sleep(RATE_LIMIT); // Prevent rate limit
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }).start();
    }


    // Controls
    public void connectChat() {
        chat.connect();
    }

    public void reconnectChat() {
        chat.reconnect();
    }

    public void disconnectChat() {
        chat.disconnect();
    }

    public void closeChat() {
        chat.close();
    }

    public void close() {
        client.close();
    }

    public void setCli(boolean cli) {
        this.cli = cli;
    }

    public void setLog(boolean log) {
        this.log = log;
    }

    // Checker
    public boolean isAdmin(TwitchMessageEvent event) {
        return admins.contains(event.getUser());
    }

    public boolean isPermitted(TwitchMessageEvent event) {
        return isModerator(event) || isBroadcaster(event);
    }

    public boolean isBroadcaster(TwitchMessageEvent event) {
        return event.getChannelId() == event.getUserId();
    }

    public boolean isModerator(TwitchMessageEvent event) {
        if (!helixHandler.checkScope(event.getChannelId(), HelixHandler.Scope.MODERATION_READ)) return false;
        else return helixHandler.isModerator(event.getChannelId(), event.getUserId());
    }

    public boolean isEditor(TwitchMessageEvent event) {
        if (!helixHandler.checkScope(event.getChannelId(), HelixHandler.Scope.CHANNEL_READ_EDITORS)) return false;
        HashSet<Integer> ids = new HashSet<>();
        helixHandler.getEditors(event.getChannelId()).forEach(twitchUser -> ids.add(twitchUser.getId()) );
        return ids.contains(event.getUserId());
    }

    public boolean isVIP(TwitchMessageEvent event) {
        if (!helixHandler.checkScope(event.getChannelId(), HelixHandler.Scope.CHANNEL_READ_VIPS)) return false;
        return helixHandler.isVIP(event.getChannelId(), event.getUserId());
    }

    public boolean isFollowing(TwitchMessageEvent event) {
        if (!helixHandler.checkScope(event.getChannelId(), HelixHandler.Scope.MODERATOR_READ_FOLLOWERS)) return false;
        return helixHandler.isFollower(event.getChannelId(), event.getUserId());
    }

    public boolean isInChat(String channel) {
        return chat.isChannelJoined(channel);
    }

    public boolean isLog() {
        return log;
    }

    public boolean isCli() {
        return cli;
    }

    // Getter
    public HashSet<String> getChannels() {
        return (HashSet<String>) chat.getChannels();
    }

    public long getLatency() {
        return chat.getLatency();
    }

    // Constants Getter
    public String getProvider() {
        return PROVIDER;
    }

    public String getBotName() {
        return botName;
    }

    public String[] getBotNames() {
        return botNames;
    }

    public String getPrefix() {
        return prefix;
    }

    public String[] getPrefixes() {
        return prefixes;
    }

    public HashSet<String> getAdmins() {
        return admins;
    }

    public HelixHandler.Scope[] getRequiredScopes() {
        return requiredScopes;
    }

    // Credentials Getter
    public JsonNode getConfig() {
        return botConfig;
    }

    public Integer getBotId() {
        return botId;
    }

    public String getBotToken() {
        return botToken;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public OAuth2Credential getDefaultAuthToken() {
        return defaultAuthToken;
    }

    public CredentialManager getCredentialManager() {
        return credentialManager;
    }

    // Module Getter
    public TwitchClient getClient() {
        return client;
    }

    public TwitchClientHelper getHelper() {
        return helper;
    }

    public TwitchChat getChat() {
        return chat;
    }

    public TwitchHelix getHelix() {
        return helix;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    // Handler Getter
    public EventHandler getEventHandler() {
        return eventHandler;
    }

    public HelixHandler getHelixHandler() {
        return helixHandler;
    }

    public MessageHandler getMessageHandler() {
        return messageHandler;
    }

    // Association Getter
    public MySQL getMySQL() {
        return mySQL;
    }

    public Frame getFrame() {
        return frame;
    }

    public Server getServer() {
        return server;
    }

    public AudioBroadcast getAudioBroadcast() {
        return audioBroadcast;
    }
}