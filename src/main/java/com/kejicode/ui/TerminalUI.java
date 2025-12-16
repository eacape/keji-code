package com.kejicode.ui;

import com.kejicode.agent.CodeAssistantAgent;
import lombok.extern.slf4j.Slf4j;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;

/**
 * 基于终端的用户界面
 */
@Slf4j
public class TerminalUI {

    private final CodeAssistantAgent agent;
    private final Terminal terminal;
    private final LineReader lineReader;
    private boolean running;

    public TerminalUI(CodeAssistantAgent agent) throws IOException {
        this.agent = agent;
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .build();

        this.lineReader = LineReaderBuilder.builder()
            .terminal(terminal)
            .build();

        this.running = false;
    }

    public void start() {
        running = true;
        printWelcome();

        while (running) {
            try {
                String input = lineReader.readLine("\n> ");

                if (input == null || input.trim().isEmpty()) {
                    continue;
                }

                // 处理特殊命令
                if (handleCommand(input.trim())) {
                    continue;
                }

                // 通过 agent 处理用户消息
                String response = agent.processUserMessage(input);
                printResponse(response);

            } catch (UserInterruptException e) {
                // 用户按下 Ctrl+C
                if (confirmExit()) {
                    break;
                }
            } catch (Exception e) {
                log.error("主循环出错", e);
                printError("发生错误: " + e.getMessage());
            }
        }

        shutdown();
    }

    private boolean handleCommand(String input) {
        if (input.startsWith("/")) {
            String command = input.substring(1).trim();
            String[] parts = command.split("\\s+", 2);
            String mainCommand = parts[0].toLowerCase();

            switch (mainCommand) {
                case "exit", "quit":
                    running = false;
                    return true;

                case "clear":
                    terminal.puts(org.jline.utils.InfoCmp.Capability.clear_screen);
                    terminal.flush();
                    return true;

                case "help":
                    printHelp();
                    return true;

                case "reset":
                    agent.clearHistory();
                    println("对话历史已清除。");
                    return true;

                case "version":
                    println("KejiCode (柯基Code) v1.0.0");
                    return true;

                case "model":
                    handleModelCommand(parts.length > 1 ? parts[1] : "");
                    return true;

                case "switch":
                    handleSwitchCommand(parts.length > 1 ? parts[1] : "");
                    return true;

                case "params":
                    handleParamsCommand(parts.length > 1 ? parts[1] : "");
                    return true;

                default:
                    printError("未知命令: " + mainCommand);
                    println("使用 /help 查看可用命令");
                    return true;
            }
        }

        return false;
    }

    /**
     * 处理模型相关命令
     */
    private void handleModelCommand(String args) {
        if (args.isEmpty()) {
            // 显示当前模型信息
            String info = agent.getCurrentModelInfo();
            println("\n" + info);
        } else {
            printError("未知的模型命令参数: " + args);
            println("使用 /model 查看当前模型信息");
            println("使用 /switch 切换模型");
        }
    }

    /**
     * 处理模型切换命令
     */
    private void handleSwitchCommand(String args) {
        if (args.isEmpty()) {
            println("\n使用方法: /switch <provider> <model_name> [api_key]");
            println("\n支持的提供商:");
            println("  anthropic  - Anthropic Claude");
            println("  openai     - OpenAI GPT");
            println("  dashscope  - 阿里百炼");
            println("  deepseek   - DeepSeek");
            println("\n示例:");
            println("  /switch anthropic claude-3-5-sonnet-20241022");
            println("  /switch openai gpt-4");
            println("  /switch dashscope qwen-max");
            println("  /switch deepseek deepseek-chat");
            return;
        }

        String[] parts = args.split("\\s+");
        if (parts.length < 2) {
            printError("参数不足。使用: /switch <provider> <model_name> [api_key]");
            return;
        }

        String provider = parts[0];
        String modelName = parts[1];
        String apiKey = parts.length > 2 ? parts[2] : null;

        println("\n正在切换模型...");
        String result = agent.switchModel(provider, modelName, apiKey);
        println(result);
    }

