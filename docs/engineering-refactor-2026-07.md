# 工程化重构记录（2026-07）

## 目标与边界

本次不是把 Express 代码逐行翻译成 Java，而是将单文件、单进程、内存索引的原型，拆成可持久化演进的前后端边界。

旧的 `server/` 仍保留为 `npm run dev:legacy`，便于对照和回退；默认 `npm run dev` 改为 React 前端 + Java 后端。

```text
React + TypeScript
  └─ /api/v1/documents
      └─ Spring Boot API
          ├─ PostgreSQL: 文档状态、租户隔离、chunk 元数据
          ├─ pgvector: Embedding 与 HNSW 相似度检索
          ├─ PostgreSQL GIN: keyword_terms 候选检索
          ├─ MinIO: 原始文件对象存储
          ├─ PostgreSQL: 会话消息、短期摘要与长期用户记忆
          ├─ Apache Tika: PDF/DOC/DOCX/TXT/MD 文本抽取
          └─ Spring AI: OpenAI Embedding / ChatClient
```

## 已实现

- `backend/`：Java 21 + Spring Boot + Spring AI 后端骨架及容器镜像定义。
- `POST /api/v1/documents`：文件白名单、SHA-256 去重、MinIO 对象键生成、`documentId` 返回。
- `GET /api/v1/documents/{documentId}`：前端轮询 `UPLOADED / INDEXING / READY / FAILED`。
- 后台索引：Tika 抽取、段落独立切块、OpenAI Embedding、pgvector `document_chunks` 入库。
- 查询：强制 `owner_id + document_id` 条件；Top 20 向量候选与 Top 20 关键词候选经 RRF 融合为 Top 4，再交给模型回答。
- React 迁移至 TypeScript：上传响应中的 `documentId` 被保存并传给提问 API；回答展示来源 chunk。
- Flyway 负责创建 PostgreSQL 扩展、32 个按 `owner_id` 哈希分区、HNSW 与 GIN 索引。Docker Compose 不再在容器初始化时执行旧 SQL，避免与 Flyway 重复建表。
- MinIO 已接入：原始文件写入 `nextai-documents` bucket，`documents.storage_path` 保存对象键；Tika 索引时从 MinIO 下载文件流。2026-07-30 已实测完成一份 Markdown 的上传、解析、Embedding 和 12 个 chunk 入库。
- 对话记忆已接入：会话保留最近 6 条消息、较早消息异步摘要；长期用户偏好/背景/目标经模型筛选和向量化后写入 `user_memories`，后续问题按用户维度语义召回。已实测同一 `sessionId` 的 4 轮问答持久化 8 条消息，生成短期摘要，并写入长期记忆。
- 语义缓存已接入：对无会话上下文的文档问题，以 `owner_id + document_id + 问题向量` 查询 pgvector 缓存；达到相似度阈值后直接复用答案和引用，文档重新索引时自动失效。已实测同义问题命中缓存，余弦相似度为 `0.9807`。

## 仍明确未实现

- 认证：当前 `X-Owner-Id` / `DEMO_OWNER_ID` 仅是本地开发适配，不具备真实身份验证能力。
- 任务队列：当前使用 Spring `@Async`，尚未接入 Redis/BullMQ/SQS；索引失败原因也还未持久化。
- Redis 精确缓存、reranker、页码级 PDF 引用、全文高亮。
- 提示注入检测、PII 脱敏及安全评测。
- PDF 批注、笔记、编辑和版本管理。

## 运行前提

本机需安装 Java 21、Maven 3.9+，启动 Docker Desktop，并提供 `OPENAI_API_KEY`。具体命令见 [backend README](../backend/README.md)。TypeScript 已通过 `npx tsc --noEmit` 与生产构建；Java 后端已通过 `mvn -DskipTests package`。PostgreSQL + pgvector 与 MinIO 均已在 Docker 中完成健康检查，上传、异步索引和向量入库链路也已实测通过。
