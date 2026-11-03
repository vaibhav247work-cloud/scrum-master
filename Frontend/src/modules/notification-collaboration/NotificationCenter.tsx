import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { NotificationSummary, getNotifications, markNotificationRead } from '../../api';

const demoNotifications: NotificationSummary[] = [
  { id: 'demo-notification-1', type: 'WORK_ITEM_UPDATED', title: 'Work item updated', body: 'A task assigned to you changed status.', readAt: null, createdAt: new Date().toISOString() },
  { id: 'demo-notification-2', type: 'SPRINT_SCOPE_CHANGED', title: 'Sprint scope changed', body: 'Checkout Sprint 12 has new committed work.', readAt: new Date(Date.now() - 86400000).toISOString(), createdAt: new Date(Date.now() - 86400000).toISOString() },
];

export function NotificationCenter({ demoMode }: { demoMode: boolean }) {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const query = useQuery({ queryKey: ['notifications'], queryFn: () => getNotifications(), enabled: !demoMode });
  const mutation = useMutation({ mutationFn: markNotificationRead, onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }) });
  const notifications = demoMode ? demoNotifications : query.data ?? [];
  const unreadCount = notifications.filter((notification) => !notification.readAt).length;

  return <div className="notification-center"><button className="icon-button" aria-label="Notifications" aria-expanded={open} onClick={() => setOpen((value) => !value)}>♢{unreadCount > 0 && <span className="notification-dot" />}</button>{open && <div className="notification-popover"><div className="notification-popover-head"><div><strong>Notifications</strong><span>{unreadCount ? `${unreadCount} unread` : 'All caught up'}</span></div><button className="text-button" onClick={() => setOpen(false)}>Close</button></div>{query.isError && <div className="inline-error">Could not load notifications.</div>}{notifications.length === 0 && !query.isLoading && <div className="empty-state">No notifications yet.</div>}{notifications.map((notification) => <NotificationRow key={notification.id} notification={notification} onRead={() => { if (!demoMode && !notification.readAt) mutation.mutate(notification.id); }} />)}</div>}</div>;
}

function NotificationRow({ notification, onRead }: { notification: NotificationSummary; onRead: () => void }) {
  return <button className={`notification-row ${notification.readAt ? 'read' : 'unread'}`} onClick={onRead}><span className="notification-mark" /><span><strong>{notification.title}</strong><small>{notification.body}</small><time>{formatDate(notification.createdAt)}</time></span></button>;
}

function formatDate(value: string) { return new Date(value).toLocaleString(undefined, { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }); }
