// Describes a group member's own net balance, from a third-person view
// ("gets back"/"owes") -- distinct from GroupListItem's balanceLine, which
// speaks from the current viewer's own perspective ("you owe"/"you are owed").
export function memberBalanceLabel(
  netBalance: number,
  currency: string
): { label: string; className: string } {
  if (Math.abs(netBalance) < 0.01) {
    return { label: 'settled up', className: 'text-subtle' };
  }
  return netBalance > 0
    ? { label: `gets back ${netBalance.toFixed(2)} ${currency}`, className: 'text-green-500' }
    : { label: `owes ${Math.abs(netBalance).toFixed(2)} ${currency}`, className: 'text-red-400' };
}
