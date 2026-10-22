// Progress statistics across devices. Each device only writes its own record, so nothing can overwrite anything:
// the totals are combined here. Counters add up, bests take the max, sets are merged, and the streak is computed from
// the union of the days a run was finished, on any device.

const daysBetween = (a, b) => (Date.parse(b) - Date.parse(a)) / 864e5

/** Run days from a record made before days were kept (only streak + lastDay): the streak's days, back from lastDay. */
export function daysOf(s) {
  if (s.days) return s.days
  if (!s.lastDay || !s.streak) return []
  const end = Date.parse(s.lastDay)
  return Array.from({ length: s.streak }, (_, i) => new Date(end - (s.streak - 1 - i) * 864e5).toISOString().slice(0, 10))
}

/** Current streak from run days: one missed day is forgiven; 0 once more than one day has been missed. */
export function streakOf(days, today) {
  const d = [...new Set(days)].sort()
  if (!d.length || daysBetween(d.at(-1), today) > 2) return 0
  let n = 1
  for (let i = d.length - 1; i > 0 && daysBetween(d[i - 1], d[i]) <= 2; i--) n++
  return n
}

/** This device's record plus the other devices' into the totals the app shows. */
export function combine(own, others = [], today) {
  const all = [own, ...others]
  const sum = (k) => all.reduce((n, s) => n + (s[k] ?? 0), 0)
  const days = [...new Set(all.flatMap(daysOf))].sort()
  return {
    points: sum('points'), listened: sum('listened'), talked: sum('talked'), written: sum('written'), spoken: sum('spoken'),
    bestCombo: Math.max(0, ...all.map((s) => s.bestCombo ?? 0)),
    stage: Math.max(0, ...all.map((s) => s.stage ?? 0)),
    read: [...new Set(all.flatMap((s) => s.read ?? []))],
    days, lastDay: days.at(-1) ?? null, streak: streakOf(days, today),
    newToday: all.reduce((n, s) => n + (s.newDay === today ? s.newCount ?? 0 : 0), 0),
  }
}
