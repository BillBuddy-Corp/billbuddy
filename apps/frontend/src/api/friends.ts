import { apiClient } from './client';
import { Expense } from './expenses';

export type Friend = {
  userId: number;
  fullName: string;
  email: string;
  friendsSince: string;
};

export type FriendBalance = {
  currency: string;
  // positive: the friend owes you. negative: you owe the friend.
  amount: number;
};

export async function addFriend(email: string): Promise<Friend> {
  const { data } = await apiClient.post<Friend>('/friends', { email });
  return data;
}

export async function listFriends(): Promise<Friend[]> {
  const { data } = await apiClient.get<Friend[]>('/friends');
  return data;
}

export async function removeFriend(friendUserId: number): Promise<void> {
  await apiClient.delete(`/friends/${friendUserId}`);
}

export async function getFriendBalance(friendUserId: number): Promise<FriendBalance[]> {
  const { data } = await apiClient.get<FriendBalance[]>(`/friends/${friendUserId}/balance`);
  return data;
}

export async function listFriendExpenses(friendUserId: number): Promise<Expense[]> {
  const { data } = await apiClient.get<Expense[]>(`/friends/${friendUserId}/expenses`);
  return data;
}

// getFriendBalance() intentionally combines shared-group debt with non-group debt (that's the
// whole point of the Friends balance feature). That makes it the wrong source for a "non-group
// only" total -- using it there double-counts any shared-group debt. This mirrors the backend's
// own paid-minus-owed math (BalanceService.getFriendBalance), computed client-side from the
// non-group expenses list instead, since there's no "non-group only" endpoint.
export function nonGroupBalanceFromExpenses(expenses: Expense[], currentUserId: number): FriendBalance[] {
  const byCurrency = new Map<string, number>();
  for (const expense of expenses) {
    for (const payer of expense.payers) {
      if (payer.userId === currentUserId) {
        byCurrency.set(expense.currency, (byCurrency.get(expense.currency) ?? 0) + payer.amountPaid);
      }
    }
    for (const split of expense.splits) {
      if (split.userId === currentUserId) {
        byCurrency.set(expense.currency, (byCurrency.get(expense.currency) ?? 0) - split.amountOwed);
      }
    }
  }
  return Array.from(byCurrency, ([currency, amount]) => ({ currency, amount }));
}
