import { Expense } from '../api/expenses';

// How this expense moved the current user's balance: what they paid minus
// what they owe on it. Returns null when it's a wash (paid exactly their
// own share, or weren't a participant at all) -- nothing worth showing.
export function myImpact(expense: Expense, currentUserId: number): { label: string; className: string } | null {
  const paid = expense.payers.find((p) => p.userId === currentUserId)?.amountPaid ?? 0;
  const owed = expense.splits.find((s) => s.userId === currentUserId)?.amountOwed ?? 0;
  const net = paid - owed;
  if (Math.abs(net) < 0.01) return null;
  return net > 0
    ? { label: `you lent ${net.toFixed(2)}`, className: 'text-green-500' }
    : { label: `you borrowed ${Math.abs(net).toFixed(2)}`, className: 'text-red-400' };
}
