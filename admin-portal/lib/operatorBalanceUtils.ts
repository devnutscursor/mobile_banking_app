import { collection, getDocs } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { User } from '@/lib/types';

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

/** Attach operator balance sum and total credit (virtual + operators) for table display. */
export function attachBalanceTotals(
  users: User[],
  balanceSums: Map<string, number>
): UserWithBalanceTotals[] {
  return users.map((user) => {
    const virtualCredit = Number(user.virtualCredit) || 0;
    const operatorBalance = balanceSums.get(user.uid) || 0;
    return {
      ...user,
      operatorBalance,
      totalCredit: virtualCredit + operatorBalance,
    };
  });
}
