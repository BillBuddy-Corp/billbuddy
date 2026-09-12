import { ENV } from '../config/env';
import { apiClient } from './client';
import { getAccessToken } from './tokenHolder';

export type UploadedFile = {
  id: number;
  url: string;
  contentType: string;
  fileSizeBytes: number;
  createdAt: string;
};

// The backend's `url` fields (UploadedFile.url, Expense.receiptUrl) are
// relative paths like "/api/v1/files/42" -- not a full origin, and not
// presigned. GET /files/{id} requires a bearer token, so plain <Image
// source={{uri}}> can't load it; source={{uri, headers}} is required.
const API_ORIGIN = ENV.API_BASE_URL.replace(/\/api\/v1\/?$/, '');

export function authenticatedImageSource(relativeUrl: string): { uri: string; headers: Record<string, string> } {
  const token = getAccessToken();
  return {
    uri: `${API_ORIGIN}${relativeUrl}`,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  };
}

// `uri` is a local file:// (or content://) URI from the image picker. RN's
// FormData/fetch implementation fills in the multipart boundary itself, so
// the Content-Type header only needs the type, not a manually-built boundary.
export async function uploadFile(uri: string, fileName: string, mimeType: string): Promise<UploadedFile> {
  const form = new FormData();
  form.append('file', { uri, name: fileName, type: mimeType } as unknown as Blob);
  const { data } = await apiClient.post<UploadedFile>('/files', form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return data;
}
