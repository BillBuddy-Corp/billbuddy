import { apiClient } from './client';

export type ReceiptScanItem = {
  name: string;
  amount: number;
  quantity: number | null;
};

export type ReceiptScan = {
  fileId: number;
  merchant: string | null;
  amount: number | null;
  currency: string | null;
  transactionDate: string | null;
  otherDiscount: number | null;
  voucherAmount: number | null;
  subtotal: number | null;
  needsReview: boolean;
  discountsNeedReview: boolean;
  items: ReceiptScanItem[];
};

export async function scanReceipt(fileId: number): Promise<ReceiptScan> {
  const { data } = await apiClient.post<ReceiptScan>(`/files/${fileId}/scan-receipt`);
  return data;
}
