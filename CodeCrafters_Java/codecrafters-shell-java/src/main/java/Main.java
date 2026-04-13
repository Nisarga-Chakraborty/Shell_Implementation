import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.stream.Collectors;

import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

// ============================================================ ShellCompleter
class ShellCompleter implements Completer {
    private final Main shell;
    //private static final List<String> FILE_COMMANDS = List.of("cd", "cat", "type");
    private String lastPrefix = "";
    private int tabCount = 0;

    ShellCompleter(Main shell) {
        this.shell = shell;
    }
    private String getCommonPrefix(List<String> matches) {
    if (matches == null || matches.isEmpty()) return "";
    if (matches.size() == 1) return matches.get(0);
    
    // Use your LCP function
    return shell.longestCommonPrefix_incrementing(
        matches.toArray(new String[0])
    );
    }

    @Override
public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    String word = line.word();
    int wordIndex = line.wordIndex();

    if (wordIndex == 0) {
        List<String> matches = new ArrayList<>();

        // Collect shell builtins
        for (String cmd : shell.shell_commands) {
            if (cmd.startsWith(word)) matches.add(cmd);
        }

        // Collect PATH executables
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String sep = System.getProperty("os.name").toLowerCase().contains("windows") ? ";" : ":";
            for (String dir : pathEnv.split(sep)) {
                if (dir.isEmpty()) continue;
                File folder = new File(dir);
                File[] files = folder.listFiles();
                if (files == null) continue;
                for (File f : files) {
                    if (f.isFile() && f.canExecute() && f.getName().startsWith(word))
                        matches.add(f.getName());
                }
            }
        }
         

        // Deduplicate and sort
        matches = matches.stream().distinct().sorted().collect(Collectors.toList());

        if (matches.isEmpty()) return;

        if (matches.size() == 1) {
            candidates.add(new Candidate(matches.get(0)));
            tabCount = 0;
            lastPrefix = "";
            return;
        }

        String commonPrefix = getCommonPrefix(matches);
        if (commonPrefix.length() > word.length()) {
            candidates.add(new Candidate(commonPrefix, commonPrefix, null, null, null, null, false));
            return;
        }

        // Multiple matches — bell on first tab, list on second
        if (word.equals(lastPrefix)) {
            tabCount++;
        } else {
            tabCount = 1;
            lastPrefix = word;
        }

