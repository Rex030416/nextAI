import { Collapse, Spin, Tag, Typography } from "antd";
import type { SourceReference } from "../api/documentApi";

const { Text } = Typography;

export interface ConversationItem {
  question: string;
  answer: string;
  sources: SourceReference[];
  cacheHit?: boolean;
}

interface RenderQAProps {
  conversation: ConversationItem[];
  isLoading: boolean;
}

const RenderQA = ({ conversation, isLoading }: RenderQAProps) => (
  <>
    {conversation.map((item, index) => (
      <div key={`${item.question}-${index}`} className="conversation-item">
        <div className="question-bubble">{item.question}</div>
        <div className="answer-bubble">
          <div>{item.answer}</div>
          {item.cacheHit && <Tag color="green" style={{ marginTop: 8 }}>语义缓存命中</Tag>}
          {item.sources.length > 0 && (
            <Collapse
              ghost
              size="small"
              items={[{
                key: "sources",
                label: `引用片段（${item.sources.length}）`,
                children: item.sources.map((source) => (
                  <p key={source.chunkIndex}>
                    <Text type="secondary">Chunk {source.chunkIndex}</Text> {source.content}
                  </p>
                )),
              }]}
            />
          )}
        </div>
      </div>
    ))}
    {isLoading && <Spin size="large" style={{ margin: 10 }} />}
  </>
);

export default RenderQA;
