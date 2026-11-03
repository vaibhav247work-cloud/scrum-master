export type ActiveTimer = { workItemId: string; startedAt: number };

export function timerKey(userId?: string) {
  return `company-os:active-timer:${userId ?? 'anonymous'}`;
}

export function readTimer(userId?: string): ActiveTimer | null {
  try {
    const value = JSON.parse(localStorage.getItem(timerKey(userId)) ?? 'null') as ActiveTimer | null;
    return value && typeof value.workItemId === 'string' && Number.isFinite(value.startedAt) ? value : null;
  } catch {
    return null;
  }
}

export function formatDuration(milliseconds: number) {
  const totalSeconds = Math.max(0, Math.floor(milliseconds / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${hours.toString().padStart(2, '0')}:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
}

export function elapsedMinutes(startedAt: number) {
  return Math.max(1, Math.round((Date.now() - startedAt) / 60000));
}
