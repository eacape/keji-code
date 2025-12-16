# KejiCode (柯基Code)

一个基于 Java 和 LangChain4j 打造的类似 Claude Code 的终端代码编程工具。

## 项目概述

KejiCode (柯基Code) 是一个 AI 驱动的终端编程助手,它结合了 LangChain4j 框架为开发者提供智能的代码辅助功能。该工具可以帮助开发者完成文件读写、代码搜索、命令执行等各种编程任务。

## 核心功能

### 1. 智能对话交互
- 基于各种大 模型的自然语言理解
- 上下文感知的对话历史管理
- 智能工具调用和任务执行

### 2. 文件操作工具

#### Read (文件读取)
- 读取本地文件系统中的文件
- 支持行偏移和限制读取
- 自动截断过长的行(超过 2000 字符)
- 提供带行号的输出格式

**使用示例:**
```
读取 pom.xml 文件
查看 src/main/java/Main.java 的内容
```

#### Write (文件写入)
- 创建新文件或覆盖已有文件
- 自动创建父目录
- 支持任意文本内容写入

**使用示例:**
```
创建一个新的 README.md 文件
写入配置到 application.properties
```

#### Edit (文件编辑)
- 精确的字符串替换功能
- 支持单次替换或全部替换
- 防止意外的多处修改(需要显式指定 replace_all)

**使用示例:**
```
将 Main.java 中的 oldMethod 重命名为 newMethod
替换所有的 TODO 注释
```

#### Glob (文件搜索)
- 基于模式匹配查找文件
- 支持通配符模式(如 `**/*.java`, `src/**/*.xml`)
- 按修改时间排序结果
- 自动跳过常见的忽略目录(node_modules, target, build 等)

**使用示例:**
```
查找所有的 Java 文件
搜索 src 目录下的所有 XML 配置文件
```

#### Grep (内容搜索)
- 强大的正则表达式搜索
- 多种输出模式:
  - `files_with_matches`: 仅显示包含匹配的文件路径
  - `content`: 显示匹配的行内容
  - `count`: 显示每个文件的匹配次数
- 支持上下文行显示(-A, -B, -C)
- 支持大小写不敏感搜索(-i)
- 文件类型过滤(Java, JS, Python 等)
- Glob 模式过滤

**使用示例:**
```
搜索所有包含 "TODO" 的文件
在 Java 文件中查找 "public class" 定义
```

#### Bash (命令执行)
- 执行 Shell 命令
- 支持超时控制(默认 2 分钟)
- 跨平台支持(Unix/Linux/macOS 使用 bash, Windows uses cmd)
- 合并标准输出和错误输出

**使用示例:**
```
运行 mvn clean package
执行 git status
查看当前目录结构
```

### 3. 终端用户界面

#### 交互式命令行
- 基于 JLine 3 的现代终端 UI
- 支持命令历史和编辑
- 优雅的错误处理和用户提示

#### 内置命令
- `/help` - 显示帮助信息
- `/clear` - 清屏
- `/reset` - 清除对话历史
- `/version` - 显示版本信息
- `/exit` 或 `/quit` - 退出程序

## 技术架构

### 技术栈
- **Java 17**: 现代 Java 特性(Records, Pattern Matching 等)
- **LangChain4j**: AI 应用开发框架
- **Anthropic Claude**: 先进的 AI 语言模型
- **Maven**: 项目构建和依赖管理
- **PicoCLI**: 命令行参数解析
- **JLine 3**: 终端交互界面
- **Lombok**: 减少样板代码
- **SLF4J + Logback**: 日志管理
- **Jackson**: JSON 处理

### 项目结构
```
kejiCode/
├── pom.xml                          # Maven 配置
├── src/
│   ├── main/
│   │   ├── java/com/codeassistant/
│   │   │   ├── Main.java            # 主入口
│   │   │   ├── agent/
│   │   │   │   └── CodeAssistantAgent.java   # AI Agent 核心逻辑
│   │   │   ├── tools/
│   │   │   │   ├── Tool.java                 # 工具接口
│   │   │   │   ├── ToolResult.java           # 工具执行结果
│   │   │   │   ├── ReadFileTool.java         # 文件读取工具
│   │   │   │   ├── WriteFileTool.java        # 文件写入工具
│   │   │   │   ├── EditFileTool.java         # 文件编辑工具
│   │   │   │   ├── GlobTool.java             # 文件搜索工具
│   │   │   │   ├── GrepTool.java             # 内容搜索工具
│   │   │   │   └── BashTool.java             # 命令执行工具
│   │   │   ├── ui/
│   │   │   │   └── TerminalUI.java           # 终端界面
│   │   │   └── config/
│   │   │       └── Configuration.java        # 配置管理
│   │   └── resources/
│   │       ├── application.properties        # 应用配置
│   │       └── logback.xml                   # 日志配置
│   └── test/
│       └── java/com/codeassistant/           # 测试代码
└── README.md                                  # 本文档
```

---

**Happy Coding with AI! 🚀**
