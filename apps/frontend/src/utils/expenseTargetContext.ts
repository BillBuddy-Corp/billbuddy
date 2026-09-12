import { Friend, listFriends } from '../api/friends';
import { getGroup, listMembers } from '../api/groups';
import { ExpenseParticipant } from '../store/addExpenseFormStore';

export type ExpenseTarget = { groupId: number } | { friendUserId: number };

export type ExpenseTargetContext = {
  participants: ExpenseParticipant[];
  currency: string;
  groupDefaultCurrency: string;
  headerLabel: string;
  headerColorSeed: number;
};

// Shared by AddExpenseScreen's own init and the pre-navigation scan flow
// (GroupDetailScreen/FriendDetailScreen), so both resolve "who's in this
// expense and what currency" the same way.
export async function loadExpenseTargetContext(
  target: ExpenseTarget,
  currentUser: { userId: number; fullName: string }
): Promise<ExpenseTargetContext> {
  if ('groupId' in target) {
    const [group, members] = await Promise.all([getGroup(target.groupId), listMembers(target.groupId)]);
    return {
      participants: members.map((m) => ({ userId: m.userId, fullName: m.fullName })),
      currency: group.defaultCurrency,
      groupDefaultCurrency: group.defaultCurrency,
      headerLabel: `With ${group.name}`,
      headerColorSeed: target.groupId,
    };
  }
  const friends = await listFriends();
  const friend = friends.find((f: Friend) => f.userId === target.friendUserId);
  return {
    participants: [
      { userId: currentUser.userId, fullName: currentUser.fullName },
      { userId: target.friendUserId, fullName: friend?.fullName ?? 'Friend' },
    ],
    currency: 'INR',
    groupDefaultCurrency: '',
    headerLabel: `With you and ${friend?.fullName ?? 'them'}`,
    headerColorSeed: target.friendUserId,
  };
}
