import { MaterialCommunityIcons } from '@expo/vector-icons';

type CategoryStyle = { icon: keyof typeof MaterialCommunityIcons.glyphMap; color: string };

// category is a free-form optional string on the backend (no fixed enum), so
// this is a best-effort match against common values rather than exhaustive.
// Anything unmatched (including null, from expenses created before this
// slice's screens picked up a category selector) falls back to a generic icon.
const CATEGORY_STYLES: Record<string, CategoryStyle> = {
  food: { icon: 'food', color: '#F0883E' },
  groceries: { icon: 'cart-outline', color: '#2BB673' },
  transport: { icon: 'car-outline', color: '#2F6FED' },
  travel: { icon: 'airplane', color: '#2F6FED' },
  rent: { icon: 'home-outline', color: '#8B5CF6' },
  housing: { icon: 'home-outline', color: '#8B5CF6' },
  utilities: { icon: 'flash-outline', color: '#EAB308' },
  entertainment: { icon: 'movie-outline', color: '#EC4899' },
  shopping: { icon: 'shopping-outline', color: '#14B8A6' },
};

const DEFAULT_STYLE: CategoryStyle = { icon: 'receipt', color: '#9CA3AF' };

export function categoryStyle(category: string | null): CategoryStyle {
  if (!category) return DEFAULT_STYLE;
  return CATEGORY_STYLES[category.trim().toLowerCase()] ?? DEFAULT_STYLE;
}
