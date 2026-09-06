export interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  timestamp: string;
}

export interface PageableResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}
