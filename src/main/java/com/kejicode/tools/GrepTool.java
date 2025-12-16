package com.kejicode.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kejicode.utils.PathValidator;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件内容搜索工具 - 使用正则表达式
 */
@Slf4j
public class GrepTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String getName() {
        return "Grep";
    }

    @Override
    public String getDescription() {
        return "强大的文件内容搜索工具。支持正则表达式和多种输出模式。";
    }

    @Override
    public ToolResult execute(String parameters) throws Exception {
        try {
            JsonNode params = MAPPER.readTree(parameters);
            String patternStr = params.get("pattern").asText();
            String searchPath = params.has("path") ? params.get("path").asText() : ".";
            final String outputMode = params.has("output_mode") ? params.get("output_mode").asText() : "files_with_matches";
            boolean caseInsensitive = params.has("-i") && params.get("-i").asBoolean();
            final boolean showLineNumbers = params.has("-n") && params.get("-n").asBoolean();
            int contextBefore = params.has("-B") ? params.get("-B").asInt() : 0;
            int contextAfter = params.has("-A") ? params.get("-A").asInt() : 0;
            if (params.has("-C")) {
                int context = params.get("-C").asInt();
                contextBefore = contextAfter = context;
            }
            final int finalContextBefore = contextBefore;
            final int finalContextAfter = contextAfter;
            Integer headLimit = params.has("head_limit") ? params.get("head_limit").asInt() : null;
            String globPattern = params.has("glob") ? params.get("glob").asText() : null;
            final String typeFilter = params.has("type") ? params.get("type").asText() : null;

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
            int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
            final Pattern pattern = Pattern.compile(patternStr, flags);

            final List<SearchResult> results = new ArrayList<>();

            if (Files.isRegularFile(basePath)) {
                searchFile(basePath, pattern, results, outputMode, showLineNumbers, finalContextBefore, finalContextAfter);
            } else {
                PathMatcher globMatcher = globPattern != null ?
                    FileSystems.getDefault().getPathMatcher("glob:" + globPattern) : null;

                Files.walkFileTree(basePath, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        try {
                            // 只包含在工作目录内的文件
                            if (!file.startsWith(workingDir)) {
                                return FileVisitResult.CONTINUE;
                            }

                            // 应用过滤器
                            if (globMatcher != null) {
                                Path relativePath = basePath.relativize(file);
                                if (!globMatcher.matches(relativePath)) {
                                    return FileVisitResult.CONTINUE;
                                }
                            }

                            if (typeFilter != null && !matchesType(file, typeFilter)) {
                                return FileVisitResult.CONTINUE;
                            }

                            searchFile(file, pattern, results, outputMode, showLineNumbers, finalContextBefore, finalContextAfter);

                        } catch (IOException e) {
                            log.warn("搜索文件出错 {}: {}", file, e.getMessage());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        String dirName = dir.getFileName() != null ? dir.getFileName().toString() : "";
                        if (dirName.startsWith(".") || dirName.equals("node_modules") ||
                            dirName.equals("target") || dirName.equals("build")) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }

            // 应用数量限制
            List<SearchResult> limitedResults = results;
            if (headLimit != null && results.size() > headLimit) {
                limitedResults = results.subList(0, headLimit);
            }

            String output = formatResults(limitedResults, outputMode);
            log.debug("找到 {} 个匹配结果: {}", limitedResults.size(), patternStr);

            return ToolResult.success(output);

        } catch (Exception e) {
            log.error("搜索失败", e);
            return ToolResult.failure("搜索失败: " + e.getMessage());
        }
    }

    private void searchFile(Path file, Pattern pattern, List<SearchResult> results,
                           String outputMode, boolean showLineNumbers,
                           int contextBefore, int contextAfter) throws IOException {

        List<String> lines = Files.readAllLines(file);
        int matchCount = 0;
        List<MatchLine> matchedLines = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = pattern.matcher(line);

            if (matcher.find()) {
                matchCount++;

                if ("content".equals(outputMode)) {
                    // Add context lines before
                    for (int j = Math.max(0, i - contextBefore); j < i; j++) {
                        matchedLines.add(new MatchLine(j + 1, lines.get(j), false));
                    }

                    // Add matched line
                    matchedLines.add(new MatchLine(i + 1, line, true));

                    // Add context lines after
                    for (int j = i + 1; j <= Math.min(lines.size() - 1, i + contextAfter); j++) {
                        matchedLines.add(new MatchLine(j + 1, lines.get(j), false));
                    }
                }
            }
        }

        if (matchCount > 0) {
            results.add(new SearchResult(file, matchCount, matchedLines));
        }
    }

    private boolean matchesType(Path file, String type) {
        String fileName = file.getFileName().toString();
        return switch (type.toLowerCase()) {
            case "java" -> fileName.endsWith(".java");
            case "js" -> fileName.endsWith(".js");
            case "ts" -> fileName.endsWith(".ts");
            case "py" -> fileName.endsWith(".py");
            case "xml" -> fileName.endsWith(".xml");
            case "json" -> fileName.endsWith(".json");
            case "yaml", "yml" -> fileName.endsWith(".yaml") || fileName.endsWith(".yml");
            default -> false;
        };
    }

    private String formatResults(List<SearchResult> results, String outputMode) {
        StringBuilder sb = new StringBuilder();

        switch (outputMode) {
            case "files_with_matches":
                for (SearchResult result : results) {
                    String displayPath = PathValidator.toRelativePath(result.file());
                    sb.append(displayPath).append("\n");
                }
                break;

            case "count":
                for (SearchResult result : results) {
                    String displayPath = PathValidator.toRelativePath(result.file());
                    sb.append(displayPath).append(": ").append(result.matchCount()).append("\n");
                }
                break;

            case "content":
                for (SearchResult result : results) {
                    String displayPath = PathValidator.toRelativePath(result.file());
                    sb.append("\n").append(displayPath).append(":\n");
                    for (MatchLine line : result.matchedLines()) {
                        sb.append(line.lineNumber()).append(": ").append(line.content()).append("\n");
                    }
                }
                break;
        }

        return sb.toString();
    }

    private record SearchResult(Path file, int matchCount, List<MatchLine> matchedLines) {}
    private record MatchLine(int lineNumber, String content, boolean isMatch) {}
}
