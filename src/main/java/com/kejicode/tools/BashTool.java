package com.kejicode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Bash 命令执行工具
 */
@Slf4j
public class BashTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TIMEOUT = 120000; // 2 分钟

    @Override
    public String getName() {
        return "Bash";
    }

    @Override
    public String getDescription() {
        return "执行 bash 命令,支持超时设置。用于终端操作如 git、npm 等。";
    }

    @Override
    public ToolResult execute(String parameters) throws Exception {
        try {
            JsonNode params = MAPPER.readTree(parameters);
            String command = params.get("command").asText();
            int timeout = params.has("timeout") ? params.get("timeout").asInt() : DEFAULT_TIMEOUT;

            log.debug("执行命令: {}", command);

            ProcessBuilder processBuilder = new ProcessBuilder();

            // 根据操作系统确定 shell
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                processBuilder.command("cmd.exe", "/c", command);
            } else {
                processBuilder.command("bash", "-c", command);
            }

            // 设置工作目录
            processBuilder.directory(new File(System.getProperty("user.dir")));
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

            // 等待完成并设置超时
            boolean completed = process.waitFor(timeout, TimeUnit.MILLISECONDS);

            if (!completed) {
                process.destroyForcibly();
                return ToolResult.failure("命令执行超时(超过 " + timeout + "ms)");
            }

            int exitCode = process.exitValue();
            String result = output.toString();

            if (exitCode == 0) {
                log.debug("命令执行成功");
                return ToolResult.success(result.isEmpty() ? "命令执行成功" : result);
            } else {
                log.warn("命令执行失败,退出码: {}", exitCode);
                return ToolResult.failure("命令执行失败,退出码 " + exitCode + ":\n" + result);
            }

        } catch (Exception e) {
            log.error("执行命令失败", e);
            return ToolResult.failure("执行命令失败: " + e.getMessage());
        }
    }
}
