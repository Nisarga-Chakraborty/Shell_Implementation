import java.util.Scanner;

public class Main {
    int i = 0;
    char c = 'a';
    String full_command = "";
    StringBuilder command = new StringBuilder();

    void input() {
        Scanner ab = new Scanner(System.in);
        while (true) {
            System.out.print("$ ");
            full_command = ab.nextLine();

            // FIX 1: Reset command each iteration
            command.setLength(0);
            // FIX 2: Reset i and c to read from full_command
            i = 0;
            c = full_command.charAt(0);

            // FIX 3: Read from full_command using index, stop at space or end
            while (i < full_command.length() && c != ' ') {
                command.append(c);
                i++;
                if (i < full_command.length())
                    c = full_command.charAt(i);
                else
                    break;
            }

            switch (command.toString()) {
                case "cd":
                    System.out.println("cd command executed");
                    break;
                case "pwd":
                    System.out.println("pwd command executed");
                    break;
                case "exit":
                    System.exit(0);
                    break;
                case "echo":
                    run_echo();
                    break;
                default:
                    System.out.println(command + ":" + " command not found");
                    break;
            }
        }
    }

    void run_echo() {
        if (full_command.length() > 5) {
            String echoText = full_command.substring(5);
            System.out.println(echoText);
        } else {
            System.out.println();
        }
    }

    public static void main(String[] args) throws Exception {
        Main ob = new Main();
        ob.input();
    }
}