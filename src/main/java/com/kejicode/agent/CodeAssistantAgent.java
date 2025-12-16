package com.kejicode.agent;

import com.kejicode.tools.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 核心 Agent - 协调 AI 模型和可用工具
 */
@Slf4j
public class CodeAssistantAgent {

    private ChatLanguageModel chatModel;
    private final Map<String, Tool> tools;
    private final ObjectMapper objectMapper;
    private final List<ChatMessage> conversationHistory;

    // 保存当前配置以支持模型切换
    private String currentProvider;
    private String currentApiKey;
    private String currentModelName;
    private double currentTemperature;
    private int currentMaxTokens;
    private String currentCustomUrl;
    private String currentCustomHeaders;

    public CodeAssistantAgent(String provider, String apiKey, String modelName,
                             double temperature, int maxTokens,
                             String customUrl, String customHeaders) {
        // 保存配置
        this.currentProvider = provider;
        this.currentApiKey = apiKey;
        this.currentModelName = modelName;
        this.currentTemperature = temperature;
        this.currentMaxTokens = maxTokens;
        this.currentCustomUrl = customUrl;
        this.currentCustomHeaders = customHeaders;

        this.chatModel = createChatModel(provider, apiKey, modelName, temperature, maxTokens, customUrl, customHeaders);
        this.tools = new HashMap<>();
        this.objectMapper = new ObjectMapper();
        this.conversationHistory = new ArrayList<>();

        // 注册所有工具
        registerTool(new ReadFileTool());
        registerTool(new WriteFileTool());
        registerTool(new EditFileTool());
        registerTool(new GlobTool());
        registerTool(new GrepTool());
        registerTool(new BashTool());
        registerTool(new GitTool());
        registerTool(new NpmTool());
        registerTool(new MavenTool());

        // 添加系统消息
        conversationHistory.add(SystemMessage.from(getSystemPrompt()));

        log.info("已初始化 {} 模型: {}", provider, modelName);
    }

    /**
     * 根据提供商创建对应的聊天模型
     */
    private ChatLanguageModel createChatModel(String provider, String apiKey, String modelName,
                                             double temperature, int maxTokens,
                                             String customUrl, String customHeaders) {
        log.info("正在创建 {} 提供商的模型...", provider);

        return switch (provider.toLowerCase()) {
            case "anthropic" -> AnthropicChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

            case "openai" -> OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();

            case "dashscope" -> QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature((float) temperature)
                .maxTokens(maxTokens)
                .build();

            case "deepseek" -> {
                // DeepSeek 使用 OpenAI 兼容接口
                OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .temperature(temperature)
                    .maxTokens(maxTokens);

                // DeepSeek API 端点
                if (customUrl != null && !customUrl.isEmpty()) {
                    builder.baseUrl(customUrl);
                } else {
                    builder.baseUrl("https://api.deepseek.com");
                }

                yield builder.build();
            }

            case "custom" -> {
                // 自定义 API (使用 OpenAI 兼容接口)
                if (customUrl == null || customUrl.isEmpty()) {
                    throw new IllegalArgumentException("自定义提供商需要配置 ai.model.custom.url");
                }

                OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .baseUrl(customUrl);

                // 处理自定义 headers (如果需要)
                // TODO: LangChain4j 目前可能不直接支持自定义 headers,可能需要额外处理

                yield builder.build();
            }

            default -> throw new IllegalArgumentException("不支持的模型提供商: " + provider);
        };
    }

    private void registerTool(Tool tool) {
        tools.put(tool.getName(), tool);
        log.debug("已注册工具: {}", tool.getName());
    }

    public String processUserMessage(String userMessage) {
        log.info("处理用户消息: {}", userMessage);

        // 添加用户消息到历史
        conversationHistory.add(UserMessage.from(userMessage));

        try {
            // 从 AI 生成响应，传递工具规格
            Response<AiMessage> response = chatModel.generate(
                conversationHistory,
                getToolSpecifications()  // 传递工具规格给 AI
            );
            AiMessage aiMessage = response.content();

            // 添加 AI 响应到历史
            conversationHistory.add(aiMessage);

            // 检查 AI 是否要使用工具
            if (aiMessage.hasToolExecutionRequests()) {
                return handleToolExecution(aiMessage.toolExecutionRequests());
            }

            // 返回文本响应
            return aiMessage.text();

        } catch (Exception e) {
            log.error("处理消息时出错", e);
            return "错误: " + e.getMessage();
        }
    }

