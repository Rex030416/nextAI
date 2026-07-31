declare module "speak-tts" {
  interface SpeakOptions {
    text: string;
    queue?: boolean;
  }

  export default class Speech {
    init(options: Record<string, unknown>): Promise<unknown>;
    speak(options: SpeakOptions): Promise<void>;
  }
}
