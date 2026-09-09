// A small fixed palette, deterministically chosen from an id so the same
// person/group always gets the same color across screens without storing it.
const PALETTE = [
  '#2F6FED', // primary blue
  '#E85D75', // rose
  '#F0883E', // orange
  '#2BB673', // green
  '#8B5CF6', // purple
  '#14B8A6', // teal
  '#EAB308', // gold
  '#EC4899', // pink
];

export function avatarColor(id: number): string {
  return PALETTE[Math.abs(id) % PALETTE.length];
}
