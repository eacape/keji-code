package com.kejicode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Git 版本管理工具
 */
@Slf4j
public class GitTool implements Tool {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getName() {
        return "Git";
    }

    @Override
    public String getDescription() {
        return """
            执行 Git 版本管理操作。支持常用的 Git 命令。

            参数:
            - command (必需): Git 命令，如 "status", "add .", "commit -m 'message'", "log", "diff" 等
            - working_directory (可选): 工作目录，默认为当前目录

            示例:
            {
              "command": "status"
            }

            {
              "command": "add .",
              "working_directory": "/path/to/repo"
            }

            {
              "command": "commit -m 'Update README'"
            }
            """;
    }

    @Override
    public ToolResult execute(String arguments) {
        try {
            JsonNode params = objectMapper.readTree(arguments);

            // 获取参数
            if (!params.has("command")) {
                return ToolResult.failure("缺少必需参数: command");
            }

            String command = params.get("command").asText();
            String workingDir = params.has("working_directory")
                    ? params.get("working_directory").asText()
                    : System.getProperty("user.dir");

            // 验证工作目录
            File dir = new File(workingDir);
            if (!dir.exists() || !dir.isDirectory()) {
                return ToolResult.failure("无效的工作目录: " + workingDir);
            }

            // 检查是否是 Git 仓库
            File gitDir = new File(dir, ".git");
            if (!gitDir.exists() && !command.equals("init") && !command.startsWith("clone")) {
                return ToolResult.failure("不是 Git 仓库: " + workingDir + "\n提示: 使用 'git init' 初始化仓库");
            }

            // 构建完整命令
            String fullCommand = "git " + command;

            log.debug("执行 Git 命令: {} 在目录: {}", fullCommand, workingDir);

            // 执行命令
            ProcessBuilder processBuilder = new ProcessBuilder();

            // 根据操作系统选择 shell
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder.command("cmd.exe", "/c", fullCommand);
            } else {
                processBuilder.command("sh", "-c", fullCommand);
            }

            processBuilder.directory(dir);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            // 读取输出
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            String result = output.toString().trim();
            if (result.isEmpty()) {
                result = "命令执行成功（无输出）";
            }

            if (exitCode == 0) {
                log.debug("Git 命令执行成功");
                return ToolResult.success("Git 命令: " + fullCommand + "\n工作目录: " + workingDir + "\n\n" + result);
            } else {
                log.warn("Git 命令执行失败，退出码: {}", exitCode);
                return ToolResult.failure("Git 命令执行失败（退出码 " + exitCode + "）:\n" + result);
            }

        } catch (Exception e) {
            log.error("执行 Git 命令时出错", e);
            return ToolResult.failure("执行 Git 命令时出错: " + e.getMessage());
        }
    }
}
