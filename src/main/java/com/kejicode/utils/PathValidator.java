package com.kejicode.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径验证工具类 - 确保所有文件操作都在工作目录范围内
 */
@Slf4j
public class PathValidator {

    private static Path workingDirectory;

    /**
     * 初始化工作目录
     */
    public static void initialize(String workingDir) {
        try {
            workingDirectory = Paths.get(workingDir).toRealPath();
            log.info("工作目录已设置为: {}", workingDirectory);
        } catch (IOException e) {
            // 如果路径不存在，使用规范化路径
            workingDirectory = Paths.get(workingDir).toAbsolutePath().normalize();
            log.info("工作目录已设置为: {} (路径尚不存在)", workingDirectory);
        }
    }

    /**
     * 获取当前工作目录
     */
    public static Path getWorkingDirectory() {
        if (workingDirectory == null) {
            // 如果未初始化，使用系统工作目录
            String userDir = System.getProperty("user.dir");
            initialize(userDir);
        }
        return workingDirectory;
    }

    /**
     * 验证给定路径是否在工作目录范围内
     *
     * @param targetPath 要验证的路径（可以是相对路径或绝对路径）
     * @return 如果路径有效且在工作目录内，返回规范化后的绝对路径
     * @throws SecurityException 如果路径试图访问工作目录外的文件
     */
    public static Path validateAndNormalize(String targetPath) throws SecurityException {
        if (targetPath == null || targetPath.trim().isEmpty()) {
            throw new SecurityException("路径不能为空");
        }

        Path workDir = getWorkingDirectory();
        Path target;

        try {
            // 将目标路径转换为绝对路径
            if (Paths.get(targetPath).isAbsolute()) {
                target = Paths.get(targetPath);
            } else {
                // 相对路径：基于工作目录解析
                target = workDir.resolve(targetPath);
            }

            // 规范化路径（移除 . 和 .. 等）
            target = target.normalize();

            // 检查规范化后的路径是否以工作目录开始
            if (!target.startsWith(workDir)) {
                throw new SecurityException(
                    String.format("拒绝访问: 路径 '%s' 在工作目录 '%s' 之外",
                        target, workDir)
                );
            }

            return target;

        } catch (SecurityException e) {
            throw e;
        } catch (Exception e) {
            throw new SecurityException("路径验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证路径并返回字符串形式
     */
    public static String validateAndNormalizeToString(String targetPath) throws SecurityException {
        return validateAndNormalize(targetPath).toString();
    }

    /**
     * 检查路径是否在工作目录内（不抛出异常）
     */
    public static boolean isWithinWorkingDirectory(String targetPath) {
        try {
            validateAndNormalize(targetPath);
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }

    /**
     * 将绝对路径转换为相对于工作目录的路径（用于显示）
     */
    public static String toRelativePath(Path absolutePath) {
        try {
            Path workDir = getWorkingDirectory();
            if (absolutePath.startsWith(workDir)) {
                return workDir.relativize(absolutePath).toString();
            }
            return absolutePath.toString();
        } catch (Exception e) {
            return absolutePath.toString();
        }
    }
}
