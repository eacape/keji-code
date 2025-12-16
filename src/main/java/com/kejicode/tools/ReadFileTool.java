package com.kejicode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kejicode.utils.PathValidator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 文件读取工具
 */
@Slf4j
public class ReadFileTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_LIMIT = 2000;

    @Override
    public String getName() {
        return "Read";
    }

    @Override
    public String getDescription() {
        return "从本地文件系统读取文件。支持行偏移和行数限制。";
    }

    @Override
    public ToolResult execute(String parameters) throws Exception {
        try {
            JsonNode params = MAPPER.readTree(parameters);
            String filePath = params.get("file_path").asText();
            int offset = params.has("offset") ? params.get("offset").asInt() : 0;
            int limit = params.has("limit") ? params.get("limit").asInt() : DEFAULT_LIMIT;

            // 验证路径是否在工作目录范围内
            Path path;
            try {
                path = PathValidator.validateAndNormalize(filePath);
            } catch (SecurityException e) {
                log.warn("路径安全检查失败: {}", e.getMessage());
                return ToolResult.failure("安全错误: " + e.getMessage());
            }

            String relativePath = PathValidator.toRelativePath(path);

            if (!Files.exists(path)) {
                return ToolResult.failure("文件不存在: " + relativePath);
            }

            if (Files.isDirectory(path)) {
                return ToolResult.failure("路径是目录而非文件: " + relativePath);
            }

            List<String> allLines = Files.readAllLines(path);

            if (allLines.isEmpty()) {
                return ToolResult.success("文件为空");
            }

            // 应用偏移和限制
            int startLine = Math.max(0, offset);
            int endLine = Math.min(allLines.size(), offset + limit);

            if (startLine >= allLines.size()) {
                return ToolResult.failure("偏移量超出文件长度");
            }

            StringBuilder output = new StringBuilder();
            for (int i = startLine; i < endLine; i++) {
                String line = allLines.get(i);
                // 截断过长的行
                if (line.length() > 2000) {
                    line = line.substring(0, 2000) + "... (已截断)";
                }
                output.append(String.format("%6d\t%s\n", i + 1, line));
            }

            String result = output.toString();
            log.debug("从 {} 读取了 {} 行 (偏移: {}, 限制: {})",
                relativePath, endLine - startLine, offset, limit);

            return ToolResult.success(result);

        } catch (IOException e) {
            log.error("读取文件失败", e);
            return ToolResult.failure("读取文件失败: " + e.getMessage());
        }
    }
}
