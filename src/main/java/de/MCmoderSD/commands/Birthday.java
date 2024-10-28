package de.MCmoderSD.commands;

import de.MCmoderSD.core.BotClient;
import de.MCmoderSD.core.HelixHandler;
import de.MCmoderSD.core.MessageHandler;
import de.MCmoderSD.objects.Birthdate;
import de.MCmoderSD.objects.TwitchMessageEvent;
import de.MCmoderSD.objects.TwitchUser;
import de.MCmoderSD.utilities.database.MySQL;

import javax.management.InvalidAttributeValueException;
import java.util.*;
import java.util.stream.Collectors;

import static de.MCmoderSD.objects.Birthdate.TIME_ZONE;
import static de.MCmoderSD.utilities.other.Calculate.*;

public class Birthday {

    // Constants
    private final String youHaveNoBirthdaySet;
    private final String yourBirthdayIsOn;
    private final String userHasNoBirthdaySet;
    private final String userHasBirthdayOn;

    private final String invalidDate;
    private final String ageRestriction;
    private final String birthWasSetOn;

    private final String nobodyHasBirthdayOn;
    private final String noSavedBirthdays;
    private final String theNextBirthday;
    private final String userOn;

    private final String followingUsersHaveBirthdayOn;
    private final String nobodyHasBirthdayToday;
    private final String followingUsersHaveBirthdayToday;
    private final String nobodyHasBirthdayThisWeek;
    private final String followingUsersHaveBirthdayThisWeek;
    private final String nobodyHasBirthdayThisMonth;
    private final String followingUsersHaveBirthdayThisMonth;
    private final String nobodyHasBirthdayThisYear;
    private final String followingUsersHaveBirthdayThisYear;

    // Association
    private final BotClient botClient;
    private final MySQL mySQL;
    private final HelixHandler helixHandler;

    // Attributes
    private final String syntax;
    private final String setSyntax;
    private final String getSyntax;
    private final String inSyntax;

    // Variables
    private LinkedHashMap<Integer, Birthdate> birthdays;

    // Constructor
    public Birthday(BotClient botClient, MessageHandler messageHandler, MySQL mySQL, HelixHandler helixHandler) {

        // Syntax
        syntax = "Syntax: " + botClient.getPrefix() + "birthday set/get/in/list/next";
        setSyntax = "Syntax: " + botClient.getPrefix() + "birthday set DD.MM.CCYY";
        getSyntax = "Syntax: " + botClient.getPrefix() + "birthday get [DD.MM | @user | Year | Month ]";
        inSyntax = "Syntax: " + botClient.getPrefix() + "birthday in <user> [ Months | Days | Hours ]";

        // About
        String[] name = {"birthday", "bday", "geburtstag", "bd", "geb", "gb"};
        String description = "Setzt deinen Geburtstag. " + syntax;

        // Constants
        userHasNoBirthdaySet = "User hat noch keinen Geburtstag gesetzt.";
        userHasBirthdayOn = "@%s hat am %s Geburtstag! YEPP";

        invalidDate = "Invalid Date: %s.%s.%s";
        ageRestriction = "Du musst mindestens 13 Jahre alt sein.";

        birthWasSetOn = "Dein Geburtstag wurde auf den %s gesetzt.";

        youHaveNoBirthdaySet = "Du hast noch keinen Geburtstag gesetzt.";
        yourBirthdayIsOn = "Dein Geburtstag ist am %s. YEPP";
        nobodyHasBirthdayOn = "Niemand hat am %s Geburtstag.";
        noSavedBirthdays = "Es gibt keine gespeicherten Geburtstage.";
        theNextBirthday = "Der nächste Geburtstag ist von @%s am %s. YEPP";
        followingUsersHaveBirthdayOn = "Folgende User haben am %s Geburtstag: %s. YEPP";
        userOn = "@%s am %s";
        nobodyHasBirthdayToday = "Niemand hat heute Geburtstag.";
        followingUsersHaveBirthdayToday = "Folgende User haben heute Geburtstag: %s. YEPP";
        nobodyHasBirthdayThisWeek = "Niemand hat diese Woche Geburtstag.";
        followingUsersHaveBirthdayThisWeek = "Folgende User haben diese Woche Geburtstag:";
        nobodyHasBirthdayThisMonth = "Niemand hat in diesem Monat Geburtstag.";
        followingUsersHaveBirthdayThisMonth = "Folgende User haben in diesem Monat Geburtstag:";
        nobodyHasBirthdayThisYear = "Niemand hat dieses Jahr Geburtstag.";
        followingUsersHaveBirthdayThisYear = "Folgende User haben dieses Jahr Geburtstag:";


        // Association
        this.botClient = botClient;
        this.mySQL = mySQL;
        this.helixHandler = helixHandler;


        // Register command
        messageHandler.addCommand(new Command(description, name) {

            @Override
            public void execute(TwitchMessageEvent event, ArrayList<String> args) {

                // Clean Args
                ArrayList<String> cleanArgs = cleanArgs(args);
                args.clear();
                args.addAll(cleanArgs);

                // Check Arguments
                if (args.isEmpty()) {
                    botClient.respond(event, getCommand(), syntax);
                    return;
                }


                // Get Birthdays
                HashMap<Integer, Birthdate> mySQLBirthdays = mySQL.getBirthdays();

                // Remove all non followers
                HashSet<TwitchUser> followers = new HashSet<>(helixHandler.getFollowers(event.getChannelId(), null));
                followers.add(new TwitchUser(event));                                       // Add User
                followers.add(new TwitchUser(event.getChannelId(), event.getChannel()));    // Add Broadcaster
                mySQLBirthdays.entrySet().removeIf(entry -> !containsTwitchUser(followers, entry.getKey()));

                birthdays = mySQLBirthdays.entrySet()
                        .stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.comparing(b -> b.getTimeUntilBirthday().getDays())))
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (e1, e2) -> e1,
                                LinkedHashMap::new
                        ));

