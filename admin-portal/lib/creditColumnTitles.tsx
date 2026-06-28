'use client';

import { Space, Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';

const LABELS = {
  virtual: 'Virtual Credit',
  operator: 'Operator Balance',
  total: 'Total Credit',
} as const;

const HELP = {
  virtual:
    'Unallocated credit from admin. Allocate to operators on the phone app via Balance Adjustment.',
  operator:
    'Per-network credit used for sales. Changes with transactions and operator balance adjustments.',
  total:
    'Virtual Credit + Operator Balance (matches the mobile dashboard). Sales reduce operator balance only, not virtual credit.',
} as const;

export const CREDIT_BALANCE_HELP =
  'Virtual Credit is the unallocated pool. Operator Balance is per-network credit used for sales. Total matches the mobile app dashboard.';

export function creditColumnTitle(key: keyof typeof LABELS) {
  return (
    <Tooltip title={HELP[key]}>
      <Space size={4}>
        <span>{LABELS[key]}</span>
        <QuestionCircleOutlined style={{ fontSize: 12, opacity: 0.65 }} />
      </Space>
    </Tooltip>
  );
}
