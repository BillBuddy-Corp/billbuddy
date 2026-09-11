import { apiClient } from './client';

export type UploadedFile = {
  id: number;
  url: string;
  contentType: string;
  fileSizeBytes: number;
  createdAt: string;
};

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