                // Check Verb
                String verb = args.getFirst().toLowerCase();

                // Check for Tagged User
                if (verb.startsWith("@")) {

                    // Check if tagged user
                    String response;
                    TwitchUser taggedUser = helixHandler.getUser(args.getFirst().substring(1).toLowerCase());
                    if (!birthdays.containsKey(taggedUser.getId())) response = userHasNoBirthdaySet;
                    else response = String.format(userHasBirthdayOn, taggedUser.getName(), birthdays.get(taggedUser.getId()).getDate());

                    // Response
                    botClient.respond(event, getCommand(), response);
                    return;
                }

                switch (verb) {
                    case "set":
                        botClient.respond(event, getCommand(), setBirthday(event, args));
                        break;
                    case "next":
                        botClient.respond(event, getCommand(), nextBirthday(args));
                        break;
                    case "list":
                        botClient.respond(event, getCommand(), listBirthday(args));
                        break;
                    case "in":
                        botClient.respond(event, getCommand(), inBirthday(event, args));
                        break;
                    case "get":
                        botClient.respond(event, getCommand(), getBirthday(event, args));
                        break;
                    default:
                        botClient.respond(event, getCommand(), syntax);
                        break;
                }
            }
        });
    }

    private String setBirthday(TwitchMessageEvent event, ArrayList<String> args) {

        // Check Syntax
        if (args.size() != 2) return setSyntax;

        // Check Birthday
        String[] date = args.get(1).split("\\.");
        if (date.length != 3) return setSyntax;

        // Set Birthday
        Birthdate birthdate;
        try {
            birthdate = new Birthdate(args.get(1));
        } catch (InvalidAttributeValueException | NumberFormatException e) {
            return String.format(invalidDate, date[0], date[1], date[2]);
        }

        // Check Age (13+)
        if (birthdate.getAge() < 13) return ageRestriction;

        // Set Birthday
        mySQL.setBirthday(event, birthdate);
        botClient.getMessageHandler().updateBirthdateList(mySQL.getBirthdays());

        // Response
        return String.format(birthWasSetOn, birthdate.getDate());
    }

    private String nextBirthday(ArrayList<String> args) {

        // Variablen
        var next = 0;

        // Check Argument
        if (args.size() > 1) {
            try {
                next = Integer.parseInt(args.get(1));
            } catch (NumberFormatException e) {
                return "Invalid Argument: " + syntax;
            }
        }

        // Check if empty
        if (birthdays.isEmpty()) return noSavedBirthdays;

        // Get Next Birthday
        Birthdate nextBirthday = birthdays.get(next);
        String nextBirthdayUser = helixHandler.getUser(birthdays.entrySet().stream()
                .filter(entry -> entry.getValue().equals(nextBirthday))
                .findFirst()
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new RuntimeException("User not found"))).getName();

        // Response
        return String.format(theNextBirthday, nextBirthdayUser, nextBirthday.getDayMonth());
    }

    private String listBirthday(ArrayList<String> args) {

        // Variables
        var next = 10;

        // Check Argument
        if (args.size() > 1) {
            try {
                next = Integer.parseInt(args.get(1));
            } catch (NumberFormatException e) {
                return "Invalid Argument: " + syntax;
            }
        }

        // Check if empty
        if (birthdays.isEmpty()) return noSavedBirthdays;

        // Collect user IDs
        HashSet<Integer> userIds = new HashSet<>();
        for (var i = 0; i < Math.min(next, birthdays.size()); i++) {
            Birthdate birthdate = birthdays.get(i);
            Integer userId = birthdays.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(birthdate))
                    .findFirst()
                    .map(Map.Entry::getKey)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            userIds.add(userId);
        }

        // Fetch users by IDs
        HashSet<TwitchUser> users = helixHandler.getUsersByID(userIds);

        // Create a map for quick lookup
        Map<Integer, TwitchUser> userMap = new HashMap<>();
        for (TwitchUser user : users) userMap.put(user.getId(), user);

        // Build the response
        StringBuilder response = new StringBuilder();
        for (var i = 0; i < Math.min(next, birthdays.size()); i++) {
            Birthdate birthdate = birthdays.get(i);
            Integer userId = birthdays.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(birthdate))
                    .findFirst()
                    .map(Map.Entry::getKey)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            String username = userMap.get(userId).getName();
            response.append(String.format(userOn, username, birthdate.getDayMonth()));
            if (i < Math.min(next, birthdays.size()) - 1) response.append(", ");
        }
        return response.toString();
    }

    private String inBirthday(TwitchMessageEvent event, ArrayList<String> args) {

        // Variables
        boolean tagged = args.size() == 3;
        String taggedUser = null;

        // Check Args
        if (args.size() < 2) return inSyntax;
        if (tagged) taggedUser = args.get(1).startsWith("@") ? args.get(1).substring(1).toLowerCase() : args.get(1).toLowerCase();
        var id = tagged ? helixHandler.getUser(taggedUser).getId() : event.getUserId();

        // Check Birthday
        if (!birthdays.containsKey(id)) return tagged ? userHasNoBirthdaySet : youHaveNoBirthdaySet;

        // Get Birthday
        Birthdate birthdate = birthdays.get(id);
        return switch (args.get(tagged ? 2 : 1).toLowerCase()) {
            case "years", "jahre" -> String.format("Es sind %f Jahr%s bis zu %s Geburtstag.", birthdate.getYearsUntilBirthday(), birthdate.getYearsUntilBirthday() > 1 ? "e" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            case "months", "monate" -> String.format("Es sind %f Monat%s bis zu %s Geburtstag.", birthdate.getMonthsUntilBirthday(), birthdate.getMonthsUntilBirthday() > 1 ? "e" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            case "weeks", "wochen" -> String.format("Es sind %f Woche%s bis zu %s Geburtstag.", birthdate.getWeeksUntilBirthday(), birthdate.getWeeksUntilBirthday() > 1 ? "n" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            case "hours", "stunden", "h" -> String.format("Es sind %d Stunde%s bis zu %s Geburtstag.", birthdate.getHoursUntilBirthday(), birthdate.getHoursUntilBirthday() > 1 ? "n" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            case "days", "tage", "d" -> String.format("Es sind %s Tag%s bis zu %s Geburtstag.", birthdate.getDaysUntilBirthday(), birthdate.getDaysUntilBirthday() > 1 ? "e" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            case "seconds", "sekunden", "s" -> String.format("Es sind %d Sekunde%s bis zu %s Geburtstag.", birthdate.getSecondsUntilBirthday(), birthdate.getSecondsUntilBirthday() > 1 ? "n" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            case "milliseconds", "millisekunden", "ms" -> String.format("Es sind %d Millisekunde%s bis zu %s Geburtstag.", birthdate.getMillisecondsUntilBirthday(), birthdate.getMillisecondsUntilBirthday() > 1 ? "n" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            case "microseconds", "mikrosekunden", "µs" -> String.format("Es sind %d Mikrosekunde%s bis zu %s Geburtstag.", birthdate.getMicrosecondsUntilBirthday(), birthdate.getMicrosecondsUntilBirthday() > 1 ? "n" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            case "nanoseconds", "nanosekunden", "ns" -> String.format("Es sind %d Nanosekunde%s bis zu %s Geburtstag.", birthdate.getNanosecondsUntilBirthday(), birthdate.getNanosecondsUntilBirthday() > 1 ? "n" : "", tagged ? "@" + taggedUser + "'s" : "deinem");
            default -> "Invalid Argument: " + inSyntax;
        };
    }

    private String getBirthday(TwitchMessageEvent event, ArrayList<String> args) {

        // Check for Self
        if (args.size() == 1) {
            if (!birthdays.containsKey(event.getUserId())) return youHaveNoBirthdaySet;
            else return String.format(yourBirthdayIsOn, birthdays.get(event.getUserId()).getDate());
        }

        // Check for Date
        if (args.size() > 1 && args.get(1).split("\\.").length > 1) {
            try {
                // Search for Birthdate
                String[] searchDateParts = args.get(1).split("\\.");
                Birthdate searchBirthdate = new Birthdate(String.format("%s.%s.%s", searchDateParts[0], searchDateParts[1], searchDateParts.length == 3 ? searchDateParts[2] : "1990"));
                HashSet<Integer> searchBirthdays = new HashSet<>();
                for (Integer user: birthdays.keySet()) if (birthdays.get(user).getDayMonth().equals(searchBirthdate.getDayMonth())) searchBirthdays.add(user);

                // Check if found
                HashSet<String> usernames = new HashSet<>();
                helixHandler.getUsersByID(searchBirthdays).forEach(user -> usernames.add(user.getName()));

                // Response
                if (usernames.isEmpty()) return String.format(nobodyHasBirthdayOn, searchBirthdate.getDayMonth());
                else return String.format(followingUsersHaveBirthdayOn, searchBirthdate.getDayMonth(), String.join(", ", usernames));
            } catch (InvalidAttributeValueException e) {
                    throw new RuntimeException(e);
            }
        }

        // Check for Tagged User
        if (args.size() > 1 && args.get(1).startsWith("@")) {
            TwitchUser taggedUser = helixHandler.getUser(args.get(1).substring(1).toLowerCase());
            if (!birthdays.containsKey(taggedUser.getId())) return userHasNoBirthdaySet;
            else return String.format(userHasBirthdayOn, taggedUser.getName(), birthdays.get(taggedUser.getId()).getDate());
        }

        return switch (args.get(1).toLowerCase()) {
            case "today", "heute" -> getThis("today");
            case "week", "woche" -> getThis("week");
            case "month", "monat" -> getThis("month");
            case "year", "jahr" -> getThis("year");
            case "january", "januar", "jänner", "1" -> getAllBirthdaysInMonth(0);
            case "february", "februar", "2" -> getAllBirthdaysInMonth(1);
            case "march", "märz", "3" -> getAllBirthdaysInMonth(2);
            case "april", "4" -> getAllBirthdaysInMonth(3);
            case "may", "mai", "5" -> getAllBirthdaysInMonth(4);
            case "june", "juni", "juno", "6" -> getAllBirthdaysInMonth(5);
            case "july", "juli", "7" -> getAllBirthdaysInMonth(6);
            case "august", "8" -> getAllBirthdaysInMonth(7);
            case "september", "9" -> getAllBirthdaysInMonth(8);
            case "october", "oktober", "10" -> getAllBirthdaysInMonth(9);
            case "november", "11" -> getAllBirthdaysInMonth(10);
            case "december", "dezember", "12" -> getAllBirthdaysInMonth(11);
            default -> "Invalid Argument: " + getSyntax;
        };
    }

    // Get all birthdays in this month
    private String getAllBirthdaysInMonth(int month) {

        // Variables
        ArrayList<Map.Entry<Integer, Birthdate>> sortedBirthdays = new ArrayList<>(birthdays.entrySet());

        // Get Current Date
        Calendar today = Calendar.getInstance(TIME_ZONE);
        int currentYear = today.get(Calendar.YEAR);

        // Filter and sort birthdays in the specified month
        List<Map.Entry<Integer, Birthdate>> monthBirthdays = new ArrayList<>();
        for (Map.Entry<Integer, Birthdate> entry : sortedBirthdays) {
            Birthdate birthdate = entry.getValue();
            Calendar birthdateCalendar = Calendar.getInstance(TIME_ZONE);
            birthdateCalendar.set(Calendar.DAY_OF_MONTH, birthdate.getDay());
            birthdateCalendar.set(Calendar.MONTH, birthdate.getMonth() - 1);
            birthdateCalendar.set(Calendar.YEAR, currentYear);

            if (birthdateCalendar.get(Calendar.MONTH) == month) monthBirthdays.add(entry);
        }

        monthBirthdays.sort(Comparator.comparingInt(entry -> entry.getValue().getDay()));

        // Check if empty
        if (monthBirthdays.isEmpty()) return nobodyHasBirthdayThisMonth;

        // Build response
        StringBuilder response = new StringBuilder(String.format("%s ", followingUsersHaveBirthdayThisMonth));
        for (Map.Entry<Integer, Birthdate> entry : monthBirthdays) {
            String username = helixHandler.getUser(entry.getKey()).getName();
            response.append(String.format(userOn, username, entry.getValue().getDayMonth()));
            if (monthBirthdays.indexOf(entry) < monthBirthdays.size() - 1) response.append(", ");
        }
        response.append(". YEPP");

        return response.toString();
    }

    private String getThis(String verb) {
        // Variables
        ArrayList<Map.Entry<Integer, Birthdate>> sortedBirthdays = new ArrayList<>(birthdays.entrySet());
        HashSet<String> usernames = new HashSet<>();

        // Get Current Date
        Calendar today = Calendar.getInstance(TIME_ZONE);
        int currentYear = today.get(Calendar.YEAR);
        int currentMonth = today.get(Calendar.MONTH);
        int currentWeek = today.get(Calendar.WEEK_OF_YEAR);

        try {
            switch (verb) {
                case "today", "heute" -> {
                    Birthdate todayBirthdate = new Birthdate(String.format("%d.%d.%d", today.get(Calendar.DAY_OF_MONTH), (today.get(Calendar.MONTH) + 1), today.get(Calendar.YEAR)));
                    HashSet<Integer> ids = new HashSet<>();
                    for (var user : birthdays.keySet()) if (birthdays.get(user).getDayMonth().equals(todayBirthdate.getDayMonth())) ids.add(user);
                    HashSet<TwitchUser> users = helixHandler.getUsersByID(ids);
                    users.forEach(user -> usernames.add(user.getName()));
                    if (usernames.isEmpty()) return nobodyHasBirthdayToday;
                    else return String.format(followingUsersHaveBirthdayToday, String.join(", ", usernames));
                }
                case "week", "woche" -> {
                    List<Map.Entry<Integer, Birthdate>> weekBirthdays = new ArrayList<>();
                    int currentDayOfYear = today.get(Calendar.DAY_OF_YEAR);

                    for (Map.Entry<Integer, Birthdate> entry : sortedBirthdays) {
                        Birthdate birthdate = entry.getValue();
                        Calendar birthdateCalendar = Calendar.getInstance(TIME_ZONE);
                        birthdateCalendar.set(Calendar.DAY_OF_MONTH, birthdate.getDay());
                        birthdateCalendar.set(Calendar.MONTH, birthdate.getMonth() - 1);
                        birthdateCalendar.set(Calendar.YEAR, currentYear);
                        if (birthdateCalendar.get(Calendar.WEEK_OF_YEAR) == currentWeek && birthdateCalendar.get(Calendar.DAY_OF_YEAR) >= currentDayOfYear) weekBirthdays.add(entry);
                    }

                    weekBirthdays.sort(Comparator.comparingInt(entry -> entry.getValue().getDay()));
                    if (weekBirthdays.isEmpty()) return nobodyHasBirthdayThisWeek;
                    else {
                        HashSet<Integer> ids = new HashSet<>();
                        for (var entry : weekBirthdays) ids.add(entry.getKey());
                        HashSet<TwitchUser> users = helixHandler.getUsersByID(ids);
                        users.forEach(user -> usernames.add(user.getName()));
                        return String.format(followingUsersHaveBirthdayThisWeek, String.join(", ", usernames));
                    }
                }
                case "month", "monat" -> {
                    List<Map.Entry<Integer, Birthdate>> monthBirthdays = new ArrayList<>();
                    int currentDayOfMonth = today.get(Calendar.DAY_OF_MONTH);

                    for (Map.Entry<Integer, Birthdate> entry : sortedBirthdays) {
                        Birthdate birthdate = entry.getValue();
                        Calendar birthdateCalendar = Calendar.getInstance(TIME_ZONE);
                        birthdateCalendar.set(Calendar.DAY_OF_MONTH, birthdate.getDay());
                        birthdateCalendar.set(Calendar.MONTH, birthdate.getMonth() - 1);
                        birthdateCalendar.set(Calendar.YEAR, currentYear);
                        if (birthdateCalendar.get(Calendar.MONTH) == currentMonth && birthdateCalendar.get(Calendar.DAY_OF_MONTH) >= currentDayOfMonth) monthBirthdays.add(entry);
                    }

                    monthBirthdays.sort(Comparator.comparingInt(entry -> entry.getValue().getDay()));
                    if (monthBirthdays.isEmpty()) return nobodyHasBirthdayThisMonth;
                    else {
                        HashSet<Integer> ids = new HashSet<>();
                        for (var entry : monthBirthdays) ids.add(entry.getKey());
                        HashSet<TwitchUser> users = helixHandler.getUsersByID(ids);
                        users.forEach(user -> usernames.add(user.getName()));
                        return String.format(followingUsersHaveBirthdayThisMonth, String.join(", ", usernames));
                    }
                }
                case "year", "jahr" -> {
                    List<Map.Entry<Integer, Birthdate>> yearBirthdays = new ArrayList<>();
                    int currentDayOfYear = today.get(Calendar.DAY_OF_YEAR);

                    for (Map.Entry<Integer, Birthdate> entry : sortedBirthdays) {
                        Birthdate birthdate = entry.getValue();
                        Calendar birthdateCalendar = Calendar.getInstance(TIME_ZONE);
                        birthdateCalendar.set(Calendar.DAY_OF_MONTH, birthdate.getDay());
                        birthdateCalendar.set(Calendar.MONTH, birthdate.getMonth() - 1);
                        birthdateCalendar.set(Calendar.YEAR, currentYear);
                        if (birthdateCalendar.get(Calendar.DAY_OF_YEAR) >= currentDayOfYear) yearBirthdays.add(entry);
                    }

                    yearBirthdays.sort(Comparator.comparingInt(entry -> entry.getValue().getDay()));
                    if (yearBirthdays.isEmpty()) return nobodyHasBirthdayThisYear;
                    else {
                        HashSet<Integer> ids = new HashSet<>();
                        for (var entry : yearBirthdays) ids.add(entry.getKey());
                        HashSet<TwitchUser> users = helixHandler.getUsersByID(ids);
                        users.forEach(user -> usernames.add(user.getName()));
                        StringBuilder response = new StringBuilder(String.format("%s ", followingUsersHaveBirthdayThisYear));
                        for (var user : usernames) response.append(user).append(", ");
                        response.setLength(response.length() - 2); // Remove trailing comma and space
                        response.append(". YEPP");
                        return response.toString();
                    }
                }
                default -> {
                    return ("Invalid Argument: " + syntax);
                }
            }
        } catch (InvalidAttributeValueException e) {
            throw new RuntimeException(e);
        }
    }
}