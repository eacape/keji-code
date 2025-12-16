package com.kejicode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * 应用程序配置管理器 - 支持 YAML 配置
 */
@Slf4j
@Getter
public class Configuration {

    private Map<String, Object> config;
    private final ObjectMapper yamlMapper;

    public Configuration() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        loadConfiguration();
    }

    @SuppressWarnings("unchecked")
    private void loadConfiguration() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application.yml")) {

            if (input == null) {
                log.warn("未找到 application.yml，使用默认值");
                config = Map.of();
                return;
            }

            config = yamlMapper.readValue(input, Map.class);
            log.info("配置加载成功");

        } catch (IOException e) {
            log.error("加载配置失败", e);
            config = Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(String... keys) {
        Map<String, Object> current = config;

        for (int i = 0; i < keys.length - 1; i++) {
            Object next = current.get(keys[i]);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                return null;
            }
        }

        return current.get(keys[keys.length - 1]);
    }

    public String getProperty(String... keys) {
        Object value = getNestedValue(keys);
        return value != null ? value.toString() : null;
    }

    public String getPropertyOrDefault(String defaultValue, String... keys) {
        String value = getProperty(keys);
        return value != null ? value : defaultValue;
    }

    public String getModelProvider() {
        String provider = getProperty("ai", "model", "provider");
        return provider != null ? provider : "anthropic";
    }

    public String getModelName() {
        String modelName = getProperty("ai", "model", "name");
        return modelName != null ? modelName : "claude-3-5-sonnet-20241022";
    }

    public String getApiKey() {
        String provider = getModelProvider();

        // 根据不同的提供商尝试不同的环境变量
        String[] envKeys = switch (provider.toLowerCase()) {
            case "anthropic" -> new String[]{"ANTHROPIC_API_KEY"};
            case "openai" -> new String[]{"OPENAI_API_KEY"};
            case "dashscope" -> new String[]{"DASHSCOPE_API_KEY", "ALIBABA_CLOUD_API_KEY"};
            case "deepseek" -> new String[]{"DEEPSEEK_API_KEY"};
            default -> new String[]{"API_KEY"};
        };

        // 首先检查特定环境变量
        for (String envKey : envKeys) {
            String value = System.getenv(envKey);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // 回退到系统属性
        for (String envKey : envKeys) {
            String value = System.getProperty(envKey);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }

        // 回退到配置文件
        String configKey = getProperty("ai", "model", "api", "key");
        if (configKey != null && configKey.startsWith("${")) {
            // 尝试解析环境变量引用
            String varName = configKey.substring(2, configKey.length() - 1);
            return System.getenv(varName);
        }

        return configKey;
    }

    public String getCustomUrl() {
        String url = getProperty("ai", "model", "custom", "url");
        return url != null ? url : "";
    }

    public String getCustomHeaders() {
        String headers = getProperty("ai", "model", "custom", "headers");
        return headers != null ? headers : "";
    }

    public double getTemperature() {
        String temp = getProperty("ai", "model", "temperature");
        return temp != null ? Double.parseDouble(temp) : 0.7;
    }

    public int getMaxTokens() {
        String tokens = getProperty("ai", "model", "max", "tokens");
        return tokens != null ? Integer.parseInt(tokens) : 4096;
    }

    public String getWorkingDirectory() {
        String workingDir = getProperty("app", "working", "directory");
        if (workingDir != null && workingDir.equals("${user.dir}")) {
            return System.getProperty("user.dir");
        }
        return workingDir != null ? workingDir : System.getProperty("user.dir");
    }
}
