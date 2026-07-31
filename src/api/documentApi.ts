import axios from "axios";

export type DocumentStatus = "UPLOADED" | "INDEXING" | "READY" | "FAILED";

export interface DocumentResponse {
  documentId: number;
  filename: string;
  status: DocumentStatus;
  createdAt: string;
  indexedAt: string | null;
}

export interface SourceReference {
  chunkIndex: number;
  pageNumber: number | null;
  content: string;
}

export interface AskDocumentResponse {
  answer: string;
  sources: SourceReference[];
  sessionId: string;
  cacheHit: boolean;
  cacheSimilarity: number | null;
}

const ownerStorageKey = "nextai-owner-id";

const getDevelopmentOwnerId = (): string => {
  const existing = window.localStorage.getItem(ownerStorageKey);
  if (existing) return existing;

  const ownerId = window.crypto.randomUUID();
  window.localStorage.setItem(ownerStorageKey, ownerId);
  return ownerId;
};

const client = axios.create({
  baseURL: process.env.REACT_APP_API_BASE_URL ?? "http://localhost:8081/api/v1",
});

client.interceptors.request.use((config) => {
  // 临时开发身份；生产环境由登录后的 access token / session 替代。
  config.headers.set("X-Owner-Id", getDevelopmentOwnerId());
  return config;
});

export const uploadDocument = async (file: File): Promise<DocumentResponse> => {
  const formData = new FormData();
  formData.append("file", file);
  const response = await client.post<DocumentResponse>("/documents", formData);
  return response.data;
};

export const getDocument = async (documentId: number): Promise<DocumentResponse> => {
  const response = await client.get<DocumentResponse>(`/documents/${documentId}`);
  return response.data;
};

export const askDocument = async (
  documentId: number,
  question: string,
  sessionId?: string | null,
): Promise<AskDocumentResponse> => {
  const response = await client.post<AskDocumentResponse>(
    `/documents/${documentId}/questions`,
    { question, sessionId: sessionId ?? null },
  );
  return response.data;
};
