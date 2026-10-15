# 情感树洞（EmotionalHole）后端服务

> 面向高校学生的匿名情感倾诉社区 —— 微信小程序后端服务
>
> 匿名倾诉 · AI 陪伴 · 多层内容安全审核 · 高并发热点交互

## 项目简介

「情感树洞」是一个匿名情感分享平台，用户无需注册账号，通过微信一键登录即可获得一个匿名身份，自由发布图文心事、评论互动、互相"抱抱"、向 AI 倾诉情绪，并获得个性化内容推荐与情绪分析。平台内置完整的内容安全治理链路（本地敏感词 → 百度文本审核 → 千帆 AI 语义审核），并配套运营管理后台，用于内容审核、用户管理、敏感词维护与数据看板。

本仓库为**后端服务**（Spring Boot 单体应用），小程序端代码位于同级目录 `EmotionalHole-frontend`。

## 功能特性

### 用户端（小程序）

- **微信一键登录**：`jscode2session` 换取匿名身份，JWT 双 Token 认证，无需注册
- **匿名树洞发布**：图文内容发布，支持多图上传，发布即走三层内容审核
- **互动机制**：点赞 + "抱抱"（双向安慰），基于 Redis 的高并发计数
- **AI 情绪陪伴**：接入百度千帆 Agent，异步对话 + 超时降级兜底，对话历史异步落库
- **个性化推荐**：行为采集 + 推荐打分模型，按场景个性化内容流
- **情绪分析 / 危机预警**：情感关键词分析与高风险内容识别，触发安全提示
- **搜索、话题分类、热榜**：Redis ZSet 加权排序热榜 + 热点数据双缓存
- **举报与反馈**：内容举报工单流转至后台处理

### 管理后台（接口）

- 管理端独立 JWT 双 Token 认证，接口拦截器鉴权
- 内容审核工作台：下架/恢复、批量封禁用户内容
- 用户管理：查询、状态变更、封禁
- 敏感词库维护（DFA 算法实时生效）、公告管理、话题/氛围分类管理
- 数据看板：内容/用户/互动统计
- 举报工单处理、AI 审核评估

### 核心链路与稳定性设计

- **三层内容审核链路**：本地 DFA 敏感词 → 百度文本审核（机器确定性过滤）→ 千帆 AI 语义审核，任一命中即拦截；每层失败自动跳过降级，保证发布可用性
- **计数一致性**：Redis Lua 脚本原子增减（仅 Key 存在时操作、递减下限 0），`mGet + pipeline` 批量回源回填，定时任务以 DB 为准对账
- **热点数据双缓存**：首页信息流短 TTL + 写路径主动失效，发布/删除/审核后按模式清除缓存
- **接口限流**：`@RateLimit` 注解 + AOP 切面，按匿名身份固定窗口限流，Redis 不可用自动降级本地内存
- **文件上传降级**：MinIO 优先，异常回退本地磁盘；上传图片魔数校验防伪装文件
- **AI 异步化**：千帆调用走独立线程池，`future.get(15s)` 超时兜底话术，避免 LLM 慢响应阻塞主链路

## 技术栈

| 分类 | 技术 |
|------|------|
| 基础框架 | Spring Boot 3.5.4（Java 17） |
| 持久层 | MyBatis-Plus 3.5.9 + mybatis-plus-jsqlparser（分页） |
| 数据库 | MySQL 5.7+（utf8mb4） |
| 缓存 | Redis（StringRedisTemplate） |
| 对象存储 | MinIO（懒加载客户端，Bucket 自动创建、公开只读） |
| AI 能力 | 百度千帆 AppBuilder Agent（聊天 + 审核） |
| 文本审核 | 百度内容审核 API（机器确定性过滤） |
| 认证授权 | JWT（用户端 + 管理端双密钥） |
| 接口文档 | SpringDoc OpenAPI 3.0（swagger-ui） |
| 其他 | Lombok、Hutool、Spring AOP、Spring Validation、@EnableScheduling 定时任务 |

## 项目结构