    private String handleToolExecution(List<ToolExecutionRequest> toolRequests) {
        StringBuilder results = new StringBuilder();

        for (ToolExecutionRequest request : toolRequests) {
            String toolName = request.name();
            String arguments = request.arguments();

            // 向用户显示正在执行的操作
            String operationDesc = getOperationDescription(toolName, arguments);
            System.out.println("⚙️  " + operationDesc);

            Tool tool = tools.get(toolName);
            if (tool == null) {
                String error = "❌ 错误: 未知工具 " + toolName;
                results.append(error).append("\n");
                System.out.println(error);
                continue;
            }

            try {
                ToolResult result = tool.execute(arguments);

                if (result.isSuccess()) {
                    System.out.println("✅ 完成");
                } else {
                    String failure = "❌ 失败: " + result.getError();
                    results.append(failure).append("\n");
                    System.out.println(failure);
                }

                conversationHistory.add(ToolExecutionResultMessage.from(
                    request,
                    result.isSuccess() ? result.getOutput() : result.getError()
                ));

            } catch (Exception e) {
                String error = "❌ 错误: " + e.getMessage();
                log.error("执行工具出错", e);
                results.append(error).append("\n");
                System.out.println(error);
            }
        }

        // 工具执行后从 AI 获取后续响应
        int maxIterations = 1000;
        int iteration = 0;

        while (iteration < maxIterations) {
            iteration++;

            try {
                Response<AiMessage> followUp = chatModel.generate(
                    conversationHistory,
                    getToolSpecifications()
                );
                AiMessage followUpMessage = followUp.content();
                conversationHistory.add(followUpMessage);

                if (followUpMessage.hasToolExecutionRequests()) {
                    System.out.println();  // 空行分隔

                    for (ToolExecutionRequest request : followUpMessage.toolExecutionRequests()) {
                        String toolName = request.name();
                        String arguments = request.arguments();

                        String operationDesc = getOperationDescription(toolName, arguments);
                        System.out.println("⚙️  " + operationDesc);

                        Tool tool = tools.get(toolName);
                        if (tool == null) {
                            String error = "❌ 错误: 未知工具 " + toolName;
                            results.append(error).append("\n");
                            System.out.println(error);
                            continue;
                        }

                        try {
                            ToolResult result = tool.execute(arguments);

                            if (result.isSuccess()) {
                                System.out.println("✅ 完成");
                            } else {
                                String failure = "❌ 失败: " + result.getError();
                                results.append(failure).append("\n");
                                System.out.println(failure);
                            }

                            conversationHistory.add(ToolExecutionResultMessage.from(
                                request,
                                result.isSuccess() ? result.getOutput() : result.getError()
                            ));

                        } catch (Exception e) {
                            String error = "❌ 错误: " + e.getMessage();
                            log.error("执行工具出错", e);
                            results.append(error).append("\n");
                            System.out.println(error);
                        }
                    }
                    continue;
                }

                // 完成所有操作
                System.out.println("\n✨ 操作完成\n");

                String textResponse = followUpMessage.text();
                if (textResponse != null && !textResponse.isEmpty()) {
                    return textResponse;
                } else {
                    return results.toString();
                }

            } catch (Exception e) {
                log.error("获取后续响应时出错", e);
                return results.toString();
            }
        }

        log.warn("达到最大迭代次数限制");
        return results.toString() + "\n(达到最大工具调用次数限制)";
    }

    /**
     * 根据工具名称和参数生成人类可读的操作描述
     */
    private String getOperationDescription(String toolName, String arguments) {
        try {
            JsonNode params = objectMapper.readTree(arguments);

            switch (toolName) {
                case "Write":
                    String filePath = params.has("file_path") ? params.get("file_path").asText() : "未知文件";
                    return "创建文件: " + filePath;

                case "Edit":
                    String editFile = params.has("file_path") ? params.get("file_path").asText() : "未知文件";
                    return "编辑文件: " + editFile;

                case "Read":
                    String readFile = params.has("file_path") ? params.get("file_path").asText() : "未知文件";
                    return "读取文件: " + readFile;

                case "Bash":
                    String command = params.has("command") ? params.get("command").asText() : "未知命令";
                    return "执行命令: " + command;

                case "Git":
                    String gitCmd = params.has("command") ? params.get("command").asText() : "未知命令";
                    return "执行 Git: " + gitCmd;

                case "Npm":
                    String npmCmd = params.has("command") ? params.get("command").asText() : "未知命令";
                    return "执行 NPM: " + npmCmd;

                case "Maven":
                    String mvnCmd = params.has("command") ? params.get("command").asText() : "未知命令";
                    return "执行 Maven: " + mvnCmd;

                case "Glob":
                    String pattern = params.has("pattern") ? params.get("pattern").asText() : "未知模式";
                    return "搜索文件: " + pattern;

                case "Grep":
                    String searchPattern = params.has("pattern") ? params.get("pattern").asText() : "未知模式";
                    return "搜索内容: " + searchPattern;

                default:
                    return "执行工具: " + toolName;
            }
        } catch (Exception e) {
            return "执行工具: " + toolName;
        }
    }

    public List<ToolSpecification> getToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();

