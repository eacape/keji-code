package com.kejicode.tools;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * AI Agent 可以使用的所有工具的基础接口
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = ReadFileTool.class, name = "read"),
    @JsonSubTypes.Type(value = WriteFileTool.class, name = "write"),
    @JsonSubTypes.Type(value = EditFileTool.class, name = "edit"),
    @JsonSubTypes.Type(value = GlobTool.class, name = "glob"),
    @JsonSubTypes.Type(value = GrepTool.class, name = "grep"),
    @JsonSubTypes.Type(value = BashTool.class, name = "bash")
})
public interface Tool {

    /**
     * 获取工具名称
     */
    String getName();

    /**
     * 获取工具功能描述
     */
    String getDescription();

    /**
     * 执行工具
     * @param parameters JSON 格式的工具参数
     * @return 工具执行结果
     */
    ToolResult execute(String parameters) throws Exception;
}
