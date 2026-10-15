package de.uni_leipzig.imise;

import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import de.uni_leipzig.UcumMapper;
import de.uni_leipzig.life.csv2fhir.PrintExceptionMessageHandler;
import picocli.CommandLine;

/** Internal sequential worker for one Synthea import; job metadata arrives on stdin. */
public final class Excel2FhirBatch {
    public static void main(String[] args) throws Exception {
        var jobs = JsonParser.parseReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).getAsJsonArray();
        for (var element : jobs) execute(element.getAsJsonObject());
    }

    static void execute(JsonObject job) throws Exception {
        PrintStream previousOut = System.out, previousErr = System.err;
        long started = System.nanoTime();
        int exitCode;
        try (var log = new PrintStream(Files.newOutputStream(Path.of(job.get("log").getAsString())),
                true, StandardCharsets.UTF_8)) {
            System.setOut(log);
            System.setErr(log);
            // Console appenders capture their stream when configured; bind them to this case.
            ((LoggerContext) LogManager.getContext(false)).reconfigure();
            UcumMapper.invalidUcumCodes.clear();
            String[] arguments = new com.google.gson.Gson().fromJson(job.get("arguments"), String[].class);
            exitCode = new CommandLine(new Excel2FhirMain(job.has("csvDirectory") ? new java.io.File(job.get("csvDirectory").getAsString()) : null))
                    .setOut(new PrintWriter(log, true)).setErr(new PrintWriter(log, true))
                    .setExecutionExceptionHandler(new PrintExceptionMessageHandler()).execute(arguments);
        } finally {
            UcumMapper.invalidUcumCodes.clear();
            System.setOut(previousOut);
            System.setErr(previousErr);
            ((LoggerContext) LogManager.getContext(false)).reconfigure();
        }
        var result = new JsonObject();
        result.addProperty("exitCode", exitCode);
        result.addProperty("seconds", (System.nanoTime() - started) / 1e9);
        Files.writeString(Path.of(job.get("result").getAsString()), result.toString());
    }
}
