package de.MCmoderSD.utilities.database.manager;

import de.MCmoderSD.utilities.database.MySQL;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

import java.util.Calendar;
import java.util.HashMap;

@SuppressWarnings("SqlSourceToSinkFlow")
public class EventManager {

    // Associations
    private final MySQL mySQL;

    // Constructor
    public EventManager(MySQL mySQL) {

        // Get Associations
        this.mySQL = mySQL;

        // Initialize Tables
        initTables();
    }

    // Initialize Tables
    private void initTables() {
        try {

            // Variables
            Connection connection = mySQL.getConnection();

            // Create NNN and DDD tables
            String[] tables = {"NoNutNovember", "DickDestroyDecember"};
            for (String table : tables) {
                connection.prepareStatement(String.format(
                        """
                        CREATE TABLE IF NOT EXISTS %s (
                        year SMALLINT NOT NULL,
                        id INT NOT NULL,
                        joined DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        gave_up DATETIME DEFAULT NULL
                        )
                        """, table)).execute();
            }

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
    }

    public boolean joinEvent(Integer id, Event event) {
        try {
            if (!mySQL.isConnected()) mySQL.connect();

            // Check if user is already joined
            if (isJoined(id, event)) return false;

            // Add user to event
            String query = String.format("INSERT INTO %s (year, id) VALUES (?, ?)", event.getTable());
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            preparedStatement.setInt(1, Calendar.getInstance().get(Calendar.YEAR));
            preparedStatement.setInt(2, id);
            preparedStatement.executeUpdate();

            // Close resources
            preparedStatement.close();

            // Check if user is registered
            return isJoined(id, event);

        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return false;
    }

    public boolean leaveEvent(Integer id, Event event) {
        try {
            if (!mySQL.isConnected()) mySQL.connect();

            // Check if user is joined
            if (!isJoined(id, event)) return false;

            // Check if user has left
            if (hasLeft(id, event)) return false;

            // Add user to event
            String query = String.format("UPDATE %s SET gave_up = CURRENT_TIMESTAMP WHERE year = ? AND id = ?", event.getTable());
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            preparedStatement.setInt(1, Calendar.getInstance().get(Calendar.YEAR));
            preparedStatement.setInt(2, id);
            preparedStatement.executeUpdate();

            // Close resources
            preparedStatement.close();

            // Check if user has left
            return hasLeft(id, event);
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return false;
    }

    public boolean isJoined(Integer id, Event event) {
        try {
            if (!mySQL.isConnected()) mySQL.connect();

            String query = String.format("SELECT * FROM %s WHERE year = ? AND id = ?", event.getTable());
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            preparedStatement.setInt(1, Calendar.getInstance().get(Calendar.YEAR));
            preparedStatement.setInt(2, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return false;
    }

    public boolean hasLeft(Integer id, Event event) {
        try {
            if (!mySQL.isConnected()) mySQL.connect();

            String query = String.format("SELECT * FROM %s WHERE year = ? AND id = ? AND gave_up IS NOT NULL", event.getTable());
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            preparedStatement.setInt(1, Calendar.getInstance().get(Calendar.YEAR));
            preparedStatement.setInt(2, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            return resultSet.next();
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return false;
    }

    public HashMap<Integer, Boolean> getParticipants(Event event) {
        try {
            if (!mySQL.isConnected()) mySQL.connect();

            // Variables
            HashMap<Integer, Boolean> participants = new HashMap<>();

            // Get participants
            String query = String.format("SELECT id, gave_up FROM %s WHERE year = ?", event.getTable());
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            preparedStatement.setInt(1, Calendar.getInstance().get(Calendar.YEAR));
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) participants.put(resultSet.getInt("id"), resultSet.getObject("gave_up") == null);

            // Close resources
            preparedStatement.close();

            return participants;
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    public Timestamp[] getParticipant(Integer id, Event event) {
        try {
            if (!mySQL.isConnected()) mySQL.connect();

            // Get participants
            String query = String.format("SELECT joined, gave_up FROM %s WHERE year = ? AND id = ?", event.getTable());
            PreparedStatement preparedStatement = mySQL.getConnection().prepareStatement(query);
            preparedStatement.setInt(1, Calendar.getInstance().get(Calendar.YEAR));
            preparedStatement.setInt(2, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) return new Timestamp[]{resultSet.getTimestamp("joined"), resultSet.getTimestamp("gave_up")};
        } catch (SQLException e) {
            System.err.println(e.getMessage());
        }
        return null;
    }

    // Events
    public enum Event {

        // Enumerations
        NNN("NoNutNovember", Calendar.NOVEMBER),
        DDD("DickDestroyDecember", Calendar.DECEMBER);

        // Variables
        private final String table;
        private final int month;

        // Constructor
        Event(String table, int month) {
            this.table = table;
            this.month = month;
        }

        // Getter
        public String getTable() {
            return table;
        }

        public int getMonth() {
            return month;
        }
    }
}