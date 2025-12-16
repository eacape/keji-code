package com.kejicode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kejicode.utils.PathValidator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件编辑工具 - 通过精确字符串替换编辑文件
 */
@Slf4j
public class EditFileTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "Edit";
    }

    @Override
    public String getDescription() {
        return """
            执行文件中的精确字符串替换。可以替换首次出现或全部出现。

            参数:
            - file_path (必需): 要编辑的文件路径
            - old_string (必需): 要被替换的原字符串
            - new_string (必需): 替换后的新字符串
            - replace_all (可选): true=替换所有出现,false=仅替换第一次出现 (默认: false)

            示例: {"file_path": "src/Main.java", "old_string": "oldCode", "new_string": "newCode", "replace_all": false}
            """;
    }

    @Override
    public ToolResult execute(String parameters) throws Exception {
        try {
            JsonNode params = MAPPER.readTree(parameters);
            String filePath = params.get("file_path").asText();
            String oldString = params.get("old_string").asText();
            String newString = params.get("new_string").asText();
            boolean replaceAll = params.has("replace_all") && params.get("replace_all").asBoolean();

            // 验证路径是否在工作目录范围内
            Path path;
            try {
                path = PathValidator.validateAndNormalize(filePath);
            } catch (SecurityException e) {
                log.warn("路径安全检查失败: {}", e.getMessage());
                return ToolResult.failure("安全错误: " + e.getMessage());
            }

            if (!Files.exists(path)) {
                String relativePath = PathValidator.toRelativePath(path);
                return ToolResult.failure("文件不存在: " + relativePath);
            }

            String content = Files.readString(path);

            // 检查 old_string 是否存在
            if (!content.contains(oldString)) {
                return ToolResult.failure("文件中未找到该字符串: " + oldString);
            }

            // 计算出现次数
            int count = countOccurrences(content, oldString);

            if (!replaceAll && count > 1) {
                return ToolResult.failure(
                    String.format("字符串在文件中出现了 %d 次。请使用 replace_all=true 或提供更多上下文使其唯一。", count)
                );
            }

            // 执行替换
            String newContent;
            if (replaceAll) {
                newContent = content.replace(oldString, newString);
            } else {
                // 替换第一次出现
                int index = content.indexOf(oldString);
                newContent = content.substring(0, index) + newString + content.substring(index + oldString.length());
            }

            Files.writeString(path, newContent);

            String relativePath = PathValidator.toRelativePath(path);
            String message = replaceAll ?
                String.format("在 %s 中替换了 %d 处", relativePath, count) :
                String.format("在 %s 中替换了 1 处", relativePath);

            log.debug(message);
            return ToolResult.success(message);

        } catch (IOException e) {
            log.error("编辑文件失败", e);
            return ToolResult.failure("编辑文件失败: " + e.getMessage());
        }
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(pattern, index)) != -1) {
            count++;
            index += pattern.length();
        }
        return count;
    }
}
