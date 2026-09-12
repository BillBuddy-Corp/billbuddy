import { create } from 'zustand';

export type SplitMode = 'EQUAL' | 'EXACT' | 'PERCENTAGE' | 'ITEMIZED';

export type ExpenseParticipant = { userId: number; fullName: string };

// A scanned/manually-entered item pending review. `assignedUserIds` is who's
// included; `shares` holds each included person's weight as editable text
// (mirrors exactAmounts/percentages below) -- included-but-blank defaults to
// an equal share of 1, but typing e.g. 2 for one person and 1 for another
// splits that item 2:1 rather than equally.
export type ItemDraft = {
  name: string;
  amount: number;
  quantity: number | null;
  assignedUserIds: number[];
  shares: Record<number, string>;
};

type AddExpenseFormState = {
  participants: ExpenseParticipant[];
  currency: string;
  // the group's own default currency, empty for a non-group (friend)
  // expense -- those never involve an exchange rate. Used to detect when
  // `currency` has diverged and an exchange rate is needed.
  groupDefaultCurrency: string;
  exchangeRate: string;
  amount: string;
  paidByUserId: number | null;
  splitType: SplitMode;
  // who's included in the split -- a subset of participants, relevant for
  // EQUAL/EXACT/PERCENTAGE (EQUAL divides only among these; EXACT/PERCENTAGE
  // only show input rows for these). ITEMIZED ignores this in favor of items.
  splitParticipantIds: number[];
  exactAmounts: Record<number, string>;
  percentages: Record<number, string>;
  items: ItemDraft[];
  receiptFileId: number | null;
  init: (
    participants: ExpenseParticipant[],
    currency: string,
    defaultPayerId: number,
    groupDefaultCurrency?: string
  ) => void;
  setAmount: (amount: string) => void;
  setCurrency: (currency: string) => void;
  setExchangeRate: (rate: string) => void;
  setPaidBy: (userId: number) => void;
  setSplitType: (type: SplitMode) => void;
  toggleSplitParticipant: (userId: number) => void;
  setExactAmount: (userId: number, value: string) => void;
  setPercentage: (userId: number, value: string) => void;
  // Replaces the item list wholesale (e.g. right after a receipt scan),
  // defaulting every item to an equal (share=1 each) split across all
  // current participants.
  setItemsFromScan: (items: { name: string; amount: number; quantity: number | null }[]) => void;
  toggleItemAssignment: (itemIndex: number, userId: number) => void;
  setItemShare: (itemIndex: number, userId: number, value: string) => void;
  setReceiptFileId: (fileId: number | null) => void;
  reset: () => void;
};

const EMPTY_STATE = {
  participants: [] as ExpenseParticipant[],
  currency: '',
  groupDefaultCurrency: '',
  exchangeRate: '',
  amount: '',
  paidByUserId: null as number | null,
  splitType: 'EQUAL' as SplitMode,
  splitParticipantIds: [] as number[],
  exactAmounts: {} as Record<number, string>,
  percentages: {} as Record<number, string>,
  items: [] as ItemDraft[],
  receiptFileId: null as number | null,
};

export function displayName(participant: ExpenseParticipant, currentUserId: number): string {
  return participant.userId === currentUserId ? 'You' : participant.fullName;
}

export const useAddExpenseFormStore = create<AddExpenseFormState>((set) => ({
  ...EMPTY_STATE,

  init: (participants, currency, defaultPayerId, groupDefaultCurrency = '') =>
    set({
      ...EMPTY_STATE,
      participants,
      currency,
      groupDefaultCurrency,
      paidByUserId: defaultPayerId,
      splitParticipantIds: participants.map((p) => p.userId),
    }),

  setAmount: (amount) => set({ amount }),

  setCurrency: (currency) =>
    set((state) => {
      const upper = currency.toUpperCase();
      // Dropping back to the group's own currency means no conversion is
      // needed anymore -- clear any rate typed in for the previous one.
      return upper === state.groupDefaultCurrency ? { currency: upper, exchangeRate: '' } : { currency: upper };
    }),

  setExchangeRate: (exchangeRate) => set({ exchangeRate }),

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

  setItemsFromScan: (items) =>
    set((state) => ({
      items: items.map((item) => ({
        ...item,
        assignedUserIds: state.participants.map((p) => p.userId),
        shares: Object.fromEntries(state.participants.map((p) => [p.userId, '1'])),
      })),
    })),

  toggleItemAssignment: (itemIndex, userId) =>
    set((state) => ({
      items: state.items.map((item, index) => {
        if (index !== itemIndex) return item;
        const has = item.assignedUserIds.includes(userId);
        return {
          ...item,
          assignedUserIds: has
            ? item.assignedUserIds.filter((id) => id !== userId)
            : [...item.assignedUserIds, userId],
          // Turning someone back on defaults them to an equal share of 1
          // again, rather than resurrecting whatever they'd typed before.
          shares: has ? item.shares : { ...item.shares, [userId]: item.shares[userId] ?? '1' },
        };
      }),
    })),

  setItemShare: (itemIndex, userId, value) =>
    set((state) => ({
      items: state.items.map((item, index) =>
        index === itemIndex ? { ...item, shares: { ...item.shares, [userId]: value } } : item
      ),
    })),

  setReceiptFileId: (receiptFileId) => set({ receiptFileId }),

  reset: () => set({ ...EMPTY_STATE }),
}));