```
EmotionalHole-back
├── src/main/java/com/myk/emotionalHole/
│   ├── annotation/          # 自定义注解（@RateLimit）
│   ├── aspect/              # AOP 切面（限流）
│   ├── common/              # 统一返回、全局异常处理
│   ├── config/              # Redis/MinIO/RestTemplate/异步/拦截器等配置
│   ├── controller/          # 接口层（用户端 + 管理端）
│   ├── dto/                 # 请求/响应对象
│   ├── entity/              # 实体类（对应数据库表）
│   ├── mapper/              # MyBatis-Plus Mapper
│   ├── service/             # 业务逻辑层（接口 + impl）
│   ├── task/                # 定时任务（计数对账、热榜刷新）
│   ├── util/                # 工具类（JWT、敏感词 DFA、AI、审核、危机检测等）
│   └── EmotionalHoleApplication.java   # 启动类
├── src/main/resources/
│   ├── mapper/              # 复杂 SQL XML 映射
│   ├── db/optimize_index.sql   # 数据库索引优化脚本（消除 filesort / 全表扫描）
│   └── application.yml      # 配置文件（${ENV:默认值} 风格）
└── pom.xml
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 5.7+（本地库名 `emotional_hole`，utf8mb4）
- Redis（默认 localhost:6379，密码为空，无需配置即可启动）
- MinIO（可选，未启动时自动降级本地磁盘存储）
- 百度千帆账号（AI 对话 / AI 审核）与百度内容审核应用（可选，未配置时逐层降级）

### 1. 初始化数据库

```sql
CREATE DATABASE emotional_hole DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

按 `entity` 包实体类建表（含 10 条初始化数据），并建议执行 `src/main/resources/db/optimize_index.sql` 为高频查询补齐复合索引。

### 2. 启动服务

```bash
mvn clean package
java -jar target/emotionalHole-0.0.1-SNAPSHOT.jar
# 或：mvn spring-boot:run
```

服务默认端口 `8080`，接口文档地址：`http://localhost:8080/swagger-ui.html`。

### 3. 关键环境变量

配置文件采用 `${ENV:默认值}` 风格，不配置时使用默认值即可本地运行；**生产环境请务必通过环境变量覆盖**：

| 环境变量 | 说明 | 默认值 |
|---------|------|--------|
| `WECHAT_APP_ID` / `WECHAT_APP_SECRET` | 微信小程序凭据 | 内置示例值 |
| `AI_API_KEY` / `AI_CHAT_AGENT_ID` / `AI_AUDIT_AGENT_ID` | 千帆 Agent 凭据 | 内置示例值 |
| `BAIDU_AUDIT_ENABLED` / `BAIDU_AUDIT_APP_ID` / `BAIDU_AUDIT_API_KEY` / `BAIDU_AUDIT_SECRET_KEY` | 百度文本审核 | 默认开启 |
| `MINIO_ENDPOINT` / `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` / `MINIO_BUCKET_NAME` / `MINIO_ACCESS_PATH` | MinIO 对象存储 | 内置示例值 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` | Redis 连接 | localhost/6379 |
| `JWT_SECRET` / `JWT_ADMIN_SECRET` | 用户端 / 管理端 JWT 密钥 | 内置示例值 |

### 4. 注意事项

- 上传图片默认落盘 `uploads/images/`（本地模式），MinIO 可用时上传至桶内并通过 `{access-path}/{bucket}/{objectName}` 访问
- MinIO Bucket 需配置公开只读（Anonymous=readonly），小程序/浏览器才能直接加载图片；真机调试需 https + 合法域名
- Redis / MinIO / 外部审核服务不可用时，缓存、上传、审核链路均自动降级，应用仍可正常启动与提供核心功能

## 数据库索引优化

`src/main/resources/db/optimize_index.sql` 基于实际 Mapper SQL 分析补建了复合索引，覆盖首页内容流、分类列表、我的发布、评论列表、点赞批量计数、行为统计等高频查询，消除了 filesort 与全表扫描（详见脚本注释）。

## 作者

- GitHub：[tianCode-hub](https://github.com/tianCode-hub)
- Gitee：[tian-tian-zhen-zhen](https://gitee.com/tian-tian-zhen-zhen)

## License

MIT License
