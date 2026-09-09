import { apiClient } from './client';

export type ExpensePayer = { userId: number; fullName: string; amountPaid: number };
export type ExpenseSplit = {
  userId: number;
  fullName: string;
  amountOwed: number;
  percentage: number | null;
};

// Shape returned for both group expenses (GET /groups/{id}/expenses) and
// non-group friend expenses (GET /friends/{id}/expenses) -- the backend
// response is identical either way, groupId is just null for the latter.
export type Expense = {
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
  payers: ExpensePayer[];
  splits: ExpenseSplit[];
  createdAt: string;
  updatedAt: string;
};

export type CreateGroupExpenseRequest = {
  description: string;
  amount: number;
  currency: string;
  paidByUserId: number;
  participantUserIds: number[];
};

export async function listGroupExpenses(groupId: number): Promise<Expense[]> {
  const { data } = await apiClient.get<Expense[]>(`/groups/${groupId}/expenses`);
  return data;
}

// Always an equal split among participantUserIds -- the only split type
// this slice's UI supports; the backend also handles PERCENTAGE/EXACT/
// ITEMIZED for the redesigned add-expense flow in a later slice.
export async function createGroupExpense(
  groupId: number,
  request: CreateGroupExpenseRequest
): Promise<Expense> {
  const { data } = await apiClient.post<Expense>(`/groups/${groupId}/expenses`, {
    description: request.description,
    amount: request.amount,
    currency: request.currency,
    splitType: 'EQUAL',
    payers: [{ userId: request.paidByUserId, amountPaid: request.amount }],
    participantUserIds: request.participantUserIds,
  });
  return data;
}
