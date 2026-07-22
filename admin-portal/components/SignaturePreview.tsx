'use client';

import { useState } from 'react';
import { Image, Modal, Typography } from 'antd';
import { Transaction } from '@/lib/types';
import { formatCurrencyWithSymbol } from '@/lib/formatUtils';
import { colors } from '@/lib/theme';
import { extractAccountNumber, getTransactionComment } from '@/lib/transactionDisplayUtils';

function isViewableSignatureUrl(url: string | undefined | null): url is string {
  if (!url) return false;
  if (url.startsWith('file://')) return false; // phone-local only — not reachable from admin
  return (
    url.startsWith('https://') ||
    url.startsWith('http://') ||
    url.startsWith('data:image/')
  );
}

/** Compact thumbnail; click opens full-size preview. */
export function SignatureThumbnail({ url }: { url?: string | null }) {
  const [open, setOpen] = useState(false);

  if (!isViewableSignatureUrl(url)) {
    return <Typography.Text type="secondary">—</Typography.Text>;
  }

  return (
    <>
      <button
        type="button"
        onClick={(e) => {
          e.stopPropagation();
          setOpen(true);
        }}
        style={{
          padding: 0,
          border: `1px solid ${colors.air_force_blue[300]}60`,
          borderRadius: 4,
          background: '#fff',
          cursor: 'pointer',
          lineHeight: 0,
          overflow: 'hidden',
        }}
        title="Preview signature"
      >
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src={url}
          alt="Signature"
          width={56}
          height={32}
          style={{ objectFit: 'contain', display: 'block', background: '#fff' }}
        />
      </button>
      <Modal
        open={open}
        onCancel={(e) => {
          e?.stopPropagation?.();
          setOpen(false);
        }}
        footer={null}
        title="Customer signature"
        centered
        width={520}
        destroyOnHidden
      >
        <div
          style={{
            background: '#fff',
            borderRadius: 8,
            padding: 12,
            textAlign: 'center',
          }}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src={url}
            alt="Signature preview"
            style={{ maxWidth: '100%', maxHeight: 360, objectFit: 'contain' }}
          />
        </div>
      </Modal>
    </>
  );
}

type DetailProps = {
  transaction: Transaction | null;
  open: boolean;
  onClose: () => void;
  userName?: string;
  customerName?: string;
  operatorName?: string;
  actionName?: string;
};

/** Transaction detail modal including clickable signature preview. */
export function TransactionDetailModal({
  transaction,
  open,
  onClose,
  userName,
  customerName,
  operatorName,
  actionName,
}: DetailProps) {
  if (!transaction) return null;

  const created =
    transaction.createdAt &&
    (typeof (transaction.createdAt as { toDate?: () => Date }).toDate === 'function'
      ? (transaction.createdAt as { toDate: () => Date }).toDate()
      : new Date(transaction.createdAt as string | number | Date));

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      title="Transaction details"
      width={560}
      destroyOnHidden
    >
      <div style={{ display: 'grid', gap: 10 }}>
        <DetailRow label="User" value={userName || transaction.userId} />
        <DetailRow label="Txn #" value={(transaction.id || '').toUpperCase()} />
        <DetailRow label="Customer" value={customerName || transaction.customerId} />
        <DetailRow label="Operator" value={operatorName || transaction.operatorId} />
        <DetailRow label="Action" value={actionName || transaction.actionId} />
        <DetailRow label="Amount" value={formatCurrencyWithSymbol(transaction.amount || 0)} />
        <DetailRow label="Status" value={transaction.status} />
        <DetailRow label="Payment" value={transaction.paymentStatus || 'paid'} />
        <DetailRow label="Comment" value={getTransactionComment(transaction.userNotes, transaction.notes) || '—'} />
        <DetailRow
          label="Account #"
          value={extractAccountNumber(transaction.notes, transaction.customerPhone) || '—'}
        />
        {created && !Number.isNaN(created.getTime()) && (
          <DetailRow label="Date" value={created.toLocaleString()} />
        )}
        <div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            Signature
          </Typography.Text>
          <div style={{ marginTop: 8 }}>
            {isViewableSignatureUrl(transaction.signatureUrl) ? (
              <Image
                src={transaction.signatureUrl}
                alt="Customer signature"
                style={{
                  maxWidth: '100%',
                  maxHeight: 220,
                  objectFit: 'contain',
                  background: '#fff',
                  borderRadius: 8,
                  border: `1px solid ${colors.air_force_blue[300]}40`,
                }}
                preview={{ mask: 'Preview' }}
              />
            ) : (
              <Typography.Text type="secondary">No signature</Typography.Text>
            )}
          </div>
        </div>
      </div>
    </Modal>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', gap: 16 }}>
      <Typography.Text type="secondary">{label}</Typography.Text>
      <Typography.Text style={{ textAlign: 'right' }}>{value}</Typography.Text>
    </div>
  );
}
