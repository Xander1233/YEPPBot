package de.MCmoderSD.objects;

import com.fasterxml.jackson.databind.JsonNode;
import de.MCmoderSD.json.JsonUtility;

import javax.management.InvalidAttributeValueException;

import java.io.IOException;

import java.net.URISyntaxException;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.Period;
import java.time.temporal.ChronoUnit;

import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

@SuppressWarnings("unused")
public class Birthdate {

    // Set Timezone
    public static final TimeZone TIME_ZONE = TimeZone.getTimeZone("Europe/Berlin");

    // Constants
    private final int year;
    private final MonthDay date;
    private final ZodiacSign zodiacSign;

    // Constructor
    public Birthdate(String date) throws InvalidAttributeValueException, NumberFormatException {

        // Split Date
        String[] split = date.split("\\.");
        if (split.length != 3) throw new InvalidAttributeValueException("Invalid date: " + date);

        // Parse Date
        byte day = Byte.parseByte(split[0]);
        byte month = Byte.parseByte(split[1]);
        short year = Short.parseShort(split[2]);

        // Check and set Date
        this.date = MonthDay.of(month, day);

        // Check Year
        if (year < 1 || year > Calendar.getInstance(TIME_ZONE).get(Calendar.YEAR)) throw new InvalidAttributeValueException("Invalid year: " + year);
        else this.year = year;

        // Set Zodiac Sign
        zodiacSign = ZodiacSign.getZodiacSign(this.date);
    }

    // Getter
    public boolean isBirthday() {
        LocalDate today = LocalDate.now(TIME_ZONE.toZoneId());
        return today.getMonthValue() == date.getMonthValue() && today.getDayOfMonth() == date.getDayOfMonth();
    }

    // Get Day
    public int getDay() {
        return date.getDayOfMonth();
    }

    // Get Month
    public int getMonth() {
        return date.getMonthValue();
    }

    // Get Year
    public int getYear() {
        return year;
    }

    // Get MM/DD/YYYY
    public String getDate() {
        return getDay() + "." + getMonth() + "." + getYear();
    }

    // Get YYYY.MM.DD
    public String getMySQLDate() {
        return getYear() + "." + getMonth() + "." + getDay();
    }

    // Get DD/MM
    public String getDayMonth() {
        return getDay() + "." + getMonth();
    }

    // Get MonthDay
    public MonthDay getMonthDay() {
        return date;
    }

    public TimeZone getTimeZone() {
        return TIME_ZONE;
    }

    public ZodiacSign getZodiacSign() {
        return zodiacSign;
    }

    public int getAge() {
        LocalDate today = LocalDate.now(TIME_ZONE.toZoneId());
        LocalDate birthDate = LocalDate.of(year, date.getMonthValue(), date.getDayOfMonth());
        return Period.between(birthDate, today).getYears();
    }

    public long getNanosecondsUntilBirthday() {
        return getMicrosecondsUntilBirthday() * 1000L;
    }

    public long getMicrosecondsUntilBirthday() {
        return getMillisecondsUntilBirthday() * 1000L;
    }

    public long getMillisecondsUntilBirthday() {
        return getSecondsUntilBirthday() * 1000L;
    }

    public int getSecondsUntilBirthday() {
        return getMinutesUntilBirthday() * 60;
    }

    public int getMinutesUntilBirthday() {
        return getHoursUntilBirthday() * 60;
    }

    public int getHoursUntilBirthday() {
        return getDaysUntilBirthday() * 24;
    }

    public int getDaysUntilBirthday() {

        // Get Birthday
        LocalDate today = LocalDate.now(TIME_ZONE.toZoneId());
        LocalDate birthday = LocalDate.of(today.getYear(), date.getMonthValue(), date.getDayOfMonth());

        // If birthday has already occurred this year, set it to next year
        if (today.isAfter(birthday) || today.isEqual(birthday)) birthday = birthday.plusYears(1);

        return (int) ChronoUnit.DAYS.between(today, birthday);
    }

    public float getWeeksUntilBirthday() {
        return getDaysUntilBirthday() / 7f;
    }

    public float getMonthsUntilBirthday() {
        return getYearsUntilBirthday() * 12f;
    }

    public float getYearsUntilBirthday() {
        return getDaysUntilBirthday() / 365f;
    }

    // Zodiac Sign
    public enum ZodiacSign {

        // Constants
        ARIES(MonthDay.of(3, 21), MonthDay.of(4, 20)),
        TAURUS(MonthDay.of(4, 21), MonthDay.of(5, 20)),
        GEMINI(MonthDay.of(5, 21), MonthDay.of(6, 21)),
        CANCER(MonthDay.of(6, 22), MonthDay.of(7, 22)),
        LEO(MonthDay.of(7, 23), MonthDay.of(8, 23)),
        VIRGO(MonthDay.of(8, 24), MonthDay.of(9, 23)),
        LIBRA(MonthDay.of(9, 24), MonthDay.of(10, 23)),
        SCORPIO(MonthDay.of(10, 24), MonthDay.of(11, 22)),
        SAGITTARIUS(MonthDay.of(11, 23), MonthDay.of(12, 21)),
        CAPRICORN(MonthDay.of(12, 22), MonthDay.of(1, 20)),
        AQUARIUS(MonthDay.of(1, 21), MonthDay.of(2, 19)),
        PISCES(MonthDay.of(2, 20), MonthDay.of(3, 20));

