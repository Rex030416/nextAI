# NextAI Java 后端

这是对原 `server/` Express 服务的增量替代：旧服务暂时保留，新服务位于本目录，使用 Java 21、Spring Boot、Spring AI、PostgreSQL + pgvector。

## 已打通的运行链路

```text
POST /api/v1/documents
→ 将原始文件保存到 MinIO 并创建 documentId
→ 后台 Tika 解析
→ 段落优先切块（仅超长段落再按句末切）
→ OpenAI Embedding
→ 写入带 owner_id 哈希分区的 document_chunks
→ HNSW 向量检索 + PostgreSQL GIN 关键词候选
→ RRF 融合 Top 4
→ Spring AI ChatClient 生成带来源 chunk 的答案
```

`owner_id` 当前由 `X-Owner-Id` 请求头提供；未提供时使用 `DEMO_OWNER_ID`，只用于本地开发。上线前必须换成 JWT 或 Session 中经服务端验证的用户主体。

## 本地启动

前提：Java 21、Maven 3.9+、Docker Desktop 已启动，以及 OpenAI API Key。

```powershell
docker compose up -d postgres minio
$env:NEXTAI_JDBC_DATABASE_URL = "jdbc:postgresql://localhost:5433/nextai"
$env:NEXTAI_DB_USER = "nextai"
$env:NEXTAI_DB_PASSWORD = "change-me-for-production"
$env:OPENAI_API_KEY = "你的 OpenAI API Key"
cd backend
mvn spring-boot:run
```

MinIO 的 S3 API 位于 `http://localhost:9000`，管理控制台位于 `http://localhost:9001`。本地默认账号来自 `backend/.env.example`；请在实际部署前替换 `NEXTAI_MINIO_ACCESS_KEY` 和 `NEXTAI_MINIO_SECRET_KEY`。上传文件会以 `owners/{ownerId 哈希}/documents/{documentId}/...` 的对象键写入 bucket，数据库仅记录这个对象键，不再保存本地磁盘路径。

Flyway 会在首次启动时创建 pgvector 扩展、`documents`、分区化的 `document_chunks`、HNSW 和 GIN 索引。不要再同时执行旧的 `server/db/init.sql`，它只保留给旧 Express 方案作参考。

## API

上传：

```http
POST /api/v1/documents
X-Owner-Id: user-123
Content-Type: multipart/form-data

file: <PDF/DOC/DOCX/TXT/MD>
```

返回 `201` 与 `documentId`、`status`。轮询下列接口，等待状态为 `READY`。

```http
GET /api/v1/documents/{documentId}
X-Owner-Id: user-123
```

提问：

```http
POST /api/v1/documents/{documentId}/questions
X-Owner-Id: user-123
Content-Type: application/json

{"question":"这份文档的核心结论是什么？"}
```

回答含 `answer` 和 `sources`。当前 `pageNumber` 还未从解析器传递，下一阶段应替换为按页 PDF 解析器并补齐页码引用。

## 对话记忆

提问接口支持可选 `sessionId`。未传时后端创建会话并在响应中返回；React 前端会按 `documentId` 自动缓存它。会话内保留最近 6 条消息，较早消息会异步压缩为短期摘要。系统还会异步提取用户稳定偏好、背景和长期目标，生成 Embedding 后写入 `user_memories`，后续问题按 `owner_id` 语义召回最多 3 条长期记忆。

对话原文、摘要和长期记忆都以不可信上下文的形式传入模型，不能覆盖系统指令。长期记忆提取会排除文档事实、临时问题、秘密和个人标识信息；生产环境仍应补充用户可见的记忆管理、删除接口和审计机制。

## 语义缓存

首次回答“无会话上下文、无长期记忆影响”的文档问题后，系统会保存问题向量、答案和引用。后续同一用户、同一文档的相似问题会先在 `semantic_answer_cache` 中按余弦相似度查找；默认相似度阈值为 `0.92`、TTL 为 24 小时。命中时跳过文档召回与 Chat Model 调用，响应中的 `cacheHit` 为 `true`，前端会显示“语义缓存命中”。文档重新索引时会清除该文档缓存。

多轮会话、摘要或长期记忆参与回答时不会使用此缓存，避免上下文不同却错误复用答案。
