package com.kejicode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kejicode.utils.PathValidator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * 文件写入工具
 */
@Slf4j
public class WriteFileTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "Write";
    }

    @Override
    public String getDescription() {
        return """
            将内容写入文件。创建新文件或覆盖已有文件。

            参数:
            - file_path (必需): 文件的完整路径,例如 "src/main/java/Example.java"
            - content (必需): 要写入的文件内容

            示例: {"file_path": "src/main/java/HelloWorld.java", "content": "public class HelloWorld {...}"}
            """;
    }

    @Override
    public ToolResult execute(String parameters) throws Exception {
        try {
            JsonNode params = MAPPER.readTree(parameters);

            // 支持 file_path 或 filename 参数 (兼容性处理)
            String filePath = null;
            if (params.has("file_path")) {
                filePath = params.get("file_path").asText();
            } else if (params.has("filename")) {
                filePath = params.get("filename").asText();
            } else {
                return ToolResult.failure("缺少必需参数: file_path (文件路径)");
            }

            if (!params.has("content")) {
                return ToolResult.failure("缺少必需参数: content (文件内容)");
            }
            String content = params.get("content").asText();

            // 验证路径是否在工作目录范围内
            Path path;
            try {
                path = PathValidator.validateAndNormalize(filePath);
            } catch (SecurityException e) {
                log.warn("路径安全检查失败: {}", e.getMessage());
                return ToolResult.failure("安全错误: " + e.getMessage());
            }

            // 如果父目录不存在则创建
            Path parent = path.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            // 写入文件
            Files.writeString(path, content,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

            String relativePath = PathValidator.toRelativePath(path);
            log.debug("成功写入 {} 字节到 {}", content.length(), relativePath);
            return ToolResult.success("文件写入成功: " + relativePath);

        } catch (IOException e) {
            log.error("写入文件失败", e);
            return ToolResult.failure("写入文件失败: " + e.getMessage());
        }
    }
}
