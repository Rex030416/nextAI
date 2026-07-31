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
const CHUNK_SIZE = 500;
const VECTOR_CANDIDATE_K = 20;
const KEYWORD_CANDIDATE_K = 20;
const FINAL_CONTEXT_K = 4;
const RRF_K = 60;
const BM25_K1 = 1.2;
const BM25_B = 0.75;

const tokenize = (text = "") => {
  const normalizedText = text.toLowerCase();
  const chineseSequences = normalizedText.match(/[\u3400-\u9fff]+/g) ?? [];
  const chineseTokens = chineseSequences.flatMap((sequence) => {
    const characters = [...sequence];
    const bigrams = characters.slice(0, -1).map((character, index) => {
      return character + characters[index + 1];
    });
    return [...characters, ...bigrams];
  });
  const wordTokens = normalizedText.match(/[a-z0-9]+(?:[-_][a-z0-9]+)*/g) ?? [];

  return [...chineseTokens, ...wordTokens];
};

const getDocumentKey = (document) => {
  const { sourceDocumentIndex, paragraphIndex, chunkIndex } = document.metadata;
  return `${sourceDocumentIndex}:${paragraphIndex}:${chunkIndex}`;
};

const rankByBm25 = (documents, query) => {
  const queryTerms = [...new Set(tokenize(query))];
  if (queryTerms.length === 0 || documents.length === 0) {
    return [];
  }

  const tokenizedDocuments = documents.map((document) => ({
    document,
    terms: tokenize(document.pageContent),
  }));
  const averageDocumentLength =
    tokenizedDocuments.reduce((total, { terms }) => total + terms.length, 0) /
    tokenizedDocuments.length;
  const documentFrequency = new Map();

  for (const { terms } of tokenizedDocuments) {
    for (const term of new Set(terms)) {
      documentFrequency.set(term, (documentFrequency.get(term) ?? 0) + 1);
    }
  }

  return tokenizedDocuments
    .map(({ document, terms }) => {
      const termFrequency = new Map();
      for (const term of terms) {
        termFrequency.set(term, (termFrequency.get(term) ?? 0) + 1);
      }

      const score = queryTerms.reduce((total, term) => {
        const frequency = termFrequency.get(term) ?? 0;
        if (frequency === 0) {
          return total;
        }

        const frequencyInDocuments = documentFrequency.get(term) ?? 0;
        const inverseDocumentFrequency = Math.log(
          1 +
            (tokenizedDocuments.length - frequencyInDocuments + 0.5) /
              (frequencyInDocuments + 0.5)
        );
        const normalizedTermFrequency =
          (frequency * (BM25_K1 + 1)) /
          (frequency +
            BM25_K1 *
              (1 - BM25_B + BM25_B * (terms.length / averageDocumentLength)));

        return total + inverseDocumentFrequency * normalizedTermFrequency;
      }, 0);

      return { document, score };
    })
    .filter(({ score }) => score > 0)
    .sort((first, second) => second.score - first.score)
    .slice(0, KEYWORD_CANDIDATE_K)
    .map(({ document }) => document);
};

const fuseRankingsWithRrf = (rankings, limit) => {
  const fusedDocuments = new Map();

  for (const ranking of rankings) {
    ranking.forEach((document, index) => {
      const key = getDocumentKey(document);
      const existing = fusedDocuments.get(key) ?? { document, score: 0 };
      existing.score += 1 / (RRF_K + index + 1);
      fusedDocuments.set(key, existing);
    });
  }

  return [...fusedDocuments.values()]
    .sort((first, second) => second.score - first.score)
    .slice(0, limit)
    .map(({ document }) => document);
};

const hybridRetrieve = async (vectorStore, documents, query) => {
  const vectorResults = await vectorStore.similaritySearchWithScore(
    query,
    VECTOR_CANDIDATE_K
  );
  const vectorRanking = vectorResults.map(([document]) => document);
  const keywordRanking = rankByBm25(documents, query);

  return fuseRankingsWithRrf(
    [vectorRanking, keywordRanking],
    FINAL_CONTEXT_K
  );
};

const splitDocumentsByParagraph = async (documents, textSplitter) => {
  const chunks = [];

  for (const [sourceDocumentIndex, document] of documents.entries()) {
    const paragraphs = document.pageContent
      .split(/\r?\n\s*\r?\n+/)
      .map((paragraph) => paragraph.trim())
      .filter(Boolean);

    for (const [paragraphIndex, paragraph] of paragraphs.entries()) {
      const metadata = {
        ...document.metadata,
        sourceDocumentIndex,
        paragraphIndex,
      };

      // 每个段落单独送入切分器，因此短段落绝不会和相邻段落合并。
      // 只有超过 CHUNK_SIZE 的段落才会按下方的句末优先级继续切分。
      const paragraphChunks = await textSplitter.createDocuments(
        [paragraph],
        [metadata]
      );
      paragraphChunks.forEach((chunk, chunkIndex) => {
        chunk.metadata = {
          ...chunk.metadata,
          chunkIndex,
        };
      });
      chunks.push(...paragraphChunks);
    }
  }

  return chunks;
};

const chat = async (filePath = "./uploads/your-default-file.pdf", query) => {
  const loader = new UnstructuredLoader(filePath, {
    apiUrl: process.env.UNSTRUCTURED_API_URL,
    apiKey: process.env.UNSTRUCTURED_API_KEY,
  });
  const data = await loader.load();

  const textSplitter = new RecursiveCharacterTextSplitter({
    chunkSize: CHUNK_SIZE,
    chunkOverlap: 0,
    // 按语义边界递归切分：先保留段落，再优先在句末结束。
    separators: [
      "\n\n",
      "\n",
      "。",
      "！",
      "？",
      "；",
      ". ",
      "! ",
      "? ",
      "; ",
      "，",
      ", ",
      " ",
      "",
    ],
  });

  const splitDocs = await splitDocumentsByParagraph(data, textSplitter);

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

  const retriever = {
    getRelevantDocuments: async (question) => {
      return hybridRetrieve(vectorStore, splitDocs, question);
    },
  };

  const chain = RetrievalQAChain.fromLLM(model, retriever, {
    prompt: PromptTemplate.fromTemplate(template),
  });

  const response = await chain.call({
    query,
  });

  return response;
};

export default chat;
