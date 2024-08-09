package de.MCmoderSD.utilities.database.manager;

import de.MCmoderSD.objects.TwitchMessageEvent;
import de.MCmoderSD.utilities.database.MySQL;

import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

public class LurkManager {

    // Assosiations
    private final MySQL mySQL;

    // Constructor
    public LurkManager(MySQL mySQL) {
        this.mySQL = mySQL;
    }

    // Save Lurk
    public HashMap<Integer, Integer> saveLurk(TwitchMessageEvent event) {

        // Set Variables
        var channelID = event.getChannelId();
        var userID = event.getUserId();

        // Log message
        try {
            if (!mySQL.isConnected()) mySQL.connect(); // connect

            // Check Channel and User
            mySQL.checkCache(channelID, event.getChannel());
            mySQL.checkCache(userID, event.getUser());

            // Prepare statement
            String query = "INSERT INTO " + "lurkList" + " (user_id, lurkChannel_ID, startTime) VALUES (?, ?, ?)";
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            preparedStatement.setInt(1, userID); // set user
            preparedStatement.setInt(2, channelID); // set channel
            preparedStatement.setTimestamp(3, event.getTimestamp()); // set timestamp
            preparedStatement.executeUpdate(); // execute
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return getLurkList(); // get lurk time
    }

    public HashMap<Integer, Integer> getLurkList() {

        // Variables
        HashMap<Integer, Integer> lurkList = new HashMap<>();

        // Get Custom Timers
        try {
            if (!mySQL.isConnected()) mySQL.connect(); // connect

            // Prepare statement
            String query = "SELECT user_id, lurkChannel_ID FROM " + "lurkList";
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            ResultSet resultSet = preparedStatement.executeQuery();

            // Add to List
            while (resultSet.next()) lurkList.put(resultSet.getInt("user_id"), resultSet.getInt("lurkChannel_ID"));

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return lurkList;
    }

    // Get Lurk Time
    public HashMap<Timestamp, ArrayList<Integer>> getLurkTime(int userID) {

        // Variables
        HashMap<Timestamp, ArrayList<Integer>> lurkTime = new HashMap<>();
        ArrayList<Integer> channels = new ArrayList<>();

        // Get Custom Timers
        try {
            if (!mySQL.isConnected()) mySQL.connect(); // connect

            // Prepare statement
            String query = "SELECT startTime, lurkChannel_ID, traitorChannel FROM " + "lurkList WHERE user_id = ?";
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            preparedStatement.setInt(1, userID);
            ResultSet resultSet = preparedStatement.executeQuery();

            // Add to List
            while (resultSet.next()) {
                // Get Start Time
                Timestamp startTime = resultSet.getTimestamp("startTime");

                // Get Lurk Channel
                var channel = resultSet.getInt("lurkChannel_ID");
                channels.add(channel);
                lurkTime.put(startTime, channels);

                // Get TraitorChannel
                if (resultSet.getString("traitorChannel") == null) return lurkTime;
                String[] traitorChannel = resultSet.getString("traitorChannel").split("\t");
                if (traitorChannel.length == 0 || traitorChannel[0].isEmpty()) return lurkTime;
                for (String name : traitorChannel) if (!name.isEmpty()) channels.add(Integer.parseInt(name));
            }

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return lurkTime;
    }
}