package io.github.sebminecrafter.SebbyLogger;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

/** This is the main class that has all the log functions. */
public class Main {
    /** SebbyLogger Version*/
    public static String ver = "0.1";
    /** Private time function to format the time */
    private static String time() {return "(" + new SimpleDateFormat("HH.mm.ss").format(new Date()) + ")";} // Return formatted date (HH.mm.ss)
    /** Private formatMessage to format and print a message, even with colors*/
    public static void formatMessage(String text, String source, String messageType, String color) {
        System.out.println(
                ConsoleColors.getColor(color) + // Get the
                time() + // Add formatted time
                "[" + source + "]" + // Source of message (main, render, etc.)
                "[" + messageType + "]: " + // Message type (info, warn, error, etc.)
                text + // Just the message text
                ConsoleColors.RESET // Reset color
        );
    }
    /** <h3>INFO Message</h3><br>
     * @param source The source of the message (e.g. Game)
     * @param text The message to log/send to console
     */
    public static void info(String text, String source) {
        formatMessage(text, source, "INFO", "WHITE"); // print full info message
    }
    /** <h3>DEBUG Message</h3><br>
     * @param source The source of the message (e.g. Game)
     * @param text The message to log/send to console
     */
    public static void debug(String text, String source) {
        formatMessage(text, source, "DEBUG", "CYAN"); // print full debug message
    }
    /** <h3>WARN Message</h3><br>
     * @param source The source of the message (e.g. Game)
     * @param text The message to log/send to console
     */
    public static void warn(String text, String source) {
        formatMessage(text, source, "WARN", "YELLOW"); // print full warn
    }
    /** <h3>ERROR Message</h3><br>
     * @param source The source of the message (e.g. Game)
     * @param text The message to log/send to console
     */
    public static void error(String text, String source) {
        formatMessage(text, source, "ERROR", "RED"); // Print full error message
    }
    /** <h3>ERROR/TRACE Message</h3><br>
     * @param source The source of the message (e.g. Game)
     * @param text The message to log/send to console
     */
    public static void trace(String text, String source) {
        String stacktrace = Arrays.toString(Thread.currentThread().getStackTrace()) // Converts stacktrace (array) to string for printing
            .replace("[","").replace("]",""); // Removes the brackets from conversion
        String newMessage = text + " [Stack trace: " + stacktrace + "]"; // Adds new brackets where I want them
        formatMessage(newMessage, source, "ERROR/TRACE", "RED"); // Print full message
    }
    /** Startup Message*/
    private static void startupMessage() {
        String logo = "Using SebbyLogger version "+ver;
        info(logo,"SebbyLogger/Main");
    }
    /** Main function*/
    public static void main(String[] args) {
        if (!Arrays.asList(args).contains("nologo")) {
            startupMessage();
        }
    }
}