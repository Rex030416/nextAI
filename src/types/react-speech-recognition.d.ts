declare module "react-speech-recognition" {
  interface SpeechRecognitionState {
    transcript: string;
    listening: boolean;
    resetTranscript: () => void;
  }

  export function useSpeechRecognition(): SpeechRecognitionState;

  const SpeechRecognition: {
    startListening: () => void;
    stopListening: () => void;
  };

  export default SpeechRecognition;
}
