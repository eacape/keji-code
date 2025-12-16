package com.kejicode;

import com.kejicode.agent.CodeAssistantAgent;
import com.kejicode.config.Configuration;
import com.kejicode.ui.TerminalUI;
import com.kejicode.utils.PathValidator;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

/**
 * Main entry point for the KejiCode (柯基Code) application
 */
@Slf4j
@Command(
    name = "kejicode",
    description = "柯基Code - AI驱动的智能终端编程助手",
    version = "1.0.0",
    mixinStandardHelpOptions = true
)
public class Main implements Callable<Integer> {

    @Option(
        names = {"-k", "--api-key"},
        description = "Anthropic API key (overrides ANTHROPIC_API_KEY env var)"
    )
    private String apiKey;

    @Option(
        names = {"-m", "--model"},
        description = "Model name to use (default: claude-3-5-sonnet-20241022)"
    )
    private String modelName;

    @Option(
        names = {"-d", "--dir"},
        description = "Working directory (default: current directory)"
    )
    private String workingDir;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose logging"
    )
    private boolean verbose;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        try {
            // 加载配置
            Configuration config = new Configuration();

            // 使用命令行参数覆盖配置
            String finalProvider = config.getModelProvider();
            String finalApiKey = apiKey != null ? apiKey : config.getApiKey();
            String finalModelName = modelName != null ? modelName : config.getModelName();
            String finalWorkingDir = workingDir != null ? workingDir : config.getWorkingDirectory();
            double temperature = config.getTemperature();
            int maxTokens = config.getMaxTokens();
            String customUrl = config.getCustomUrl();
            String customHeaders = config.getCustomHeaders();

            // 验证 API key
            if (finalApiKey == null || finalApiKey.isEmpty()) {
                System.err.println("错误: 未提供 API key。");
                System.err.println("请设置对应的环境变量或使用 --api-key 选项。");
                System.err.println("支持的环境变量:");
                System.err.println("  - ANTHROPIC_API_KEY (Anthropic Claude)");
                System.err.println("  - OPENAI_API_KEY (OpenAI)");
                System.err.println("  - DASHSCOPE_API_KEY (阿里百炼)");
                System.err.println("  - DEEPSEEK_API_KEY (DeepSeek)");
                return 1;
            }

            // 设置工作目录
            System.setProperty("user.dir", finalWorkingDir);

            // 初始化路径验证器
            PathValidator.initialize(finalWorkingDir);

            if (verbose) {
                System.out.println("正在启动 KejiCode (柯基Code)...");
                System.out.println("提供商: " + finalProvider);
                System.out.println("模型: " + finalModelName);
                System.out.println("工作目录: " + finalWorkingDir);
            }

            // 初始化 agent
            CodeAssistantAgent agent = new CodeAssistantAgent(
                finalProvider,
                finalApiKey,
                finalModelName,
                temperature,
                maxTokens,
                customUrl,
                customHeaders
            );

            // 启动终端 UI
            TerminalUI ui = new TerminalUI(agent);
            ui.start();

            return 0;

        } catch (Exception e) {
            log.error("Fatal error", e);
            System.err.println("Fatal error: " + e.getMessage());
            return 1;
        }
    }
}
