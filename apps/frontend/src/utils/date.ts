function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

export function formatDateHeader(iso: string): string {
  const date = new Date(iso);
  const today = new Date();

  if (isSameDay(date, today)) return 'Today';

  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  if (isSameDay(date, yesterday)) return 'Yesterday';

  return date.toLocaleDateString(undefined, {
    month: 'long',
    day: 'numeric',
    year: date.getFullYear() === today.getFullYear() ? undefined : 'numeric',
  });
}

export type DateSection<T> = { title: string; data: T[] };

// Groups a list already sorted newest-first into SectionList sections keyed
// by formatDateHeader -- consecutive items sharing a header stay in one
// section, matching the input order rather than re-sorting.
export function groupByDate<T>(items: T[], getCreatedAt: (item: T) => string): DateSection<T>[] {
  const sections: DateSection<T>[] = [];
  for (const item of items) {
    const title = formatDateHeader(getCreatedAt(item));
    const last = sections[sections.length - 1];
    if (last && last.title === title) {
      last.data.push(item);
    } else {
      sections.push({ title, data: [item] });
    }
  }
  return sections;
}
