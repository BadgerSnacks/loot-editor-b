package dev.badgersnacks.looteditor.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Lightweight file logger that records high-level UI actions to a per-session log file.
 */
public final class ActionLogger implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ActionLogger.class);
    private static final DateTimeFormatter FILE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter ENTRY_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final Path logFile;
    private final BufferedWriter writer;

    public ActionLogger() {
        try {
            this.logFile = createLogFile();
            this.writer = Files.newBufferedWriter(logFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            log("session:start", "Loot Editor logging to " + logFile.toAbsolutePath());
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to initialize action logger", e);
        }
    }

    public Path getLogFile() {
        return logFile;
    }

    public void log(String action, String message) {
        log(action, message, null);
    }

    public synchronized void log(String action, String message, Throwable error) {
        try {
            writer.write(ENTRY_FORMAT.format(Instant.now()));
            writer.write(" [");
            writer.write(action);
            writer.write("] ");
            writer.write(message == null ? "" : message);
            writer.newLine();
            if (error != null) {
                StringWriter sw = new StringWriter();
                error.printStackTrace(new java.io.PrintWriter(sw));
                writer.write(sw.toString());
            }
            writer.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to write action log entry", e);
        }
    }

    @Override
    public synchronized void close() {
        try {
            log("session:end", "Closing action logger.");
            writer.close();
        } catch (IOException e) {
            LOGGER.warn("Failed to close action logger", e);
        }
    }

    private static Path createLogFile() throws IOException {
        Path logsDir = Path.of(System.getProperty("user.home"), ".loot-editor-b", "logs");
        Files.createDirectories(logsDir);
        String fileName = "loot-editor-" + FILE_FORMAT.format(Instant.now()) + ".log";
        return logsDir.resolve(fileName).toAbsolutePath();
    }
}