        // Attributes
        private final MonthDay startDate;
        private final MonthDay endDate;
        private final Iterator<Map.Entry<String, JsonNode>> matches;

        // Static Attributes
        private ZodiacSign[] compatibleSigns;

        // Constructor
        ZodiacSign(MonthDay startDate, MonthDay endDate) {

            // Set Attributes
            this.startDate = startDate;
            this.endDate = endDate;

            // Load Matches
            try {
                matches = JsonUtility.loadJson("/assets/matchList.json", false).get(getName()).fields();
            } catch (IOException | URISyntaxException e) {
                throw new RuntimeException("Failed to load match list for " + getName() + "!", e);
            }
        }

        // Static Initializer
        static {
            ARIES.compatibleSigns = new ZodiacSign[]{GEMINI, LEO, SAGITTARIUS};
            TAURUS.compatibleSigns = new ZodiacSign[]{CANCER, LIBRA, PISCES};
            GEMINI.compatibleSigns = new ZodiacSign[]{GEMINI, LIBRA, SAGITTARIUS};
            CANCER.compatibleSigns = new ZodiacSign[]{VIRGO, SCORPIO, PISCES};
            LEO.compatibleSigns = new ZodiacSign[]{LEO, VIRGO, LIBRA};
            VIRGO.compatibleSigns = new ZodiacSign[]{TAURUS, SCORPIO, CAPRICORN};
            LIBRA.compatibleSigns = new ZodiacSign[]{GEMINI, LEO, AQUARIUS};
            SCORPIO.compatibleSigns = new ZodiacSign[]{CANCER, VIRGO, CAPRICORN};
            SAGITTARIUS.compatibleSigns = new ZodiacSign[]{SAGITTARIUS, AQUARIUS, PISCES};
            CAPRICORN.compatibleSigns = new ZodiacSign[]{CAPRICORN, TAURUS, PISCES};
            AQUARIUS.compatibleSigns = new ZodiacSign[]{ARIES, GEMINI, AQUARIUS};
            PISCES.compatibleSigns = new ZodiacSign[]{TAURUS, CANCER, CAPRICORN};
        }

        // Methods
        public String getName() {
            return name().charAt(0) + name().substring(1).toLowerCase();
        }

        public MonthDay getStartDate() {
            return startDate;
        }

        public MonthDay getEndDate() {
            return endDate;
        }

        public ZodiacSign[] getCompatibleSigns() {
            return compatibleSigns;
        }

        public ZodiacSign getCompatibleSign(int index) {
            return compatibleSigns[index];
        }

        public String getTranslatedName() {
            return switch (name().toLowerCase()) {
                case "aquarius" -> "Wassermann";
                case "pisces" -> "Fische";
                case "aries" -> "Widder";
                case "taurus" -> "Stier";
                case "gemini" -> "Zwillinge";
                case "cancer" -> "Krebs";
                case "leo" -> "Löwe";
                case "virgo" -> "Jungfrau";
                case "libra" -> "Waage";
                case "scorpio" -> "Skorpion";
                case "sagittarius" -> "Schütze";
                case "capricorn" -> "Steinbock";
                default -> "Unbekannt";
            };
        }

        public LinkedHashMap<ZodiacSign, String> getMatches() {
            LinkedHashMap<ZodiacSign, String> matches = new LinkedHashMap<>();
            this.matches.forEachRemaining(entry -> matches.put(getZodiacSign(entry.getKey()), entry.getValue().asText()));
            return matches;
        }

        public String getMatchDescription(ZodiacSign sign) {
            return getMatches().get(sign);
        }

        // Static Methods
        public static ZodiacSign getZodiacSign(String name) {
            for (ZodiacSign zodiacSign : values()) if (zodiacSign.getName().equalsIgnoreCase(name)) return zodiacSign;
            return null;
        }

        public static ZodiacSign getZodiacSign(int month, int day) {
            return getZodiacSign(MonthDay.of(month, day));
        }

        public static ZodiacSign getZodiacSign(MonthDay monthDay) {
            for (ZodiacSign sign : ZodiacSign.values()) {
                if (sign.getStartDate().isBefore(sign.getEndDate())) {

                    // Norm al case: start date is before end date within the same year
                    if ((monthDay.isAfter(sign.getStartDate()) || monthDay.equals(sign.getStartDate())) && (monthDay.isBefore(sign.getEndDate()) || monthDay.equals(sign.getEndDate()))) {
                        return sign;
                    }

                } else {

                    // Special case: zodiac sign spans the end and start of the year
                    if ((monthDay.isAfter(sign.getStartDate()) || monthDay.equals(sign.getStartDate())) || (monthDay.isBefore(sign.getEndDate()) || monthDay.equals(sign.getEndDate()))) {
                        return sign;
                    }
                }
            }

            // Invalid date
            throw new IllegalArgumentException("Invalid date: " + monthDay);
        }
    }
}