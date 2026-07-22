'use client';

import { Space, Tooltip } from 'antd';
import { QuestionCircleOutlined } from '@ant-design/icons';

const LABELS = {
  operator: 'Operator Balance',
  total: 'Total Credit',
} as const;

const HELP = {
  operator:
    'Per-network credit used for sales. Set by admin per operator, or changed on the phone via Purchase Credit / transactions.',
  total:
    'Sum of all operator balances for this user. Sales reduce operator balance only.',
} as const;

export const CREDIT_BALANCE_HELP =
  'Operator Balance is per-network credit used for sales. Admin assigns it per operator. Cash Balance is managed on the phone.';

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
