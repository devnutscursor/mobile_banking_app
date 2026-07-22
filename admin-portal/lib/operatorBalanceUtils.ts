import {
  collection,
  doc,
  getDocs,
  query,
  setDoc,
  Timestamp,
  where,
} from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Operator, User } from '@/lib/types';

/** Sum of operator_balances.balance grouped by userId. */
export async function loadOperatorBalanceSumsByUserId(): Promise<Map<string, number>> {
  const snapshot = await getDocs(collection(db, 'operator_balances'));
  const sums = new Map<string, number>();

  snapshot.docs.forEach((docSnap) => {
    const data = docSnap.data();
    const userId = data.userId as string | undefined;
    if (!userId) return;

    const balance =
      typeof data.balance === 'number' ? data.balance : Number(data.balance) || 0;
    sums.set(userId, (sums.get(userId) || 0) + balance);
  });

  return sums;
}

export type UserWithBalanceTotals = User & {
  operatorBalance: number;
  totalCredit: number;
};

/** Attach operator balance sum. Total credit = operator balances only (no virtual pool). */
export function attachBalanceTotals(
  users: User[],
  balanceSums: Map<string, number>
): UserWithBalanceTotals[] {
  return users.map((user) => {
    const operatorBalance = balanceSums.get(user.uid) || 0;
    return {
      ...user,
      operatorBalance,
      totalCredit: operatorBalance,
    };
  });
}

export type OperatorBalanceRow = {
  operatorId: string;
  operatorName: string;
  balance: number;
};

/** Load operators owned by a user plus their current operator_balances. */
export async function loadOperatorBalancesForUser(
  userId: string
): Promise<OperatorBalanceRow[]> {
  const [operatorsSnap, balancesSnap] = await Promise.all([
    getDocs(query(collection(db, 'operators'), where('userId', '==', userId))),
    getDocs(query(collection(db, 'operator_balances'), where('userId', '==', userId))),
  ]);

  const balanceByOperator = new Map<string, number>();
  balancesSnap.docs.forEach((docSnap) => {
    const data = docSnap.data();
    const operatorId = data.operatorId as string | undefined;
    if (!operatorId) return;
    const balance =
      typeof data.balance === 'number' ? data.balance : Number(data.balance) || 0;
    balanceByOperator.set(operatorId, balance);
  });

  const rows: OperatorBalanceRow[] = operatorsSnap.docs.map((docSnap) => {
    const op = { ...docSnap.data(), id: docSnap.id } as Operator;
    return {
      operatorId: docSnap.id,
      operatorName: op.name || docSnap.id,
      balance: balanceByOperator.get(docSnap.id) || 0,
    };
  });

  rows.sort((a, b) => a.operatorName.localeCompare(b.operatorName));
  return rows;
}

/**
 * Upsert operator_balances docs for a user.
 * Doc id: `{userId}_{operatorId}` (matches Android OperatorBalanceHelper).
 */
export async function saveOperatorBalancesForUser(
  userId: string,
  balances: { operatorId: string; balance: number }[]
): Promise<void> {
  const now = Timestamp.now();
  await Promise.all(
    balances.map(async ({ operatorId, balance }) => {
      const id = `${userId}_${operatorId}`;
      const ref = doc(db, 'operator_balances', id);
      await setDoc(
        ref,
        {
          userId,
          operatorId,
          balance: Number(balance) || 0,
          updatedAt: now,
        },
        { merge: true }
      );
    })
  );
}
