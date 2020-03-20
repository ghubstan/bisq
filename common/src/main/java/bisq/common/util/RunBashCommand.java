package bisq.common.util;


import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.extern.slf4j.Slf4j;

import static java.lang.management.ManagementFactory.getRuntimeMXBean;

@Slf4j
public class RunBashCommand {
    private static final Logger log = LoggerFactory.getLogger(RunBashCommand.class);
    /*
        RunBashCommand.printSystemLoad(log, new Exception("some remark"));
     */
    private final String linuxShellCommand;
    private final int numLines;

    // can run basic ls or ps commands
    // can run command pipelines
    // can run sudo command if you know the password is correct
    public RunBashCommand(String linuxShellCommand, int numLines) {
        this.linuxShellCommand = linuxShellCommand;
        this.numLines = numLines;
    }

    public String run() throws IOException, InterruptedException {
        // build the system command we want to run
        List<String> commands = new ArrayList<String>();
        commands.add("/bin/bash");
        commands.add("-c");
        commands.add(this.linuxShellCommand);
        SystemCommandExecutor commandExecutor = new SystemCommandExecutor(commands);
        int result = commandExecutor.executeCommand();

        // get the stdout and stderr from the command that was run
        StringBuilder stdout = commandExecutor.getStandardOutputFromCommand();
        StringBuilder stderr = commandExecutor.getStandardErrorFromCommand();
        if (result != 0) {
            log.error("The numeric result of the command was: " + result);
            return linuxShellCommand + " returned error status " + result;
        }
        if (stderr.length() > 0) {
            log.error(stderr.toString());
            return linuxShellCommand + " returned error msg " + stderr.toString();
        }

        String[] lines = stdout.toString().split("[\\r\\n]]");
        StringBuilder prettyLines = new StringBuilder("[ $ ").append(linuxShellCommand).append(" ]\n");
        int limit = numLines > lines.length ? lines.length : numLines;
        for (int i = 0; i < limit; i++) {
            String line = lines[i].length() >= 160 ? lines[i].substring(0, 160) + " ..." : lines[i];
            prettyLines.append(line).append("\n");
        }
        return prettyLines.toString().trim();
    }

    public static void printSystemLoad(Logger log, Exception e) {
        log.info(printSystemLoadString(e));
    }

    public static String printSystemLoadString(Exception tracingException) {
        try {

            StackTraceElement[] stackTraceElement = tracingException.getStackTrace();
            StringBuilder stackTraceBuilder = new StringBuilder(tracingException.getMessage()).append("\n");
            int traceLimit = stackTraceElement.length >= 4 ? 4 : stackTraceElement.length;
            for (int i = 0; i < traceLimit; i++) {
                stackTraceBuilder.append(stackTraceElement[i]).append("\n");
            }
            stackTraceBuilder.append("...");
            log.info(stackTraceBuilder.toString());
            String psOutput = new RunBashCommand("ps -aux --sort -rss --headers", 2).run();
            return psOutput + "\n"
                    + "System load: Memory (MB): " + getUsedMemoryInMB() + " / No. of threads: " + Thread.activeCount()
                    + " JVM uptime (ms): " + getRuntimeMXBean().getUptime();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return "error running bash command(s)";
    }

    public static long getUsedMemoryInMB() {
        Runtime runtime = Runtime.getRuntime();
        long free = runtime.freeMemory() / 1024 / 1024;
        long total = runtime.totalMemory() / 1024 / 1024;
        return total - free;
    }
}
