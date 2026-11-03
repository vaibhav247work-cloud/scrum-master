import { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { WorkHubView } from './WorkHubView';
import { createTimeEntry, getCurrentTimesheet, getWorkHub } from './api';

vi.mock('./api', () => ({
  createTimeEntry: vi.fn(),
  createWorkItem: vi.fn(),
  getWorkHub: vi.fn(),
  getTimeCategories: vi.fn(),
  getCurrentTimesheet: vi.fn(),
  updateWorkItem: vi.fn(),
}));

const mockedGetWorkHub = vi.mocked(getWorkHub);
const mockedCreateTimeEntry = vi.mocked(createTimeEntry);
const mockedGetCurrentTimesheet = vi.mocked(getCurrentTimesheet);

function renderHub(node: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

describe('WorkHubView', () => {
  beforeEach(() => {
    mockedGetWorkHub.mockReset();
    mockedCreateTimeEntry.mockReset();
    mockedGetCurrentTimesheet.mockReset();
    localStorage.clear();
  });

  it('opens task context and preselects that task for time entry in demo mode', async () => {
    renderHub(<WorkHubView demoMode userId="demo-user" />);

    fireEvent.click(screen.getByRole('button', { name: /Finalize checkout API contract/ }));

    expect(screen.getByRole('heading', { name: 'Finalize checkout API contract' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Log time on this task' }));

    expect(screen.getByLabelText('Work item')).toHaveValue('demo-1');
    expect(screen.getByLabelText('Description')).toBeRequired();
  });

  it('shows an empty state when the live work hub has no assigned work', async () => {
    mockedGetWorkHub.mockResolvedValue({ workItems: [], recentTimeEntries: [] });
    renderHub(<WorkHubView demoMode={false} userId="user-1" />);

    expect(await screen.findByText('No assigned work yet.')).toBeInTheDocument();
    expect(screen.getByText('No time entries recorded yet.')).toBeInTheDocument();
  });

  it('shows a safe error state when the live work hub cannot load', async () => {
    mockedGetWorkHub.mockRejectedValue(new Error('request failed'));
    renderHub(<WorkHubView demoMode={false} userId="user-1" />);

    expect(await screen.findByRole('alert')).toHaveTextContent('Could not load live work');
  });

  it('sends a central time entry with the selected task and description', async () => {
    mockedGetWorkHub.mockResolvedValue({ workItems: [{
      id: 'work-1', workKey: 'W-1', projectId: null, sprintId: null, assigneeUserId: 'user-1',
      title: 'Review release notes', description: 'Check the final release summary.', workType: 'TASK',
      priority: 'HIGH', status: 'TODO', createdAt: new Date().toISOString(),
    }], recentTimeEntries: [] });
    mockedCreateTimeEntry.mockResolvedValue({
      id: 'time-1', userId: 'user-1', workItemId: 'work-1', projectId: null, sprintId: null,
      entryDate: new Date().toISOString().slice(0, 10), minutes: 45, description: 'Reviewed notes',
      source: 'QUICK_HOURS', createdAt: new Date().toISOString(),
    });
    renderHub(<WorkHubView demoMode={false} userId="user-1" />);

    await screen.findByRole('button', { name: /Review release notes/ });
    const taskRow = screen.getByText('Review release notes').closest('.work-item');
    expect(taskRow).not.toBeNull();
    fireEvent.click(within(taskRow as HTMLElement).getByRole('button', { name: 'Log time' }));
    fireEvent.change(screen.getByLabelText('Minutes'), { target: { value: '45' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Reviewed notes' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save time' }));

    await waitFor(() => expect(mockedCreateTimeEntry).toHaveBeenCalled());
    expect(mockedCreateTimeEntry.mock.calls[0][0]).toEqual(expect.objectContaining({
      workItemId: 'work-1', minutes: 45, description: 'Reviewed notes', source: 'QUICK_HOURS',
    }));
    expect(await screen.findByRole('status')).toHaveTextContent('Time saved to your central timesheet.');
  });

  it('starts and stops a persistent timer before saving a TIMER entry', async () => {
    const item = {
      id: 'work-timer', workKey: 'W-TIMER', projectId: 'project-1', sprintId: 'sprint-1', assigneeUserId: 'user-1',
      title: 'Investigate checkout latency', description: 'Trace the slow payment response.', workType: 'TASK',
      priority: 'HIGH', status: 'IN_PROGRESS', createdAt: new Date().toISOString(),
    };
    mockedGetWorkHub.mockResolvedValue({ workItems: [item], recentTimeEntries: [] });
    mockedCreateTimeEntry.mockResolvedValue({
      id: 'timer-entry', userId: 'user-1', workItemId: 'work-timer', projectId: 'project-1', sprintId: 'sprint-1',
      entryDate: new Date().toISOString().slice(0, 10), minutes: 1, description: 'Traced latency.', source: 'TIMER', createdAt: new Date().toISOString(),
    });
    renderHub(<WorkHubView demoMode={false} userId="user-1" />);

    await screen.findByText('Investigate checkout latency');
    fireEvent.click(screen.getByRole('button', { name: 'Start timer' }));
    expect(screen.getByText('TIMER RUNNING')).toBeInTheDocument();
    expect(localStorage.getItem('company-os:active-timer:user-1')).toContain('work-timer');

    fireEvent.click(screen.getAllByRole('button', { name: 'Stop timer' })[0]);
    expect(screen.getByRole('button', { name: 'Save time' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Traced latency.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save time' }));

    await waitFor(() => expect(mockedCreateTimeEntry).toHaveBeenCalled());
    expect(mockedCreateTimeEntry.mock.calls[0][0]).toEqual(expect.objectContaining({
      workItemId: 'work-timer', source: 'TIMER',
    }));
  });
});
