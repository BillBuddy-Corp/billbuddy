import { apiClient } from './client';

export type ExpensePayer = { userId: number; fullName: string; amountPaid: number };
export type ExpenseSplit = {
  userId: number;
  fullName: string;
  amountOwed: number;
  percentage: number | null;
};

export type ExpenseItemAssignment = { userId: number; fullName: string; share: number; amountOwed: number };
export type ExpenseItem = { id: number; name: string; amount: number; assignments: ExpenseItemAssignment[] };

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
  // only populated when splitType is ITEMIZED
  items: ExpenseItem[];
  createdAt: string;
  updatedAt: string;
};

export type ExpenseTarget = { groupId: number } | { friendUserId: number };

export type CreateExpenseRequest = {
  description: string;
  amount: number;
  currency: string;
  // required only when currency differs from the group's default currency;
  // ignored (and not required) for non-group friend expenses, which never
  // involve a group default to convert against.
  exchangeRate?: number;
  category?: string;
  paidByUserId: number;
  // set when the expense was created from a scanned receipt (see billScanner.ts)
  receiptFileId?: number;
} & (
  | { splitType: 'EQUAL'; participantUserIds: number[] }
  | { splitType: 'EXACT'; exactAmounts: { userId: number; amount: number }[] }
  | { splitType: 'PERCENTAGE'; percentages: { userId: number; percentage: number }[] }
  | {
      splitType: 'ITEMIZED';
      items: { name: string; amount: number; assignments: { userId: number; share: number }[] }[];
    }
);

export type ExchangeRateSuggestion = {
  fromCurrency: string;
  toCurrency: string;
  rate: number;
  asOf: string;
};

export async function getSuggestedExchangeRate(
  groupId: number,
  fromCurrency: string
): Promise<ExchangeRateSuggestion> {
  const { data } = await apiClient.get<ExchangeRateSuggestion>(`/groups/${groupId}/expenses/exchange-rate`, {
    params: { fromCurrency },
  });
  return data;
}

export async function listGroupExpenses(groupId: number): Promise<Expense[]> {
  const { data } = await apiClient.get<Expense[]>(`/groups/${groupId}/expenses`);
  return data;
}

// Single payer only -- the "multiple people paid" flow is a later slice
// (WhoPaidScreen shows it as a stubbed, disabled option for now).
export async function createExpense(target: ExpenseTarget, request: CreateExpenseRequest): Promise<Expense> {
  const url = 'groupId' in target ? `/groups/${target.groupId}/expenses` : `/friends/${target.friendUserId}/expenses`;
  const { description, amount, currency, exchangeRate, category, paidByUserId, receiptFileId, ...split } = request;
  const { data } = await apiClient.post<Expense>(url, {
    description,
    amount,
    currency,
    exchangeRate,
    category,
    receiptFileId,
    payers: [{ userId: paidByUserId, amountPaid: amount }],
    ...split,
  });
  return data;
}
