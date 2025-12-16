package com.kejicode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kejicode.utils.PathValidator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 文件模式匹配查找工具
 */
@Slf4j
public class GlobTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "Glob";
    }

    @Override
    public String getDescription() {
        return "快速的文件模式匹配工具。支持 glob 模式如 **/*.js 或 src/**/*.ts";
    }

    @Override
    public ToolResult execute(String parameters) throws Exception {
        try {
            JsonNode params = MAPPER.readTree(parameters);
            String pattern = params.get("pattern").asText();
            String searchPath = params.has("path") ? params.get("path").asText() : ".";

            // 验证搜索路径是否在工作目录范围内
            Path basePath;
            try {
                basePath = PathValidator.validateAndNormalize(searchPath);
            } catch (SecurityException e) {
                log.warn("路径安全检查失败: {}", e.getMessage());
                return ToolResult.failure("安全错误: " + e.getMessage());
            }

            if (!Files.exists(basePath)) {
                return ToolResult.failure("搜索路径不存在: " + PathValidator.toRelativePath(basePath));
            }

            Path workingDir = PathValidator.getWorkingDirectory();
            List<FileMatch> matches = new ArrayList<>();
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

            Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // 只包含在工作目录内的文件
                    if (!file.startsWith(workingDir)) {
                        return FileVisitResult.CONTINUE;
                    }

                    Path relativePath = basePath.relativize(file);
                    if (matcher.matches(relativePath) || matcher.matches(file.getFileName())) {
                        matches.add(new FileMatch(file, attrs.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    // 跳过隐藏目录和常见的忽略目录
                    String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (dirName.startsWith(".") || dirName.equals("node_modules") ||
                        dirName.equals("target") || dirName.equals("build")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // 出错时继续
                    return FileVisitResult.CONTINUE;
                }
            });

            // 按修改时间排序（最新的在前）
            matches.sort(Comparator.comparingLong(FileMatch::lastModified).reversed());

            StringBuilder output = new StringBuilder();
            output.append(String.format("找到 %d 个匹配模式 '%s' 的文件:\n\n", matches.size(), pattern));

            for (FileMatch match : matches) {
                String displayPath = PathValidator.toRelativePath(match.path());
                output.append(displayPath).append("\n");
            }

            log.debug("找到 {} 个匹配模式的文件: {}", matches.size(), pattern);
            return ToolResult.success(output.toString());

        } catch (IOException e) {
            log.error("搜索文件失败", e);
            return ToolResult.failure("搜索文件失败: " + e.getMessage());
        }
    }

    private record FileMatch(Path path, long lastModified) {}
}
