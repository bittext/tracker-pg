import { CashIoAccountRef, describeRoute, ownSuffix, planRoute } from './cash-io-route.util';

describe('cash-io-route.util', () => {
  const accounts: CashIoAccountRef[] = [
    { suffix: '3370', label: 'Individual ••••3370' },
    { suffix: '5501', label: 'Agentic ••••5501' },
  ];
  const bank = 'Robinhood Coastal Banking ••••6310';

  it('recognizes own accounts by label or last digits', () => {
    expect(ownSuffix('Individual ••••3370', accounts)).toBe('3370');
    expect(ownSuffix('individual ••••3370', accounts)).toBe('3370');
    expect(ownSuffix('3370', accounts)).toBe('3370');
    expect(ownSuffix('••••1234', accounts)).toBe('1234');
  });

  it('treats named outside accounts as outside', () => {
    expect(ownSuffix(bank, accounts)).toBeNull();
    expect(ownSuffix('Chase checking', accounts)).toBeNull();
    expect(ownSuffix('', accounts)).toBeNull();
  });

  it('plans an output when money leaves your account for an outside one', () => {
    expect(planRoute('Individual ••••3370', bank, accounts)).toEqual({
      ok: true,
      moves: [{ accountSuffix: '3370', direction: 'OUT', counterparty: bank }],
    });
  });

  it('plans an input when money arrives from an outside account', () => {
    expect(planRoute('Chase', '3370', accounts)).toEqual({
      ok: true,
      moves: [{ accountSuffix: '3370', direction: 'IN', counterparty: 'Chase' }],
    });
  });

  it('plans an output and an input when both sides are yours', () => {
    expect(planRoute('3370', 'Agentic ••••5501', accounts)).toEqual({
      ok: true,
      moves: [
        { accountSuffix: '3370', direction: 'OUT', counterparty: 'Agentic ••••5501' },
        { accountSuffix: '5501', direction: 'IN', counterparty: 'Individual ••••3370' },
      ],
    });
  });

  it('rejects incomplete or impossible routes', () => {
    expect(planRoute('', bank, accounts).ok).toBeFalse();
    expect(planRoute('Chase', bank, accounts).ok).toBeFalse();
    expect(planRoute('3370', 'Individual ••••3370', accounts).ok).toBeFalse();
  });

  it('describes what will be recorded', () => {
    expect(describeRoute('3370', bank, accounts)).toBe('Output from Individual ••••3370');
    expect(describeRoute('Chase', '3370', accounts)).toBe('Input to Individual ••••3370');
    expect(describeRoute('', '', accounts)).toBe('');
  });
});
