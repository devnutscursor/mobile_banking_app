'use client';

import { useEffect, useState } from 'react';
import { InputNumber, Space, Spin, Typography, Empty } from 'antd';
import {
  loadOperatorBalancesForUser,
  OperatorBalanceRow,
} from '@/lib/operatorBalanceUtils';
import { formatCurrencyWithSymbol } from '@/lib/formatUtils';
import { colors } from '@/lib/theme';

type Props = {
  userId: string | null;
  /** Called whenever local balance edits change (parent reads on submit). */
  onChange: (balances: { operatorId: string; balance: number }[]) => void;
};

/**
 * Edit operator balances for an existing user.
 * New users have no operators yet — show a short hint instead.
 */
export default function OperatorBalancesEditor({ userId, onChange }: Props) {
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<OperatorBalanceRow[]>([]);

  useEffect(() => {
    if (!userId) {
      setRows([]);
      onChange([]);
      return;
    }

    let cancelled = false;
    (async () => {
      setLoading(true);
      try {
        const data = await loadOperatorBalancesForUser(userId);
        if (cancelled) return;
        setRows(data);
        onChange(data.map((r) => ({ operatorId: r.operatorId, balance: r.balance })));
      } catch (e) {
        console.error('Failed to load operator balances', e);
        if (!cancelled) {
          setRows([]);
          onChange([]);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps -- load once per userId
  }, [userId]);

  const updateBalance = (operatorId: string, balance: number | null) => {
    const next = rows.map((r) =>
      r.operatorId === operatorId ? { ...r, balance: Number(balance) || 0 } : r
    );
    setRows(next);
    onChange(next.map((r) => ({ operatorId: r.operatorId, balance: r.balance })));
  };

  if (!userId) {
    return (
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Create the user first, then edit them to set operator balances. Operators are
        created on the phone app under that account.
      </Typography.Text>
    );
  }

  if (loading) {
    return <Spin size="small" />;
  }

  if (rows.length === 0) {
    return (
      <Empty
        image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            No operators found for this user yet. They must create operators on the
            phone app first, then you can set balances here.
          </Typography.Text>
        }
      />
    );
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="small">
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        Set credit per network (operator). This is used for sales on the phone.
      </Typography.Text>
      {rows.map((row) => (
        <div
          key={row.operatorId}
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 12,
            padding: '6px 0',
            borderBottom: `1px solid ${colors.air_force_blue[300]}30`,
          }}
        >
          <Typography.Text style={{ flex: 1 }}>{row.operatorName}</Typography.Text>
          <InputNumber
            min={0}
            value={row.balance}
            onChange={(v) => updateBalance(row.operatorId, v)}
            style={{ width: 160 }}
            formatter={(value) =>
              value !== null && value !== undefined
                ? value.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
                : ''
            }
            parser={(value) => (value ? value.replace(/,/g, '') : '') as unknown as number}
          />
        </div>
      ))}
      <Typography.Text style={{ fontSize: 12, color: colors.ash_gray[500] }}>
        Total: {formatCurrencyWithSymbol(rows.reduce((s, r) => s + (r.balance || 0), 0))}
      </Typography.Text>
    </Space>
  );
}
