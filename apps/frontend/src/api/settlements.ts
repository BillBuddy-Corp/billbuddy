import { apiClient } from './client';

export type SimplifiedBalance = {
  fromUserId: number;
  fromName: string;
  toUserId: number;
  toName: string;
  amount: number;
};

export type Settlement = {
  id: number;
  groupId: number;
  paidByUserId: number;
  paidByName: string;
  paidToUserId: number;
  paidToName: string;
  amount: number;
  currency: string;
  note: string | null;
  createdByUserId: number;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
};

export type CreateSettlementRequest = {
  paidByUserId: number;
  paidToUserId: number;
  amount: number;
  currency: string;
  note?: string;
};

export async function getSimplifiedBalances(groupId: number): Promise<SimplifiedBalance[]> {
  const { data } = await apiClient.get<SimplifiedBalance[]>(`/groups/${groupId}/balances/simplified`);
  return data;
}

export async function createSettlement(
  groupId: number,
  request: CreateSettlementRequest
): Promise<Settlement> {
  const { data } = await apiClient.post<Settlement>(`/groups/${groupId}/settlements`, request);
  return data;
}
