import { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SprintWorkspaceView } from './modules/sprint-management/SprintWorkspaceView';
import { createTimeEntry, createWorkItem, getWorkItems, updateWorkItem } from './api';

vi.mock('./api', () => ({
  createTimeEntry: vi.fn(),
  createWorkItem: vi.fn(),
  getWorkItems: vi.fn(),
  getTimeCategories: vi.fn(),
  updateWorkItem: vi.fn(),
}));

const mockedGetWorkItems = vi.mocked(getWorkItems);
const mockedCreateTimeEntry = vi.mocked(createTimeEntry);
const mockedCreateWorkItem = vi.mocked(createWorkItem);
const mockedUpdateWorkItem = vi.mocked(updateWorkItem);

function renderWorkspace(node: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

describe('SprintWorkspaceView', () => {
  beforeEach(() => {
    mockedGetWorkItems.mockReset();
    mockedCreateTimeEntry.mockReset();
    mockedCreateWorkItem.mockReset();
    mockedUpdateWorkItem.mockReset();
    localStorage.clear();
  });

  it('keeps sprint work in project context and advances a live item', async () => {
    const item = {
      id: 'work-1', workKey: 'COM-1', projectId: 'project-1', sprintId: 'sprint-1', assigneeUserId: null,
      title: 'Implement payment retry', description: 'Retry transient payment failures.', workType: 'TASK',
      priority: 'HIGH', status: 'TODO', createdAt: new Date().toISOString(),
    };
    mockedGetWorkItems.mockResolvedValue([item]);
    mockedUpdateWorkItem.mockResolvedValue({ ...item, status: 'IN_PROGRESS' });

    renderWorkspace(<SprintWorkspaceView demoMode={false} projectId="project-1" sprintId="sprint-1" projectName="Commerce" sprintName="Checkout 12" onBack={vi.fn()} />);

    const row = await screen.findByText('Implement payment retry');
    expect(mockedGetWorkItems).toHaveBeenCalledWith({ sprintId: 'sprint-1' });
    fireEvent.click(within(row.closest('.work-item') as HTMLElement).getByRole('button', { name: 'Start work' }));

    await waitFor(() => expect(mockedUpdateWorkItem).toHaveBeenCalledWith('work-1', { status: 'IN_PROGRESS' }));
  });

  it('shows the demo workspace without making live mutations', () => {
    renderWorkspace(<SprintWorkspaceView demoMode projectId="demo-commerce" sprintId="demo-sprint" projectName="Commerce" sprintName="Checkout 12" onBack={vi.fn()} />);

    expect(screen.getByText('Resolve tax calculation dependency')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Add work item' }));
    expect(screen.getByRole('button', { name: 'Demo mode' })).toBeDisabled();
    expect(mockedCreateWorkItem).not.toHaveBeenCalled();
  });

  it('saves time with project, sprint, and work-item context', async () => {
    const item = {
      id: 'work-2', workKey: 'COM-2', projectId: 'project-2', sprintId: 'sprint-2', assigneeUserId: 'user-2',
      title: 'Review checkout metrics', description: 'Review conversion and payment metrics.', workType: 'TASK',
      priority: 'MEDIUM', status: 'IN_PROGRESS', createdAt: new Date().toISOString(),
    };
    mockedGetWorkItems.mockResolvedValue([item]);
    mockedCreateTimeEntry.mockResolvedValue({
      id: 'time-2', userId: 'user-2', workItemId: 'work-2', projectId: 'project-2', sprintId: 'sprint-2',
      entryDate: new Date().toISOString().slice(0, 10), minutes: 45, description: 'Reviewed dashboard metrics.', source: 'QUICK_HOURS', createdAt: new Date().toISOString(),
    });
    renderWorkspace(<SprintWorkspaceView demoMode={false} projectId="project-2" sprintId="sprint-2" projectName="Commerce" sprintName="Checkout 12" onBack={vi.fn()} />);

    const row = await screen.findByText('Review checkout metrics');
    fireEvent.click(within(row.closest('.work-item') as HTMLElement).getByRole('button', { name: 'Log time' }));
    fireEvent.change(screen.getByLabelText('Minutes'), { target: { value: '45' } });
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Reviewed dashboard metrics.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save time' }));

    await waitFor(() => expect(mockedCreateTimeEntry).toHaveBeenCalledWith(expect.objectContaining({
      workItemId: 'work-2', minutes: 45, source: 'QUICK_HOURS',
    })));
    expect(await screen.findByRole('status')).toHaveTextContent('Time saved to your central timesheet.');
  });

  it('can stop the same persisted timer from the sprint workspace', async () => {
    const item = {
      id: 'shared-work', workKey: 'COM-3', projectId: 'project-3', sprintId: 'sprint-3', assigneeUserId: 'user-3',
      title: 'Trace checkout latency', description: 'Inspect the slow payment path.', workType: 'TASK',
      priority: 'HIGH', status: 'IN_PROGRESS', createdAt: new Date().toISOString(),
    };
    mockedGetWorkItems.mockResolvedValue([item]);
    mockedCreateTimeEntry.mockResolvedValue({
      id: 'shared-time', userId: 'user-3', workItemId: 'shared-work', projectId: 'project-3', sprintId: 'sprint-3',
      entryDate: new Date().toISOString().slice(0, 10), minutes: 1, description: 'Inspected payment path.', source: 'TIMER', createdAt: new Date().toISOString(),
    });
    localStorage.setItem('company-os:active-timer:user-3', JSON.stringify({ workItemId: 'shared-work', startedAt: Date.now() - 60000 }));
    renderWorkspace(<SprintWorkspaceView demoMode={false} projectId="project-3" sprintId="sprint-3" projectName="Commerce" sprintName="Checkout 12" userId="user-3" onBack={vi.fn()} />);

    expect(await screen.findByText('TIMER RUNNING')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Stop timer' })[0]);
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: 'Inspected payment path.' } });
    fireEvent.click(screen.getByRole('button', { name: 'Save time' }));

    await waitFor(() => expect(mockedCreateTimeEntry).toHaveBeenCalledWith(expect.objectContaining({ workItemId: 'shared-work', source: 'TIMER' })));
  });
});