        if (tabCount == 1) {
            reader.getTerminal().writer().print("\007");
            reader.getTerminal().writer().flush();
        } else {
            reader.getTerminal().writer().println();
            reader.getTerminal().writer().println(String.join("  ", matches));
            reader.getTerminal().writer().flush();
            reader.callWidget(LineReader.REDRAW_LINE);
            reader.callWidget(LineReader.REDISPLAY);
            tabCount = 0;
            lastPrefix = "";
        }

    } else {
    // Split word into directory prefix and filename prefix
    // e.g. "cow/pi" -> dirPrefix="cow/", namePrefix="pi"
    // e.g. "cow/"   -> dirPrefix="cow/", namePrefix=""
    // e.g. "fox_"   -> dirPrefix="",     namePrefix="fox_"
    String dirPrefix = "";
    String namePrefix = word;
    int lastSlash = word.lastIndexOf('/');
    if (lastSlash >= 0) {
        dirPrefix = word.substring(0, lastSlash + 1);
        namePrefix = word.substring(lastSlash + 1);
    }

    File dir = new File(shell.currentPath.toFile(), dirPrefix);
    File[] allFiles = dir.listFiles();

    List<String> files = new ArrayList<>();
    List<String> fileValues = new ArrayList<>();

    if (allFiles != null) {
        for (File f : allFiles) {
            String name = f.getName();
            if (name.startsWith(namePrefix)) {
                if (f.isDirectory()) {
                    files.add(name + "/");        // display: no trailing space
                    fileValues.add(dirPrefix + name + "/");  // completion value
                } else {
                    files.add(name);              // display: no trailing char
                    fileValues.add(dirPrefix + name + " "); // completion value with space
                }
            }
        }
    }

    // Sort both together as pairs by clean name
    List<int[]> indices = new ArrayList<>();
    for (int i = 0; i < files.size(); i++) indices.add(new int[]{i});
    indices.sort((a, b) -> {
        String cleanA = files.get(a[0]).replaceAll("[/ ]$", "");
        String cleanB = files.get(b[0]).replaceAll("[/ ]$", "");
        return cleanA.compareTo(cleanB);
    });
    List<String> sortedFiles = new ArrayList<>();
    List<String> sortedValues = new ArrayList<>();
    for (int[] idx : indices) {
        sortedFiles.add(files.get(idx[0]));
        sortedValues.add(fileValues.get(idx[0]));
    }
    files.clear(); files.addAll(sortedFiles);
    fileValues.clear(); fileValues.addAll(sortedValues);

    if (files.isEmpty()) return;

    if (files.size() == 1) {
    String val = fileValues.get(0);
    if (val.endsWith("/")) {
        // Directory — no trailing space
        candidates.add(new Candidate(val, val, null, null, null, null, false));
    } else {
        // File — strip the space we added, let JLine add it cleanly
        String cleanVal = val.endsWith(" ") ? val.substring(0, val.length() - 1) : val;
        candidates.add(new Candidate(cleanVal, cleanVal, null, null, null, null, true));
    }
    tabCount = 0;
    lastPrefix = "";
    return;
}
// Multiple matches — check LCP first
String commonPrefix = shell.longestCommonPrefix_incrementing(
    files.stream().map(f -> f.replaceAll("[/ ]$", "")).toArray(String[]::new)
);

if (commonPrefix.length() > namePrefix.length()) {
    // Can complete further to LCP
    candidates.add(new Candidate(dirPrefix + commonPrefix, dirPrefix + commonPrefix, null, null, null, null, false));
    tabCount = 0;
    lastPrefix = "";
    return;
}

    // Multiple matches — bell on first tab, list on second
    if (word.equals(lastPrefix)) {
        tabCount++;
    } else {
        tabCount = 1;
        lastPrefix = word;
    }
    

    if (tabCount == 1) {
        reader.getTerminal().writer().print("\007");
        reader.getTerminal().writer().flush();
    } else {
        reader.getTerminal().writer().println();
        reader.getTerminal().writer().println(String.join("  ", files));
        reader.getTerminal().writer().flush();
        reader.callWidget(LineReader.REDRAW_LINE);
        reader.callWidget(LineReader.REDISPLAY);
        tabCount = 0;
        lastPrefix = "";
    }
}
}
    }

// ================================================================= Main
public final class Main {
    Path currentPath;
    Path previousPath;
    String home;
    String full_command = "";
    int a = 0;
    private LineReader lineReader;
    private boolean useJLine = true;
    private LinkedList<String> commandHistory;
     int historyCounter = 1;  // For absolute numbering
    int MAX_SIZE = 100;
    int lastWrittenIndex = 0;

