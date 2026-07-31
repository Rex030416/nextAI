import { useEffect, useState } from "react";
import { Alert, Layout, Tag, Typography, message } from "antd";
import ChatComponent from "./components/ChatComponent";
import PdfUploader from "./components/PdfUploader";
import RenderQA, { type ConversationItem } from "./components/RenderQA";
import { getDocument, type DocumentResponse } from "./api/documentApi";
import "./App.css";

const { Header, Content } = Layout;
const { Title } = Typography;

const App = () => {
  const [conversation, setConversation] = useState<ConversationItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [document, setDocument] = useState<DocumentResponse | null>(null);

  useEffect(() => {
    if (!document || document.status === "READY" || document.status === "FAILED") return undefined;

    const timer = window.setInterval(async () => {
      try {
        const updated = await getDocument(document.documentId);
        setDocument(updated);
        if (updated.status === "FAILED") {
          message.error("文档解析或入库失败，请检查后端日志后重试。");
        }
      } catch {
        message.error("无法读取文档索引状态。");
        window.clearInterval(timer);
      }
    }, 2000);

    return () => window.clearInterval(timer);
  }, [document]);

  return (
    <Layout className="app-layout">
      <Header className="app-header">
        <Title level={3} className="app-title">Next AI</Title>
      </Header>
      <Content className="app-content">
        <PdfUploader
          onUploaded={(uploadedDocument) => {
            setDocument(uploadedDocument);
            setConversation([]);
          }}
        />
        {document && (
          <Alert
            showIcon
            type={document.status === "FAILED" ? "error" : document.status === "READY" ? "success" : "info"}
            message={
              <span>
                当前文档：{document.filename} <Tag>{document.status}</Tag>
              </span>
            }
            description={
              document.status === "READY"
                ? "索引完成，可以开始提问。"
                : "文件正在后台解析并建立持久化索引。"
            }
            style={{ marginTop: 16 }}
          />
        )}
        <section className="conversation-panel">
          <RenderQA conversation={conversation} isLoading={isLoading} />
        </section>
      </Content>
      <div className="chat-dock">
        <ChatComponent
          document={document}
          onAnswer={(item) => setConversation((current) => [...current, item])}
          isLoading={isLoading}
          setIsLoading={setIsLoading}
        />
      </div>
    </Layout>
  );
};

export default App;
