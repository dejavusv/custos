export interface ExternalNotifyTask {
  taskId: string;
  title: string;
  chatbotId: string;
  chatbotName?: string | null;
  recipients: string[];
}

export interface ExternalNotifySendRequest {
  credentialId: string;
  taskId: string;
  message: string;
}

export interface ExternalNotifySendResult {
  sentCount: number;
  failedRecipients: string[];
  message?: string;
}
