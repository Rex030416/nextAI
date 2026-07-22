# 2026-07-22 工作总结：PDF 文档问答检索升级

## 本次目标

梳理现有 PDF 文档问答流程，改善文本切块与召回逻辑，并为后续持久化文档向量库和多用户隔离建立基础设施。

## 已完成的代码修改

### 1. 文本切块优化

修改 [server/chat.js](../server/chat.js)：

- 从“直接对整份解析结果切块”改为“先按空行识别段落，再逐段切分”。
- 段落不超过 500 字符时，整段独立作为一个 chunk，不会与相邻段落合并。
- 只有超长段落才在段落内部继续递归切分。
- 递归切分优先级为：段落、换行、中文句末标点、英文句末标点、逗号、空格、字符。
- 为每个 chunk 保留来源文档序号、段落序号和 chunk 序号元数据。

### 2. 混合召回

修改 [server/chat.js](../server/chat.js)：

- 保留原有 OpenAI embedding + FAISS 语义向量召回。
- 新增本地 BM25 关键词检索。
- 中文关键词采用单字和双字词组；英文采用单词 token。
- 向量召回与关键词召回各取 Top 20 候选。
- 使用 Reciprocal Rank Fusion（RRF）合并、去重排序。
- 最终只将 Top 4 个相关 chunk 交给 GPT 生成答案。

当前检索路径：

```text
问题
├─ 向量语义召回 Top 20
└─ BM25 关键词召回 Top 20
        ↓
RRF 融合与去重
        ↓
最终 Top 4 chunk
        ↓
GPT 回答
```

### 3. 持久化文档向量库基础设施

新增 [docker-compose.yml](../docker-compose.yml)、[server/db/init.sql](../server/db/init.sql) 和 [server/.env.example](../server/.env.example)。

技术选型：PostgreSQL 16 + pgvector。

- `documents`：保存 PDF 文件归属、存储路径、内容哈希、embedding 模型和索引状态。
- `document_chunks`：保存 chunk 文本、页码、关键词、1536 维向量和元数据。
- 向量索引：每个分区使用 HNSW + 余弦距离。
- 关键词索引：每个分区使用 GIN 索引。

### 4. 多租户分区

`document_chunks` 按 `owner_id` 做 32 个固定哈希分区：

- 不是每个用户创建一个数据库。
- 同一用户稳定进入一个分区。
- 查询必须带 `owner_id`，使 PostgreSQL 可以只扫描目标分区。
- `document_id + owner_id` 复合外键保证 chunk 与其所属文档属于同一用户。

推荐的查询范围：

```sql
WHERE owner_id = $1
  AND document_id = $2
```

## 已完成的验证

- `node --check server/chat.js`：通过语法检查。
- `git diff --check`：通过。
- `docker compose config`：通过 Compose 配置校验。

## 当前限制与下一步

1. 后端尚未接入 PostgreSQL；`chat.js` 仍会在每个问题请求时临时创建 FAISS 索引。
2. Docker Desktop 守护进程尚未启动，因此本地 PostgreSQL 容器和 schema 尚未实际运行。
3. 下一步应将上传接口改为：上传后异步解析、切块、embedding 并写入 PostgreSQL；聊天接口则按 `owner_id` 和 `document_id` 从已持久化的向量与关键词索引检索。
4. 长期用户记忆应与文档库逻辑隔离，后续建立独立的 `user_memories` 表或 collection。
