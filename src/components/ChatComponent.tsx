import { useEffect, useState } from "react";
import { AudioOutlined } from "@ant-design/icons";
import { Button, Input, message } from "antd";
import SpeechRecognition, { useSpeechRecognition } from "react-speech-recognition";
import Speech from "speak-tts";
import { askDocument, type DocumentResponse } from "../api/documentApi";
import type { ConversationItem } from "./RenderQA";

const { Search } = Input;
const sessionStoragePrefix = "nextai-chat-session-";

interface ChatComponentProps {
  document: DocumentResponse | null;
  onAnswer: (item: ConversationItem) => void;
  isLoading: boolean;
  setIsLoading: (isLoading: boolean) => void;
}

const ChatComponent = ({ document, onAnswer, isLoading, setIsLoading }: ChatComponentProps) => {
  const [searchValue, setSearchValue] = useState("");
  const [isChatModeOn, setIsChatModeOn] = useState(false);
  const [isRecording, setIsRecording] = useState(false);
  const [speech, setSpeech] = useState<Speech | null>(null);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const { transcript, listening, resetTranscript } = useSpeechRecognition();
  const documentId = document?.documentId;

  useEffect(() => {
    const speechEngine = new Speech();
    speechEngine.init({
      volume: 1,
      lang: "zh-CN",
      rate: 1,
      pitch: 1,
      splitSentences: true,
    }).then(() => setSpeech(speechEngine)).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!documentId) {
      setSessionId(null);
      return;
    }
    setSessionId(window.localStorage.getItem(`${sessionStoragePrefix}${documentId}`));
  }, [documentId]);

  const onSearch = async (question: string) => {
    const normalizedQuestion = question.trim();
    if (!normalizedQuestion) return;
    if (!document) {
      message.warning("请先上传一份文档。");
      return;
    }
    if (document.status !== "READY") {
      message.info("文档仍在建立索引，请稍候。");
      return;
    }

    setSearchValue("");
    setIsLoading(true);
    try {
      const response = await askDocument(document.documentId, normalizedQuestion, sessionId);
      setSessionId(response.sessionId);
      window.localStorage.setItem(`${sessionStoragePrefix}${document.documentId}`, response.sessionId);
      onAnswer({
        question: normalizedQuestion,
        answer: response.answer,
        sources: response.sources,
        cacheHit: response.cacheHit,
      });
      if (isChatModeOn && speech) {
        await speech.speak({ text: response.answer, queue: false });
      }
    } catch (error) {
      const detail = error instanceof Error ? error.message : "请求失败";
      onAnswer({ question: normalizedQuestion, answer: detail, sources: [] });
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    if (!listening && transcript) {
      void onSearch(transcript);
      setIsRecording(false);
      resetTranscript();
    }
  // onSearch captures current document and controls; voice recognition should react only on stop.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [listening, transcript]);

  const toggleChatMode = () => {
    setIsChatModeOn((current) => !current);
    setIsRecording(false);
    SpeechRecognition.stopListening();
  };

  const toggleRecording = () => {
    if (isRecording) {
      SpeechRecognition.stopListening();
      setIsRecording(false);
      return;
    }
    resetTranscript();
    SpeechRecognition.startListening();
    setIsRecording(true);
  };

  return (
    <div className="search-container">
      {!isChatModeOn && (
        <Search
          placeholder="输入你对当前文档的问题"
          enterButton="提问"
          size="large"
          onSearch={onSearch}
          loading={isLoading}
          value={searchValue}
          onChange={(event) => setSearchValue(event.target.value)}
        />
      )}
      <Button type="primary" size="large" danger={isChatModeOn} onClick={toggleChatMode} style={{ marginLeft: 5 }}>
        语音模式：{isChatModeOn ? "开" : "关"}
      </Button>
      {isChatModeOn && (
        <Button
          type="primary"
          icon={<AudioOutlined />}
          size="large"
          danger={isRecording}
          onClick={toggleRecording}
          style={{ marginLeft: 5 }}
        >
          {isRecording ? "正在录音…" : "开始录音"}
        </Button>
      )}
    </div>
  );
};

export default ChatComponent;
