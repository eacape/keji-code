package com.kejicode.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行后返回的结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    private boolean success;
    private String output;
    private String error;
    private Object data;

    public static ToolResult success(String output) {
        return ToolResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    public static ToolResult success(String output, Object data) {
        return ToolResult.builder()
            .success(true)
            .output(output)
            .data(data)
            .build();
    }

    public static ToolResult failure(String error) {
        return ToolResult.builder()
            .success(false)
            .error(error)
            .build();
    }
}
