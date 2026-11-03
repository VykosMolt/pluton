// Local progress statistics. Every number here comes from this device only; Pluton has no accounts or sync.

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

/** This device's record, with the fields the UI reads and a streak computed from its run days. */
export function combine(own, today) {
  const days = [...new Set(daysOf(own))].sort()
  return {
    points: own.points ?? 0,
    listened: own.listened ?? 0,
    talked: own.talked ?? 0,
    written: own.written ?? 0,
    spoken: own.spoken ?? 0,
    bestCombo: own.bestCombo ?? 0,
    stage: own.stage ?? 0,
    read: [...new Set(own.read ?? [])],
    days,
    lastDay: days.at(-1) ?? null,
    streak: streakOf(days, today),
    newToday: own.newDay === today ? own.newCount ?? 0 : 0,
  }
}
