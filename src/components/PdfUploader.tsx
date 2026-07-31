import { InboxOutlined } from "@ant-design/icons";
import { message, Upload } from "antd";
import type { UploadProps } from "antd";
import { uploadDocument, type DocumentResponse } from "../api/documentApi";

const { Dragger } = Upload;

interface PdfUploaderProps {
  onUploaded: (document: DocumentResponse) => void;
}

const PdfUploader = ({ onUploaded }: PdfUploaderProps) => {
  const uploadProps: UploadProps = {
    name: "file",
    multiple: false,
    accept: ".pdf,.doc,.docx,.txt,.md",
    customRequest: async ({ file, onSuccess, onError }) => {
      try {
        const document = await uploadDocument(file as File);
        onUploaded(document);
        onSuccess?.(document);
      } catch (error) {
        const detail = error instanceof Error ? error.message : "Upload failed";
        onError?.(new Error(detail));
      }
    },
    onChange(info) {
      if (info.file.status === "done") {
        message.success(`${info.file.name} 上传成功，正在建立索引。`);
      }
      if (info.file.status === "error") {
        message.error(`${info.file.name} 上传失败。`);
      }
    },
  };

  return (
    <Dragger {...uploadProps}>
      <p className="ant-upload-drag-icon"><InboxOutlined /></p>
      <p className="ant-upload-text">点击或拖拽上传文档</p>
      <p className="ant-upload-hint">支持 PDF、DOC、DOCX、TXT、MD；每次问答绑定当前文档。</p>
    </Dragger>
  );
};

export default PdfUploader;
