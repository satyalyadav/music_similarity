/// <reference types="vite/client" />

declare interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_DEV_SERVER_PORT?: string;
}

declare interface ImportMeta {
  readonly env: ImportMetaEnv;
}
