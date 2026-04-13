import org.jline.reader.*;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

public class TestJLine {
    public static void main(String[] args) throws Exception {
        Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();
        
        StringsCompleter completer = new StringsCompleter("hello", "world", "exit");
        
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(completer)
                .build();
        
        while (true) {
            String line = reader.readLine("test> ");
            if (line == null || line.equals("exit")) break;
            System.out.println("You typed: " + line);
        }
    }
}
