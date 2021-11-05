package de.uni_leipzig.life.csv2fhir;

import java.io.PrintWriter;

import picocli.CommandLine;
import picocli.CommandLine.IExitCodeExceptionMapper;

/**
 * @author fheuschkel (02.11.2020)
 */
class PrintExceptionMessageHandler implements CommandLine.IExecutionExceptionHandler {

    @Override
    public int handleExecutionException(Exception ex, CommandLine cmd, CommandLine.ParseResult parseResult) {
        // bold red error message
        try (PrintWriter errWriter = cmd.getErr()) {
            errWriter.println(cmd.getColorScheme().errorText(ex.getMessage()));
            IExitCodeExceptionMapper exitCodeExceptionMapper = cmd.getExitCodeExceptionMapper();
            return exitCodeExceptionMapper != null ? exitCodeExceptionMapper.getExitCode(ex)
                    : cmd.getCommandSpec().exitCodeOnExecutionException();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * @param e
     */
    public static void printException(Exception e) {
        if (e.getMessage() == null) {
            e.printStackTrace();
        } else {
            System.out.println(e.getMessage());
        }
    }

}
