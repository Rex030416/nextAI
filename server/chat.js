import { RecursiveCharacterTextSplitter } from "langchain/text_splitter";
import { OpenAIEmbeddings } from "langchain/embeddings/openai";
import { FaissStore } from "langchain/vectorstores/faiss";
import { RetrievalQAChain } from "langchain/chains";
import { ChatOpenAI } from "langchain/chat_models/openai";
import { PromptTemplate } from "langchain/prompts";
import { UnstructuredLoader } from "langchain/document_loaders/fs/unstructured";
import { IndexFlatL2, IndexIVFFlat } from "faiss-node";

const DIMENSION = 1536; // OpenAI embedding 维度
const NLIST = 10; // IVF 聚类数（分成10个区域）

const chat = async (filePath = "./uploads/your-default-file.pdf", query) => {
  const loader = new UnstructuredLoader(filePath, {
    apiUrl: process.env.UNSTRUCTURED_API_URL,
    apiKey: process.env.UNSTRUCTURED_API_KEY,
  });
  const data = await loader.load();

  const textSplitter = new RecursiveCharacterTextSplitter({
    chunkSize: 500,
    chunkOverlap: 0,
  });

  const splitDocs = await textSplitter.splitDocuments(data);

  const embeddings = new OpenAIEmbeddings({
    openAIApiKey: process.env.REACT_APP_OPENAI_API_KEY,
  });

  // 当文档块数足够多时使用 IVF 索引，否则回退到 Flat
  let vectorStore;
  if (splitDocs.length >= 50) {
    const quantizer = new IndexFlatL2(DIMENSION);
    const ivfIndex = new IndexIVFFlat(quantizer, DIMENSION, NLIST);
    vectorStore = await FaissStore.fromDocuments(splitDocs, embeddings, {
      index: ivfIndex,
    });
  } else {
    vectorStore = await FaissStore.fromDocuments(splitDocs, embeddings);
  }

  const model = new ChatOpenAI({
    modelName: "gpt-3.5-turbo",
    openAIApiKey: process.env.REACT_APP_OPENAI_API_KEY,
  });

  const template = `Use the following pieces of context to answer the question at the end.
If you don't know the answer, just say that you don't know, don't try to make up an answer.
Use three sentences maximum and keep the answer as concise as possible.

{context}
Question: {question}
Helpful Answer:`;

  const chain = RetrievalQAChain.fromLLM(model, vectorStore.asRetriever(), {
    prompt: PromptTemplate.fromTemplate(template),
  });

  const response = await chain.call({
    query,
  });

  return response;
};

export default chat;
