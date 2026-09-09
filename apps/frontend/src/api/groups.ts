import { apiClient } from './client';

export type GroupRole = 'ADMIN' | 'MEMBER';

export type Group = {
  id: number;
  name: string;
  description: string | null;
  defaultCurrency: string;
  createdByUserId: number;
  createdByName: string;
  memberCount: number;
  currentUserRole: GroupRole;
  createdAt: string;
  updatedAt: string;
};

export type GroupMember = {
  userId: number;
  fullName: string;
  email: string;
  role: GroupRole;
  joinedAt: string;
};

export type CreateGroupRequest = {
  name: string;
  description?: string;
  defaultCurrency: string;
};

export async function listGroups(): Promise<Group[]> {
  const { data } = await apiClient.get<Group[]>('/groups');
  return data;
}

export async function createGroup(request: CreateGroupRequest): Promise<Group> {
  const { data } = await apiClient.post<Group>('/groups', request);
  return data;
}

export async function getGroup(groupId: number): Promise<Group> {
  const { data } = await apiClient.get<Group>(`/groups/${groupId}`);
  return data;
}

export async function listMembers(groupId: number): Promise<GroupMember[]> {
  const { data } = await apiClient.get<GroupMember[]>(`/groups/${groupId}/members`);
  return data;
}
