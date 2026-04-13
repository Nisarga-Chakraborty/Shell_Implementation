# 🐚 Java Shell

A Unix-like shell implementation in Java featuring command execution, pipelines, redirections, history management, tab completion, and more.

## 📋 Overview

This project is a fully functional command-line shell written in Java that mimics the behavior of traditional Unix shells (bash, zsh). It provides an interactive environment for executing commands, managing processes, and controlling input/output streams.

## ✨ Features

### Core Functionality
- **Command Execution** - Run both built-in commands and external programs
- **PATH Resolution** - Automatically finds executables in system PATH
- **Command Parsing** - Handles quoted arguments, escape characters, and spaces

### I/O Redirection
- Output redirection (`>`)
- Append output (`>>`)
- Error redirection (`2>`)
- Append error (`2>>`)

### Pipelines
- Connect multiple commands with pipes (`|`)
- Mix built-in and external commands in pipelines
- Proper stdin/stdout chaining between processes

### History Management
- Persistent command history across sessions via `HISTFILE`
- History display with numbered entries
- History expansion (`!!`, `!n`, `!-n`, `!prefix`)
- History operations:
  - `history -r <file>` - Read/append history from file
  - `history -w <file>` - Write history to file (overwrite)
  - `history -a <file>` - Append new commands to file
  - `history -c` - Clear history
  - `history -n <file>` - Read unread lines from file
  - `history -p <expr>` - Expand and print history expressions
  - `history <n>` - Show last n commands

### Interactive Features
- **Tab Completion** - Auto-completes commands, paths, and executables
- **Command History Navigation** - Browse previous commands (via JLine)

## 🛠️ Built-in Commands

| Command | Description |
|---------|-------------|
| `cd [path]` | Change current directory (supports `~`, `-`, relative/absolute paths) |
| `pwd` | Print working directory |
| `echo [text...]` | Display text to stdout |
| `type <command>` | Display command type (builtin or executable path) |
| `cat <file...>` | Display file contents |
| `history [options]` | Manage and display command history |
| `exit` | Exit the shell |

## 🔧 Technical Implementation

### Architecture Components

| Component | Description |
|-----------|-------------|
| **Command Parser** | Tokenizes input with support for quotes and escape characters |
| **Command Executor** | Routes commands to built-ins or external processes |
| **Process Manager** | Handles external process creation and management |
| **Pipeline Handler** | Chains multiple processes with stdin/stdout redirection |
| **Redirection Handler** | Manages file descriptor redirections |
| **History Manager** | Maintains in-memory history and file persistence |
| **Tab Completer** | Provides intelligent command and path completion |
| **Environment Manager** | Tracks current directory, previous directory, and PATH |

### Key Design Decisions

- **ProcessBuilder** - Used for external process creation with proper environment setup
- **JLine Library** - Provides rich terminal interaction (tab completion, history navigation)
- **NIO.2 (Files API)** - Modern file operations with better error handling
- **LinkedList** - Efficient bounded history with automatic oldest removal

## 📦 Requirements

- **Java 11+** (uses NIO.2 and modern APIs)
- **JLine 3.29.0** (for terminal interaction)

## 🚀 Installation & Usage

### Clone and Build

```bash
git clone https://github.com/Nisarga-Chakraborty/Shell_Implementation.git
ls

# Download JLine library
wget https://repo1.maven.org/maven2/org/jline/jline/3.29.0/jline-3.29.0.jar

# Compile
javac -cp ".:jline-3.29.0.jar" src/main/java/Main.java

# Run
java -cp ".:jline-3.29.0.jar:src/main/java" Main