    String[] shell_commands = {
        ":", ".",
        "alias", "bg", "cd", "command", "echo", "eval", "exec", "exit",
        "export", "fc", "fg", "getopts", "jobs", "kill", "pwd", "read",
        "readonly", "set", "shift", "times", "trap", "type", "ulimit",
        "umask", "unalias", "unset", "wait", "history", "jobs"
    };

    
    public Main() throws IOException {
        currentPath = Paths.get(System.getProperty("user.dir"));
        previousPath = currentPath;
        commandHistory = new LinkedList<>();
        historyCounter = 0;
        String historyString = System.getenv("HISTFILE");
        if(historyString != null && !historyString.trim().isEmpty() )
            {
                loadHistory(historyString);
            }
            else 
            {

            }
            setupShutdownHook();
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();

            lineReader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new ShellCompleter(this))
                    .option(LineReader.Option.DISABLE_EVENT_EXPANSION, true)
                    .option( LineReader.Option.AUTO_REMOVE_SLASH,true)
                    .build();
            useJLine = true;
        } catch (Exception e) {
            // JLine failed (non-interactive environment like tests) — fall back to Scanner
            useJLine = false;
        }
    }

    void input() throws FileNotFoundException, IOException {
        PrintStream consoleOut = System.out;
        PrintStream consoleErr = System.err;

        if (useJLine) {
            while (true) {
                try {
                    full_command = lineReader.readLine("$ ");
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                if (full_command == null || full_command.isEmpty()) continue;
                else if(full_command.equals("jobs"))
                {
                    System.out.print("");
                }
                else 
                {
                    addHistory(full_command);
                    processCommand(full_command, consoleOut, consoleErr);
                }
                
            }
        } else {
            // Non-interactive fallback for tests
            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    System.out.print("$ ");
                    if (!scanner.hasNextLine()) break;
                    full_command = scanner.nextLine();// get input
                    if (full_command == null || full_command.isEmpty()) continue;
                    else if(full_command.equals("jobs"))
                {
                    System.out.print("");
                }
                else 
                {
                    processCommand(full_command, consoleOut, consoleErr);
                }
                    
                }
            }
        }
    }

    void processCommand(String command, PrintStream consoleOut, PrintStream consoleErr) throws IOException {
        boolean isAppend = command.contains(">>");
        boolean isRedirect = false;
        boolean isStderrRedirect = false;
        String filename = "";
        String commandPart = command;
        PrintStream fileOut = null;
        PrintStream fileErr = null;

        if (command.contains("2>>")) {
            String[] parts = command.split("2>>", 2);
            commandPart = parts[0].trim();
            filename = parts[1].trim();
            isStderrRedirect = true;
            isRedirect = true;
        } else if (command.contains("1>>")) {
            String[] parts = command.split("1>>", 2);
            commandPart = parts[0].trim();
            filename = parts[1].trim();
            isRedirect = true;
        } else if (isAppend) {
            String[] parts = command.split(">>", 2);
            commandPart = parts[0].trim();
            filename = parts[1].trim();
            isRedirect = true;
        } else if (command.contains("2>")) {
            String[] parts = command.split("2>", 2);
            commandPart = parts[0].trim();
            filename = parts[1].trim();
            isStderrRedirect = true;
            isRedirect = true;
        } else if (command.contains("1>")) {
            String[] parts = command.split("1>", 2);
            commandPart = parts[0].trim();
            filename = parts[1].trim();
            isRedirect = true;
        } else if (command.contains(">")) {
            String[] parts = command.split(">", 2);
            commandPart = parts[0].trim();
            filename = parts[1].trim();
            isRedirect = true;
        }

        full_command = commandPart;

        if (isRedirect && !isStderrRedirect) {
            if (!filename.isEmpty()) {
                try {
                    fileOut = new PrintStream(new FileOutputStream(filename, isAppend));
                    System.setOut(fileOut);
                } catch (FileNotFoundException e) {
                    System.err.println("File Creation failed: " + e.getMessage());
                    fileOut = null;
                    System.setOut(consoleOut);
                }
            }
        }

        if (isStderrRedirect) {
            if (!filename.isEmpty()) {
                try {
                    fileErr = new PrintStream(new FileOutputStream(filename, isAppend));
                    System.setErr(fileErr);
                } catch (FileNotFoundException e) {
                    System.err.println("File Creation failed for stderr: " + e.getMessage());
                    fileErr = null;
                }
            }
        }
        int pipeCount = 0;
        char c ;
        if(full_command.contains("|"))
        {
            for ( int i =0 ; i< full_command.length() ; i++)
        {
            c= full_command.charAt(i);
            if( c == '|')
            {
                pipeCount ++;
            }
        }
        List<String>[] commands = new List[pipeCount + 1];  // Array of Lists!
        String pipeParts[] = full_command.split("\\|");
        String builtinOutput = "";
        
        // Build all commands
for (int i = 0; i < pipeParts.length; i++) {
    commands[i] = tokenize(pipeParts[i].trim());
}

// Check if FIRST command is a built-in
String firstCmd = commands[0].get(0);
if (isBuiltIn(firstCmd) && commands.length == 2) {
    // Special case: built-in | external (like "echo hello | grep h")
     builtinOutput = executeBuiltinWithCapture(commands[0]);
    
    // Run the second command with that output as input
    ProcessBuilder pb = new ProcessBuilder(commands[1]);
    pb.directory(currentPath.toFile());
    Process p = pb.start();
    
    // Write the built-in output to the process
    try (PrintStream ps = new PrintStream(p.getOutputStream())) {
        ps.print(builtinOutput);
    }
    
    // Read and print the result
    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
    String line;
    while ((line = reader.readLine()) != null) {
        System.out.println(line);
    }
                try {
                    p.waitFor();
                } catch (InterruptedException ex) {
                    System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
                return;
} 
String lastCmd = commands[commands.length -1].get(0);
    if (isBuiltIn(lastCmd) && commands.length == 2) {
    // External | Built-in (like "ls | type exit")
    
    // Run the first command (external)
    ProcessBuilder pb = new ProcessBuilder(commands[0]);
    pb.directory(currentPath.toFile());
    Process p = pb.start();
    
    // Capture its output
    BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
    StringBuilder output = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
        output.append(line).append("\n");
    }
                try {
                    p.waitFor();
                } catch (InterruptedException ex) {
                    System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
    
    // Feed that output as "input" to the built-in?
    // Wait—built-ins don't read from stdin! (except cat, which we haven't implemented)
    // For 'type', it ignores stdin anyway
    
    // So just run the built-in normally!
    executeBuiltin(commands[1]);
    return;
}
else {
    // All external commands—use the original pipeline
    try
    {
        executePipedCommands(commands);
    }catch (InterruptedException ex) {
                    System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
                }
    
}
        }
        else
        {
            List<String> tokens = tokenize(full_command);// tokenizing the full command into parts

            executeBuiltin(tokens);
        }

        if (isRedirect || isStderrRedirect) {
            System.setOut(consoleOut);
            System.setErr(consoleErr);
            if (fileOut != null) fileOut.close();
            if (fileErr != null) fileErr.close();
        }
    }
    boolean isBuiltIn (String s)
    {
        for ( String builtIn : shell_commands)
        {
            if(s.equals(builtIn))
            {
                return true;
            }
        }
        return false;
    }
    void executePipedCommands(List<String>[] commands) throws IOException, InterruptedException {
    List<ProcessBuilder> builders = new ArrayList<>();
    
        for (int i = 0; i < commands.length; i++) {
        ProcessBuilder pb = new ProcessBuilder(commands[i]);
        pb.directory(currentPath.toFile());
        builders.add(pb);
        }
    
        List<Process> processes = ProcessBuilder.startPipeline(builders);
    
        // Read output from LAST process
        Process lastProcess = processes.get(processes.size() - 1);
        InputStream is = lastProcess.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
        }
    
    // Read errors from ALL processes
        for (Process p : processes) {
            InputStream es = p.getErrorStream();
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(es));
            while ((line = errorReader.readLine()) != null) {
                System.err.println(line);
            }
        }
    
    // Wait for all processes
        for (Process p : processes) {
            p.waitFor();
        }
    }
    String executeBuiltinWithCapture(List<String> tokens) {
    // Save original System.out
    PrintStream originalOut = System.out;
    
    // Create capturing streams
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream captureStream = new PrintStream(baos);
    
    // Redirect
    System.setOut(captureStream);
    
    // Execute (it will print to our stream)
    executeBuiltin(tokens);
    
    // Restore original
    System.setOut(originalOut);
    captureStream.close();
    
    return baos.toString();
}
void executeBuiltin(List<String> tokens) {
    if (!tokens.isEmpty()) {
            String cmd = tokens.get(0);
            List<String> args = tokens.subList(1, tokens.size());

            switch (cmd) {
                case "cd":
                    cd(args.isEmpty() ? null : args.get(0));
                    break;
                case "pwd":
                    System.out.println(currentPath.toString());
                    break;
                case "exit":
                    System.exit(0);
                    break;
                case "echo":
                    echo(args);
                    break;
                case "type":
                    run_type(args.isEmpty() ? null : args.get(0));
                    break;
                case "cat":
                    cat(args);
                    break;
                case "history": 
                    historyOperations(args);
                    break;
                default:
                    String executablePath = findExecutable(cmd);
                    if (executablePath != null) {
                        executeExternal(tokens);
                    } else {
                        System.out.println(cmd + ": command not found");
                    }
                    break;
            }
          }
}

    // ------------------------------------------------------------------ cat
    void cat(List<String> args) {
        try {
            for (String file : args) {
                Files.copy(Path.of(file), System.out);// copying the file contents into the terminal
            }
        } catch (IOException e) {
            System.err.println("cat: " + e.getMessage() + ": No such file or directory");
        }
    }

    // ----------------------------------------------------------------- echo
    void echo(List<String> args) {
        System.out.println(String.join(" ", args));
    }

    // ------------------------------------------------------------ tokenize
    List<String> tokenize(String argument) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingle = false;
        boolean inDouble = false;
        boolean escapeNext = false;
        int i = 0;
        


        while (i < argument.length()) {
            char ch = argument.charAt(i);

            if (escapeNext) {
                current.append(ch);
                escapeNext = false;
                i++;
                continue;
            }

            if (ch == '\\' && !inSingle) {
                escapeNext = true;
                i++;
                continue;
            }

            if (inSingle) {
                if (ch == '\'') {
                    inSingle = false;
                    i++;
                } else {
                    current.append(ch);
                    i++;
                }
            } else if (inDouble) {
                if (ch == '"') {
                    inDouble = false;
                    i++;
                } else if (ch == '\\' && i + 1 < argument.length()) {
                    char next = argument.charAt(i + 1);
                    if (next == '"' || next == '\\' || next == '$') {
                        current.append(next);
                        i += 2;
                    } else {
                        current.append(ch);
                        i++;
                    }
                } else {
                    current.append(ch);
                    i++;
                }
            } else {
                if (ch == '\'') {
                    inSingle = true;
                    i++;
                } else if (ch == '"') {
                    inDouble = true;
                    i++;
                } else if (ch == ' ' || ch == '\t') {
                    if (current.length() > 0) {
                        tokens.add(current.toString());
                        current.setLength(0);
                    }
                    i++;
                } else {
                    current.append(ch);
                    i++;
                }
            }
        }

        if (escapeNext) {
            current.append('\\');
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    // ------------------------------------------------------------------- cd
    void cd(String path) {
        try {
            Path newPath;

            if (path == null || path.isEmpty() || path.equals("~")) {
                home = System.getenv("HOME");
                if (home == null) home = System.getProperty("user.home");
                newPath = Paths.get(home);
            } else if (path.equals("-")) {
                newPath = previousPath;
                System.out.println(newPath.toString());
            } else if (path.startsWith("~")) {
                home = System.getenv("HOME");
                if (home == null) home = System.getProperty("user.home");
                newPath = (path.length() == 1)
                        ? Paths.get(home)
                        : Paths.get(home + path.substring(1));
            } else if (path.startsWith("/")) {
                newPath = Paths.get(path);
            } else {
                newPath = currentPath.resolve(path);
            }

            newPath = newPath.normalize();
            File newDir = newPath.toFile();

            if (newDir.exists() && newDir.isDirectory()) {
                previousPath = currentPath;
                currentPath = newPath;
            } else {
                System.err.println("cd: " + path + ": No such file or directory");
            }

        } catch (Exception e) {
            System.err.println("cd: " + e.getMessage());
        }
    }

    // --------------------------------------------------------------- type
    void run_type(String commandName) {
        if (commandName == null || commandName.isEmpty()) {
            System.out.println("type: missing argument");
            return;
        }
        commandName = commandName.trim();
        boolean found = false;

        for (String s : shell_commands) {
            if (s.equals(commandName)) {
                System.out.println(commandName + " is a shell builtin");
                found = true;
                break;
            }
        }

        if (!found) {
            String executablePath = findExecutable(commandName);
            if (executablePath != null) {
                System.out.println(commandName + " is " + executablePath);
                found = true;
            }
        }

        if (!found) {
            System.out.println(commandName + ": not found");
        }
    }

    // -------------------------------------------------------- findExecutable
    String findExecutable(String commandName) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isEmpty()) return null;

        String sep = System.getProperty("os.name").toLowerCase().contains("windows") ? ";" : ":";
        String[] directories = pathEnv.split(sep);

        for (String dir : directories) {
            if (dir.isEmpty()) dir = ".";
            Path fullPath = Paths.get(dir, commandName);
            try {
                if (Files.exists(fullPath) &&
                    Files.isRegularFile(fullPath) &&
                    Files.isExecutable(fullPath)) {
                    return fullPath.toAbsolutePath().toString();
                }
            } catch (SecurityException e) {
                // skip inaccessible directories
            }
        }
        return null;
    }

    // --------------------------------------------------------- executeExternal
   void executeExternal(List<String> tokens) {
    try {
        ProcessBuilder pb = new ProcessBuilder(tokens);
        pb.directory(currentPath.toFile());
        Process process = pb.start();
        
        // Use try-with-resources
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
             BufferedReader error_reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
            while ((line = error_reader.readLine()) != null) {
                System.err.println(line);
            }
        }
        process.waitFor();
    } catch (IOException | InterruptedException e) {
        System.err.println("Error executing command: " + e.getMessage());
    }
}
    String longestCommonPrefix_decrementing (String s[])
    {
        if(( s == null) || ( s.length == 0))
        {
            return "";
        }
        else 
        {
            String prefix = s[0];
            int i=0;
            for ( i =0 ;i < s.length ; i++)
            {
                while( s[i].indexOf(prefix) != 0)
                {
                    prefix = prefix.substring(0 , prefix.length() -1);
                }
            }
            if(prefix.length() == 0)
            {
                return "";
            }
            else 
            {
                return prefix;
            }
        }
    }
    String longestCommonPrefix_incrementing(String s[])
    {
        if(s == null || s.length == 0)
        {
            return "";
        }
        else 
        {
            char currentChar ;
            int i=0 , j=0;
            for ( i=0 ; i < s[0].length() ; i++)
            {
                currentChar = s[0].charAt(i);
                // comparing this character accross all the strings
                for ( j=0 ; j< s.length ; j++)
                {
                    if( i >= s[j].length() || currentChar != s[j].charAt(i))
                    {
                        return s[0].substring(0,i);
                    }
                }
            }
            return s[0];
        }
    }
    private void setupShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            String histFile = System.getenv("HISTFILE");
                if (histFile != null && !histFile.trim().isEmpty()) {
                saveHistoryOnExit(histFile);
            }
        }));
    }
    void saveHistoryOnExit(String s)
    {
        Path path = Paths.get(s).normalize();
            
            try
            {
                // Create parent directories if needed
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
                Files.write(path , commandHistory);
            }
            catch(IOException e)
            {
                System.out.println("could not save hstory to file"+ e.getMessage());
            }
    }
    public void history(List<String> args)
    {
        int size = commandHistory.size();
        int displayCounter = 0;
        int i=0;
        if(args.isEmpty())
        {
            normalHistory();
        }
        else 
        {
            try
            {
                int limit = Integer.parseInt(args.get(0));
            int startIndex = size - limit;
            displayCounter = (historyCounter - commandHistory.size() + startIndex + 1);
            if(startIndex < 0)// if the user asks for more commands than the actual size        
            {
                startIndex =0;
            }
            for(i= startIndex ; i< size; i++)
            {
                System.out.println((displayCounter) + " " + commandHistory.get(i));
                displayCounter ++;
            }
            }catch (NumberFormatException e) {
            System.err.println("history: invalid number: " + args.get(0));
            }   
        } 
    }
    
    public void normalHistory()
    {
        int displayCounter = 0;
        if( commandHistory.size() == 0 )
        {
            System.out.println("No commands found");
        }
        else 
        {
            displayCounter = (historyCounter - commandHistory.size() + 1);
            for (int i = 0; i < commandHistory.size(); i++) {
            System.out.println("    "+ (displayCounter + i) + " " + commandHistory.get(i));
        }
        }
    }
    void addHistory(String s)
    {
        if( s != null && s.trim().isEmpty() == false) 
        {
            
                if(commandHistory.size()>= MAX_SIZE)
                {
                    commandHistory.removeFirst();// removing the first command
                    commandHistory.addLast(s); // adding a new element to the Linked List
                }
                else if(commandHistory.size() < MAX_SIZE)
                {
                    commandHistory.addLast(s); // adding a new element to the Linked List
                    historyCounter ++;
                }
                
            
        }  
    }
    void loadHistory(String s)
    {
        Path path = Paths.get(s).normalize();
        File file = path.toFile();
        if(file.exists() && file.isFile())
        {
            try {
                List<String> loadedHistory = Files.readAllLines(path);
                for(String lines : loadedHistory)
                {
                    addHistory(lines);
                }
                lastWrittenIndex = commandHistory.size();
            } catch (IOException ex) {
                System.getLogger(Main.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
            }
        }
    }
    void historyOperations( List<String> args) 
    {
        if(args.isEmpty())
        {
            history(args);
            return;
        }
        String symbol = args.get(0);
        if(symbol.equals("-r"))
        {
            readHistory(args);
        }
        else if(symbol.equals("-w"))
            {
                writeHistory(args);
            }
        else if(symbol.equals("-c"))
            {
                clearHistory();
            }       
        else if(symbol.equals("-d"))
        {

        }
        else if(symbol.equals("-a"))
        {
            appendHistory(args);
        }
        else if(symbol.equals("-n"))
        {
            readNewHistory(args);
        }
        else if(symbol.equals("-s"))
        {

        }
        else if(symbol.equals("-p"))
        {
            printExpanded(args);
        }
        else 
        {
            history(args);
        }
    }   
    void readHistory(List<String> args)
    {
        if(args.size()<2)
        {
            System.out.println("Usage: history -r <path_to_history_file>");
            return;
        }
        String path_str = args.get(1);
        Path path = Paths.get(path_str);
        if (path.toString().isEmpty() == false) {
        path = path.normalize();
        File file = path.toFile();
        
        if (file.exists() && file.isFile()) {  // Should be a file
            try {
                // Read all lines from the history file
                List<String> historyLines = Files.readAllLines(path);
                
                // For each line in the file, add to history list
                for (String line : historyLines) {
                    // Skip empty lines if needed
                    if (!line.trim().isEmpty()) {
                        // Add to your in-memory history list
                        addHistory(line);
                        //historyCounter ++ ;
                    }
                }
                lastWrittenIndex = commandHistory.size();
            } catch (IOException e) {
                System.out.println("Error reading history file: " + e.getMessage());
            }
        } else {
            System.out.println("No such file: " + path);
        }
    } else {
        System.out.println("Please enter a path");
    }
    }
    // -w : Write current in-memory history to a file (overwrites)
void writeHistory(List<String> args) {
    if (args.size() < 2) {
        System.err.println("Usage: history -w <path_to_history_file>");
        return;
    }

    Path path = Paths.get(args.get(1)).normalize();

    try {
        // Create parent directories if they don't exist
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        // Write all in-memory history lines, one per line, overwriting the file
        Files.write(path, commandHistory);
        lastWrittenIndex = commandHistory.size();
        
    } catch (IOException e) {
        System.err.println("history: error writing file: " + e.getMessage());
    }
}
// -c : Clear the in-memory history list entirely
void clearHistory() {
    commandHistory.clear();

    // Reset the counter so numbering restarts from 1 cleanly
    historyCounter = 0;
    lastWrittenIndex = 0;
}
// -n : Read history file, append only lines not yet loaded this session
// Tracks how many lines were already loaded so it skips them on next call
private int linesLoadedFromFile = 0;

void readNewHistory(List<String> args) {
    if (args.size() < 2) {
        System.err.println("Usage: history -n <path_to_history_file>");
        return;
    }

    Path path = Paths.get(args.get(1)).normalize();
    File file = path.toFile();

    if (!file.exists() || !file.isFile()) {
        System.err.println("history: no such file: " + path);
        return;
    }

    try {
        List<String> allLines = Files.readAllLines(path);

        // Only process lines we haven't seen from this file before
        List<String> newLines = allLines.subList(
            Math.min(linesLoadedFromFile, allLines.size()),
            allLines.size()
        );

        for (String line : newLines) {
            if (!line.trim().isEmpty()) {
                addHistory(line);
            }
        }

        // Remember how many lines we've now consumed from this file
        linesLoadedFromFile = allLines.size();

    } catch (IOException e) {
        System.err.println("history: error reading file: " + e.getMessage());
    }
}
// -p : Expand and print history expressions without executing them
void printExpanded(List<String> args) {
    if (args.size() < 2) {
        System.err.println("Usage: history -p <expression> [expression...]");
        return;
    }

    // Process every argument after -p
    for (int i = 1; i < args.size(); i++) {
        String expr = args.get(i);
        String expanded = expandHistoryExpression(expr);
        if (expanded != null) {
            System.out.println(expanded);
        }
        // If null, the error was already printed inside expandHistoryExpression
    }
}

// Resolves !! , !n , !-n , !prefix  — returns null on failure
private String expandHistoryExpression(String expr) {
    if (expr.equals("!!")) {
        // Last command
        if (commandHistory.isEmpty()) {
            System.err.println("history: !!: event not found");
            return null;
        }
        return commandHistory.getLast();
    }

    if (expr.startsWith("!")) {
        String body = expr.substring(1);

        // !-n  →  nth command from the end  (!-1 == last, !-2 == second to last)
        if (body.startsWith("-")) {
            try {
                int offset = Integer.parseInt(body); // e.g. -2
                int index = commandHistory.size() + offset; // size-2
                if (index < 0 || index >= commandHistory.size()) {
                    System.err.println("history: " + expr + ": event not found");
                    return null;
                }
                return commandHistory.get(index);
            } catch (NumberFormatException e) {
                // Not a number — fall through to prefix search
            }
        }

        // !n  →  absolute history number
        try {
            int n = Integer.parseInt(body);
            // Convert absolute number to index in our bounded list
            int startNumber = historyCounter - commandHistory.size() + 1;
            int index = n - startNumber;
            if (index < 0 || index >= commandHistory.size()) {
                System.err.println("history: " + expr + ": event not found");
                return null;
            }
            return commandHistory.get(index);
        } catch (NumberFormatException e) {
            // Not a number — prefix search
        }

        // !prefix  →  most recent command starting with prefix
        for (int i = commandHistory.size() - 1; i >= 0; i--) {
            if (commandHistory.get(i).startsWith(body)) {
                return commandHistory.get(i);
            }
        }

        System.err.println("history: " + expr + ": event not found");
        return null;
    }

    // Not a history expression — return as-is (bash behaviour)
    return expr;
}


void appendHistory(List<String> args) {
    if (args.size() < 2) {
        System.err.println("Usage: history -a <path_to_history_file>");
        return;
    }
    
    String path_str = args.get(1);
    if (path_str == null || path_str.trim().isEmpty()) {
        System.out.println("Please enter a valid path");
        return;
    }
    
    Path path = Paths.get(path_str).normalize();
    
    try {
        // Create parent directories if needed
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        
        // Get only commands that haven't been written yet
        List<String> unsavedCommands = new ArrayList<>();
        for (int i = lastWrittenIndex; i < commandHistory.size(); i++) {
            unsavedCommands.add(commandHistory.get(i));
        }
        
        // Append unsaved commands to file
        if (!unsavedCommands.isEmpty()) {
            try (FileWriter fw = new FileWriter(path.toString(), true)) {
                for (String cmd : unsavedCommands) {
                    fw.write(cmd + "\n");
                }
            }
            // Update the counter to mark these as written
            lastWrittenIndex = commandHistory.size();
        }
        
    } catch (IOException e) {
        System.err.println("Error appending to history file: " + e.getMessage());
    }
}

    public static void main(String[] args) {
        try {
            Main ob = new Main();
            ob.input();
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}