        for (Tool tool : tools.values()) {
            ToolSpecification spec = ToolSpecification.builder()
                .name(tool.getName())
                .description(tool.getDescription())
                .build();
            specs.add(spec);
        }

        return specs;
    }

    public void clearHistory() {
        conversationHistory.clear();
        conversationHistory.add(SystemMessage.from(getSystemPrompt()));
        log.info("对话历史已清除");
    }

    /**
     * 切换 AI 模型
     */
    public String switchModel(String provider, String modelName, String apiKey) {
        try {
            log.info("切换模型: {} - {}", provider, modelName);

            // 使用提供的参数或当前配置
            String newProvider = provider != null ? provider : this.currentProvider;
            String newModelName = modelName != null ? modelName : this.currentModelName;
            String newApiKey = apiKey != null ? apiKey : this.currentApiKey;

            // 创建新的聊天模型
            ChatLanguageModel newChatModel = createChatModel(
                newProvider,
                newApiKey,
                newModelName,
                this.currentTemperature,
                this.currentMaxTokens,
                this.currentCustomUrl,
                this.currentCustomHeaders
            );

            // 切换成功，更新配置
            this.chatModel = newChatModel;
            this.currentProvider = newProvider;
            this.currentModelName = newModelName;
            if (apiKey != null) {
                this.currentApiKey = newApiKey;
            }

            log.info("模型切换成功");
            return "已切换到 " + newProvider + " 的 " + newModelName + " 模型";

        } catch (Exception e) {
            log.error("切换模型失败", e);
            return "切换模型失败: " + e.getMessage();
        }
    }

    /**
     * 获取当前模型信息
     */
    public String getCurrentModelInfo() {
        return String.format("当前模型:\n提供商: %s\n模型名称: %s\n温度: %.1f\n最大令牌数: %d",
            currentProvider,
            currentModelName,
            currentTemperature,
            currentMaxTokens
        );
    }

    /**
     * 更新模型参数
     */
    public String updateModelParameters(Double temperature, Integer maxTokens) {
        try {
            boolean changed = false;

            if (temperature != null && temperature >= 0.0 && temperature <= 1.0) {
                this.currentTemperature = temperature;
                changed = true;
            }

            if (maxTokens != null && maxTokens > 0) {
                this.currentMaxTokens = maxTokens;
                changed = true;
            }

            if (changed) {
                // 重新创建模型以应用新参数
                this.chatModel = createChatModel(
                    this.currentProvider,
                    this.currentApiKey,
                    this.currentModelName,
                    this.currentTemperature,
                    this.currentMaxTokens,
                    this.currentCustomUrl,
                    this.currentCustomHeaders
                );
                log.info("模型参数已更新");
                return "模型参数已更新:\n温度: " + this.currentTemperature + "\n最大令牌数: " + this.currentMaxTokens;
            } else {
                return "未进行任何更改";
            }

        } catch (Exception e) {
            log.error("更新模型参数失败", e);
            return "更新参数失败: " + e.getMessage();
        }
    }

    private String getSystemPrompt() {
        return """
            你是一个有帮助的 AI 编程助手,集成在基于终端的开发工具中。
            你的角色是帮助开发者完成各种编程任务。

            【重要】你必须实际执行操作,而不是仅仅提供建议!

            当用户要求你:
            - "写一个XX程序" → 使用 Write 工具创建文件
            - "修改XX文件" → 使用 Edit 工具编辑文件
            - "创建XX项目" → 使用 Write 工具创建必要的文件
            - "运行XX命令" → 使用 Bash/Git/Npm/Maven 工具执行命令
            - "查看XX文件" → 使用 Read 工具读取文件
            - "搜索XX内容" → 使用 Grep 工具搜索

            你可以使用以下工具:
            - Read: 从文件系统读取文件
            - Write: 创建或覆盖文件 (当用户要求创建文件时必须使用!)
            - Edit: 对现有文件进行精确编辑
            - Glob: 查找匹配模式的文件
            - Grep: 使用正则表达式搜索文件内容
            - Bash: 执行 shell 命令
            - Git: 执行 Git 版本管理操作
            - Npm: 执行 NPM 包管理操作
            - Maven: 执行 Maven 构建操作

            工作流程:
            1. 理解用户需求
            2. 立即使用相应工具执行操作
            3. 确认操作结果
            4. 如果用户要求"写代码"、"创建文件",你必须使用 Write 工具,不能只返回代码片段!

            指导原则:
            - 主动执行,不要只是建议
            - 使用工具完成实际操作
            - 操作前验证文件路径
            - 清晰解释你的操作
            - 优雅地处理错误
            - 优先考虑代码质量和最佳实践

            帮助编写代码时:
            - 遵循项目现有的风格和约定
            - 编写清晰、可维护的代码
            - 为复杂逻辑添加注释
            - 考虑边界情况和错误处理

            记住: 你是一个实际操作的助手,不仅仅是代码顾问!
            """;
    }
}
