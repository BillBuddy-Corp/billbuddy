import { create } from 'zustand';

export type SplitMode = 'EQUAL' | 'EXACT' | 'PERCENTAGE';

export type ExpenseParticipant = { userId: number; fullName: string };

type AddExpenseFormState = {
  participants: ExpenseParticipant[];
  currency: string;
  amount: string;
  paidByUserId: number | null;
  splitType: SplitMode;
  // who's included in the split -- a subset of participants, relevant for
  // all three modes (EQUAL divides only among these; EXACT/PERCENTAGE only
  // show input rows for these)
  splitParticipantIds: number[];
  exactAmounts: Record<number, string>;
  percentages: Record<number, string>;
  init: (participants: ExpenseParticipant[], currency: string, defaultPayerId: number) => void;
  setAmount: (amount: string) => void;
  setCurrency: (currency: string) => void;
  setPaidBy: (userId: number) => void;
  setSplitType: (type: SplitMode) => void;
  toggleSplitParticipant: (userId: number) => void;
  setExactAmount: (userId: number, value: string) => void;
  setPercentage: (userId: number, value: string) => void;
  reset: () => void;
};

const EMPTY_STATE = {
  participants: [] as ExpenseParticipant[],
  currency: '',
  amount: '',
  paidByUserId: null as number | null,
  splitType: 'EQUAL' as SplitMode,
  splitParticipantIds: [] as number[],
  exactAmounts: {} as Record<number, string>,
  percentages: {} as Record<number, string>,
};

export function displayName(participant: ExpenseParticipant, currentUserId: number): string {
  return participant.userId === currentUserId ? 'You' : participant.fullName;
}

export const useAddExpenseFormStore = create<AddExpenseFormState>((set) => ({
  ...EMPTY_STATE,

  init: (participants, currency, defaultPayerId) =>
    set({
      ...EMPTY_STATE,
      participants,
      currency,
      paidByUserId: defaultPayerId,
      splitParticipantIds: participants.map((p) => p.userId),
    }),

  setAmount: (amount) => set({ amount }),

  setCurrency: (currency) => set({ currency: currency.toUpperCase() }),

  setPaidBy: (userId) => set({ paidByUserId: userId }),

  setSplitType: (splitType) => set({ splitType }),

  toggleSplitParticipant: (userId) =>
    set((state) => {
      const has = state.splitParticipantIds.includes(userId);
      return {
        splitParticipantIds: has
          ? state.splitParticipantIds.filter((id) => id !== userId)
          : [...state.splitParticipantIds, userId],
      };
    }),

  setExactAmount: (userId, value) =>
    set((state) => ({ exactAmounts: { ...state.exactAmounts, [userId]: value } })),

  setPercentage: (userId, value) =>
    set((state) => ({ percentages: { ...state.percentages, [userId]: value } })),

  reset: () => set({ ...EMPTY_STATE }),
}));
