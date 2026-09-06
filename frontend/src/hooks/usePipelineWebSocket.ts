import { useEffect, useState, useRef, useCallback } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { LogMessageDto, ProgressUpdateDto } from '../types/console';

export function usePipelineWebSocket(executionId?: string | null) {
  const [isConnected, setIsConnected] = useState(false);
  const [logs, setLogs] = useState<LogMessageDto[]>([]);
  const [progress, setProgress] = useState<ProgressUpdateDto | null>(null);
  const clientRef = useRef<Client | null>(null);

  const clearLogs = useCallback(() => {
    setLogs([]);
  }, []);

  const resetProgress = useCallback(() => {
    setProgress(null);
  }, []);

  useEffect(() => {
    if (!executionId) {
      setIsConnected(false);
      return;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: () => {
        setIsConnected(true);

        // Subscribe to log stream
        client.subscribe(`/topic/pipeline/${executionId}/logs`, (message: IMessage) => {
          try {
            const parsedLog: LogMessageDto = JSON.parse(message.body);
            setLogs((prev) => [...prev, parsedLog]);
          } catch (e) {
            console.error('Failed to parse incoming log message:', e);
          }
        });

        // Subscribe to progress stream
        client.subscribe(`/topic/pipeline/${executionId}/progress`, (message: IMessage) => {
          try {
            const parsedProgress: ProgressUpdateDto = JSON.parse(message.body);
            setProgress(parsedProgress);
          } catch (e) {
            console.error('Failed to parse incoming progress message:', e);
          }
        });
      },
      onDisconnect: () => {
        setIsConnected(false);
      },
      onStompError: (frame) => {
        console.warn('STOMP broker error:', frame.headers['message']);
        setIsConnected(false);
      },
      onWebSocketClose: () => {
        setIsConnected(false);
      },
    });

    clientRef.current = client;
    client.activate();

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [executionId]);

  return {
    isConnected,
    logs,
    progress,
    setLogs,
    clearLogs,
    resetProgress,
  };
}