    /**
     * 处理参数调整命令
     */
    private void handleParamsCommand(String args) {
        if (args.isEmpty()) {
            println("\n使用方法: /params [temperature=<value>] [max_tokens=<value>]");
            println("\n参数说明:");
            println("  temperature  - 控制输出随机性 (0.0-1.0)");
            println("  max_tokens   - 最大令牌数 (>0)");
            println("\n示例:");
            println("  /params temperature=0.5");
            println("  /params max_tokens=2048");
            println("  /params temperature=0.7 max_tokens=4096");
            return;
        }

        Double temperature = null;
        Integer maxTokens = null;

        String[] parts = args.split("\\s+");
        for (String part : parts) {
            if (part.startsWith("temperature=")) {
                try {
                    temperature = Double.parseDouble(part.substring(12));
                } catch (NumberFormatException e) {
                    printError("无效的温度值: " + part.substring(12));
                    return;
                }
            } else if (part.startsWith("max_tokens=")) {
                try {
                    maxTokens = Integer.parseInt(part.substring(11));
                } catch (NumberFormatException e) {
                    printError("无效的令牌数: " + part.substring(11));
                    return;
                }
            } else {
                printError("未知参数: " + part);
                return;
            }
        }

        String result = agent.updateModelParameters(temperature, maxTokens);
        println("\n" + result);
    }

    private void printWelcome() {
        println("╔════════════════════════════════════════════════════════════╗");
        println("║          KejiCode (柯基Code) - 智能编程助手                   ║");
        println("║             Powered by Java & LangChain4j                  ║");
        println("║                                                            ║");
        println("║               一个 AI 驱动的终端编程工具                       ║");
        println("║            输入您的需求或使用 /help 查看可用命令                 ║");
        println("╚════════════════════════════════════════════════════════════╝");
    }

    private void printHelp() {
        println("\n╔═══════════════════════════════════════════════════════════════╗");
        println("║                        命令帮助                                 ║");
        println("╚═══════════════════════════════════════════════════════════════╝");

        println("\n【基础命令】");
        println("  /help     - 显示此帮助信息");
        println("  /clear    - 清屏");
        println("  /reset    - 清除对话历史");
        println("  /version  - 显示版本信息");
        println("  /exit     - 退出程序");

        println("\n【模型管理】");
        println("  /model                          - 显示当前模型信息");
        println("  /switch <provider> <model>      - 切换 AI 模型");
        println("  /params temperature=<val>       - 调整温度参数 (0.0-1.0)");
        println("  /params max_tokens=<val>        - 调整最大令牌数");

        println("\n【模型切换示例】");
        println("  /switch anthropic claude-3-5-sonnet-20241022");
        println("  /switch openai gpt-4");
        println("  /switch dashscope qwen-max");
        println("  /switch deepseek deepseek-chat");

        println("\n【内置工具】");
        println("  Read      - 从文件系统读取文件");
        println("  Write     - 创建或覆盖文件");
        println("  Edit      - 对文件进行精确编辑");
        println("  Glob      - 按模式查找文件");
        println("  Grep      - 搜索文件内容");
        println("  Bash      - 执行 shell 命令");
        println("  Git       - 执行 Git 版本管理操作");
        println("  Npm       - 执行 NPM 包管理操作");
        println("  Maven     - 执行 Maven 构建操作");

        println("\n【使用示例】");
        println("  - '读取 pom.xml 文件'");
        println("  - '查找 src/ 目录下的所有 Java 文件'");
        println("  - '搜索代码中的 TODO 注释'");
        println("  - '创建一个新的 README.md 文件'");
        println("  - '执行 git status 命令'");
        println("  - '运行 npm install'");
        println("  - '执行 mvn clean install'");
    }

    private void printResponse(String response) {
        println("\n" + response);
    }

    private void printError(String error) {
        println("\n错误: " + error);
    }

    private void println(String text) {
        terminal.writer().println(text);
        terminal.flush();
    }

    private boolean confirmExit() {
        try {
            String response = lineReader.readLine("\n确定要退出吗? (y/n): ");
            return response != null && response.trim().equalsIgnoreCase("y");
        } catch (Exception e) {
            return true;
        }
    }

    private void shutdown() {
        try {
            println("\n再见!");
            terminal.close();
        } catch (IOException e) {
            log.error("关闭终端出错", e);
        }
    }
}
