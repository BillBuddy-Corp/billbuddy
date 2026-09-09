import { apiClient } from './client';

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

export type FriendExpensePayer = { userId: number; fullName: string; amountPaid: number };
export type FriendExpenseSplit = {
  userId: number;
  fullName: string;
  amountOwed: number;
  percentage: number | null;
};

export type FriendExpense = {
  id: number;
  groupId: number | null;
  description: string;
  amount: number;
  currency: string;
  convertedAmount: number;
  exchangeRate: number;
  category: string | null;
  receiptUrl: string | null;
  splitType: 'EQUAL' | 'PERCENTAGE' | 'EXACT' | 'ITEMIZED';
  createdByUserId: number;
  createdByName: string;
  payers: FriendExpensePayer[];
  splits: FriendExpenseSplit[];
  createdAt: string;
  updatedAt: string;
};

export type CreateFriendExpenseRequest = {
  description: string;
  amount: number;
  currency: string;
  // must be either the caller's or the friend's own userId -- these are the only two valid
  // participants for a non-group expense
  paidByUserId: number;
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

export async function listFriendExpenses(friendUserId: number): Promise<FriendExpense[]> {
  const { data } = await apiClient.get<FriendExpense[]>(`/friends/${friendUserId}/expenses`);
  return data;
}

// getFriendBalance() intentionally combines shared-group debt with non-group debt (that's the
// whole point of the Friends balance feature). That makes it the wrong source for a "non-group
// only" total -- using it there double-counts any shared-group debt. This mirrors the backend's
// own paid-minus-owed math (BalanceService.getFriendBalance), computed client-side from the
// non-group expenses list instead, since there's no "non-group only" endpoint.
export function nonGroupBalanceFromExpenses(expenses: FriendExpense[], currentUserId: number): FriendBalance[] {
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

// Always an equal split between the caller and the friend -- the only split type this slice's
// UI supports; the backend itself handles PERCENTAGE/EXACT/ITEMIZED too, for future UI.
export async function createFriendExpense(
  friendUserId: number,
  currentUserId: number,
  request: CreateFriendExpenseRequest
): Promise<FriendExpense> {
  const { data } = await apiClient.post<FriendExpense>(`/friends/${friendUserId}/expenses`, {
    description: request.description,
    amount: request.amount,
    currency: request.currency,
    splitType: 'EQUAL',
    payers: [{ userId: request.paidByUserId, amountPaid: request.amount }],
    participantUserIds: [currentUserId, friendUserId],
  });
  return data;
}
