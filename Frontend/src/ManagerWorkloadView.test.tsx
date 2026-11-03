import { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ManagerWorkloadView } from './ManagerWorkloadView';
import { getManagerWorkload, getPendingTimesheets } from './api';

vi.mock('./api', () => ({ getManagerWorkload: vi.fn(), getPendingTimeEntries: vi.fn().mockResolvedValue([]), getPendingTimesheets: vi.fn().mockResolvedValue([]) }));

const mockedGetManagerWorkload = vi.mocked(getManagerWorkload);

function renderManager(node: ReactNode) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={queryClient}>{node}</QueryClientProvider>);
}

describe('ManagerWorkloadView', () => {
  beforeEach(() => mockedGetManagerWorkload.mockReset());

  it('filters the team to members who need attention', () => {
    renderManager(<ManagerWorkloadView demoMode />);

    fireEvent.click(screen.getByRole('button', { name: /Needs attention/ }));

    expect(screen.getByText('Ananya Nair')).toBeInTheDocument();
    expect(screen.queryByText('Vikram Kumar')).not.toBeInTheDocument();
    expect(screen.queryByText('Sara Patel')).not.toBeInTheDocument();
  });

  it('sorts members by name for predictable manager review', () => {
    renderManager(<ManagerWorkloadView demoMode />);

    fireEvent.change(screen.getByLabelText('Sort by'), { target: { value: 'NAME_ASC' } });

    const names = screen.getAllByText(/Nair|Kumar|Patel/).map((element) => element.textContent);
    expect(names).toEqual(['Ananya Nair', 'Sara Patel', 'Vikram Kumar']);
  });
});
