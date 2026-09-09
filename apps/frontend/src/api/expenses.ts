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

export type ExpenseTarget = { groupId: number } | { friendUserId: number };

export type CreateExpenseRequest = {
  description: string;
  amount: number;
  currency: string;
  category?: string;
  paidByUserId: number;
} & (
  | { splitType: 'EQUAL'; participantUserIds: number[] }
  | { splitType: 'EXACT'; exactAmounts: { userId: number; amount: number }[] }
  | { splitType: 'PERCENTAGE'; percentages: { userId: number; percentage: number }[] }
);

export async function listGroupExpenses(groupId: number): Promise<Expense[]> {
  const { data } = await apiClient.get<Expense[]>(`/groups/${groupId}/expenses`);
  return data;
}

// Single payer only -- the "multiple people paid" flow is a later slice
// (WhoPaidScreen shows it as a stubbed, disabled option for now).
export async function createExpense(target: ExpenseTarget, request: CreateExpenseRequest): Promise<Expense> {
  const url = 'groupId' in target ? `/groups/${target.groupId}/expenses` : `/friends/${target.friendUserId}/expenses`;
  const { description, amount, currency, category, paidByUserId, ...split } = request;
  const { data } = await apiClient.post<Expense>(url, {
    description,
    amount,
    currency,
    category,
    payers: [{ userId: paidByUserId, amountPaid: amount }],
    ...split,
  });
  return data;
}
