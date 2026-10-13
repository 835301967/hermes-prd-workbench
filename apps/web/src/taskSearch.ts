export function filterTasks<T extends { title: string }>(tasks: T[], query: string, currentTitle: string): T[] {
  const q = query.trim();
  const current = currentTitle.trim();
  if (!q || q === current) {
    return tasks;
  }
  const needle = q.toLowerCase();
  return tasks.filter((item) => item.title.toLowerCase().includes(needle));
}
