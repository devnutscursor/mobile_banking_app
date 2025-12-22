import * as XLSX from 'xlsx';
import { Transaction, Customer, Commission, User, License, Operator, OperatorAction } from './types';
import { format, isValid } from 'date-fns';
import { Timestamp } from 'firebase/firestore';

/**
 * Export transactions to Excel
 */
export function exportTransactionsToExcel(
  transactions: Transaction[],
  users: { [key: string]: User },
  customers: { [key: string]: Customer },
  operators: { [key: string]: any },
  actions: { [key: string]: any },
  filename: string = 'transactions.xlsx'
) {
  const data = transactions.map(txn => {
    const user = users[txn.userId];
    const customer = customers[txn.customerId];
    const operator = operators[txn.operatorId];
    const action = actions[txn.actionId];
    
    const createdAt = txn.createdAt instanceof Timestamp 
      ? txn.createdAt.toDate() 
      : new Date(txn.createdAt as any);
    
    return {
      'Date': format(createdAt, 'yyyy-MM-dd HH:mm:ss'),
      'Customer Name': customer?.fullName || 'N/A',
      'Customer Phone': customer?.phoneNumber || 'N/A',
      'Operator': operator?.name || 'N/A',
      'Action': action?.name || 'N/A',
      'Amount': txn.amount.toFixed(2),
      'Status': txn.status,
      'Agent/Dealer': user?.name || 'N/A',
      'Email': user?.email || 'N/A',
      'Role': user?.role || 'N/A',
      'USSD Code': txn.ussdCode || '',
      'Notes': txn.notes || '',
    };
  });

  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Transactions');
  
  // Auto-size columns
  const maxWidth = 50;
  const cols = Object.keys(data[0] || {}).map(key => ({
    wch: Math.min(
      Math.max(
        key.length,
        ...data.map(row => String(row[key as keyof typeof data[0]] || '').length)
      ),
      maxWidth
    )
  }));
  worksheet['!cols'] = cols;
  
  XLSX.writeFile(workbook, filename);
}

/**
 * Export customers to Excel
 */
export function exportCustomersToExcel(
  customers: Customer[],
  users: { [key: string]: User },
  filename: string = 'customers.xlsx'
) {
  const data = customers.map(customer => {
    const addedByUser = users[customer.addedBy];

    // Safely normalise createdAt
    let createdAt: Date | null = null;
    if (customer.createdAt instanceof Timestamp) {
      createdAt = customer.createdAt.toDate();
    } else if (customer.createdAt) {
      const parsed = new Date(customer.createdAt as any);
      createdAt = isValid(parsed) ? parsed : null;
    }

    // Safely normalise dateOfBirth (can be missing / invalid)
    let dateOfBirth: Date | null = null;
    if (customer.dateOfBirth instanceof Timestamp) {
      dateOfBirth = customer.dateOfBirth.toDate();
    } else if (customer.dateOfBirth) {
      const parsedDob = new Date(customer.dateOfBirth as any);
      dateOfBirth = isValid(parsedDob) ? parsedDob : null;
    }
    
    return {
      'Full Name': customer.fullName,
      'Phone Number': customer.phoneNumber,
      'Address': customer.address || '',
      'Date of Birth': dateOfBirth ? format(dateOfBirth, 'yyyy-MM-dd') : '',
      'Added By': addedByUser?.name || 'N/A',
      'Added By Email': addedByUser?.email || 'N/A',
      'Added By Role': addedByUser?.role || 'N/A',
      'Date Added': createdAt ? format(createdAt, 'yyyy-MM-dd HH:mm:ss') : '',
    };
  });

  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Customers');
  
  // Auto-size columns
  const maxWidth = 50;
  const cols = Object.keys(data[0] || {}).map(key => ({
    wch: Math.min(
      Math.max(
        key.length,
        ...data.map(row => String(row[key as keyof typeof data[0]] || '').length)
      ),
      maxWidth
    )
  }));
  worksheet['!cols'] = cols;
  
  XLSX.writeFile(workbook, filename);
}

/**
 * Export commissions to Excel
 */
export function exportCommissionsToExcel(
  commissions: Commission[],
  filename: string = 'commissions.xlsx'
) {
  const data = commissions.map(comm => {
    const commissionDate = comm.commissionDate instanceof Timestamp
      ? comm.commissionDate.toDate()
      : new Date(comm.commissionDate as any);
    
    return {
      'Date': format(commissionDate, 'yyyy-MM-dd'),
      'Period': `${comm.year}-${String(comm.month).padStart(2, '0')}`,
      'Agent/Dealer Name': comm.userName,
      'Role': comm.userRole,
      'Operator': comm.operatorName,
      'Transaction Type': comm.transactionType,
      'Transaction Amount': comm.transactionAmount.toFixed(2),
      'Commission Rate (%)': comm.commissionRate.toFixed(2),
      'Tax Rate (%)': comm.taxRate.toFixed(2),
      'Commission Amount': comm.commissionAmount.toFixed(2),
      'Tax Amount': comm.taxAmount.toFixed(2),
      'Total Commission': comm.totalCommission.toFixed(2),
    };
  });

  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Commissions');
  
  // Auto-size columns
  const maxWidth = 50;
  const cols = Object.keys(data[0] || {}).map(key => ({
    wch: Math.min(
      Math.max(
        key.length,
        ...data.map(row => String(row[key as keyof typeof data[0]] || '').length)
      ),
      maxWidth
    )
  }));
  worksheet['!cols'] = cols;
  
  XLSX.writeFile(workbook, filename);
}

/**
 * Export commission summary (aggregated by period and user)
 */
export function exportCommissionSummaryToExcel(
  commissions: Commission[],
  filename: string = 'commission_summary.xlsx'
) {
  // Group by period and user
  const summaryMap: { [key: string]: {
    period: string;
    userName: string;
    userRole: string;
    transactionCount: number;
    totalCommission: number;
  } } = {};

  commissions.forEach(comm => {
    const period = `${comm.year}-${String(comm.month).padStart(2, '0')}`;
    const key = `${period}_${comm.userId}`;
    
    if (!summaryMap[key]) {
      summaryMap[key] = {
        period,
        userName: comm.userName,
        userRole: comm.userRole,
        transactionCount: 0,
        totalCommission: 0,
      };
    }
    
    summaryMap[key].transactionCount++;
    summaryMap[key].totalCommission += comm.totalCommission;
  });

  const data = Object.values(summaryMap).map(summary => ({
    'Period': summary.period,
    'Agent/Dealer Name': summary.userName,
    'Role': summary.userRole,
    'Transaction Count': summary.transactionCount,
    'Total Commission': summary.totalCommission.toFixed(2),
  }));

  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Commission Summary');
  
  // Auto-size columns
  const maxWidth = 50;
  const cols = Object.keys(data[0] || {}).map(key => ({
    wch: Math.min(
      Math.max(
        key.length,
        ...data.map(row => String(row[key as keyof typeof data[0]] || '').length)
      ),
      maxWidth
    )
  }));
  worksheet['!cols'] = cols;
  
  XLSX.writeFile(workbook, filename);
}

/**
 * Export operators to Excel
 */
export function exportOperatorsToExcel(
  operators: Operator[],
  users: { [key: string]: User },
  filename: string = 'operators.xlsx'
) {
  const data = operators.map(op => {
    const addedByUser = users[op.userId];
    const createdAt = op.createdAt instanceof Timestamp
      ? op.createdAt.toDate()
      : new Date(op.createdAt as any);

    return {
      'ID': op.id,
      'Name': op.name,
      'Code': op.code,
      'Type': op.type,
      'Color': op.color,
      'Enabled': op.enabled ? 'Yes' : 'No',
      'Added By': addedByUser?.name || 'N/A',
      'Added By Email': addedByUser?.email || 'N/A',
      'Created At': format(createdAt, 'yyyy-MM-dd HH:mm:ss'),
    };
  });

  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Operators');

  const maxWidth = 50;
  const cols = Object.keys(data[0] || {}).map(key => ({
    wch: Math.min(
      Math.max(
        key.length,
        ...data.map(row => String(row[key as keyof typeof data[0]] || '').length)
      ),
      maxWidth
    )
  }));
  worksheet['!cols'] = cols;

  XLSX.writeFile(workbook, filename);
}

/**
 * Export operator actions to Excel
 */
export function exportOperatorActionsToExcel(
  actions: OperatorAction[],
  operators: { [key: string]: Operator },
  users: { [key: string]: User },
  filename: string = 'operator_actions.xlsx'
) {
  const data = actions.map(action => {
    const operator = operators[action.operatorId];
    const addedByUser = users[action.userId];
    const createdAt = action.createdAt instanceof Timestamp
      ? action.createdAt.toDate()
      : new Date(action.createdAt as any);

    return {
      'ID': action.id,
      'Operator': operator?.name || 'N/A',
      'Action Name': action.name,
      'Type': action.type,
      'Action Code': action.actionCode || '',
      'USSD Disabled': action.disableUssd ? 'Yes' : 'No',
      'Is Active': action.isActive ? 'Yes' : 'No',
      'Added By': addedByUser?.name || 'N/A',
      'Added By Email': addedByUser?.email || 'N/A',
      'Created At': format(createdAt, 'yyyy-MM-dd HH:mm:ss'),
    };
  });

  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Operator Actions');

  const maxWidth = 50;
  const cols = Object.keys(data[0] || {}).map(key => ({
    wch: Math.min(
      Math.max(
        key.length,
        ...data.map(row => String(row[key as keyof typeof data[0]] || '').length)
      ),
      maxWidth
    )
  }));
  worksheet['!cols'] = cols;

  XLSX.writeFile(workbook, filename);
}

/**
 * Export licenses to Excel
 */
export function exportLicensesToExcel(
  licenses: License[],
  users: { [key: string]: User },
  filename: string = 'licenses.xlsx'
) {
  const data = licenses.map(license => {
    // Handle both array (new) and string (legacy) formats for assignedToUserId
    const assignedUserIds = license.assignedToUserId 
      ? (Array.isArray(license.assignedToUserId) ? license.assignedToUserId : [license.assignedToUserId])
      : [];
    
    // Get all assigned users
    const assignedUsers = assignedUserIds
      .map(userId => users[userId])
      .filter((user): user is User => user !== undefined);
    
    const assignedToNames = assignedUsers.map(u => u.name).join(', ') || 'N/A';
    const assignedToEmails = assignedUsers.map(u => u.email).join(', ') || 'N/A';
    const assignedToRoles = assignedUsers.map(u => u.role).join(', ') || 'N/A';
    
    const issueDate = license.issueDate instanceof Timestamp
      ? license.issueDate.toDate()
      : new Date(license.issueDate as any);
    const expiryDate = license.expiryDate instanceof Timestamp
      ? license.expiryDate.toDate()
      : new Date(license.expiryDate as any);

    return {
      'License Key': license.licenseKey,
      'Assigned To': assignedToNames,
      'Assigned To Email': assignedToEmails,
      'Assigned To Role': assignedToRoles,
      'Issue Date': format(issueDate, 'yyyy-MM-dd'),
      'Expiry Date': format(expiryDate, 'yyyy-MM-dd'),
      'Is Active': license.isActive ? 'Yes' : 'No',
      'Max Users': license.maxAgentCount ?? 'Unlimited',
      'License Type': license.licenseType || 'Annual',
    };
  });

  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Licenses');

  const maxWidth = 50;
  const cols = Object.keys(data[0] || {}).map(key => ({
    wch: Math.min(
      Math.max(
        key.length,
        ...data.map(row => String(row[key as keyof typeof data[0]] || '').length)
      ),
      maxWidth
    )
  }));
  worksheet['!cols'] = cols;

  XLSX.writeFile(workbook, filename);
}

/**
 * Export users to Excel
 */
export function exportUsersToExcel(
  users: User[],
  filename: string = 'users.xlsx'
) {
  const data = users.map(user => {
    const createdAt = user.createdAt instanceof Timestamp
      ? user.createdAt.toDate()
      : new Date(user.createdAt as any);
    const updatedAt = user.updatedAt instanceof Timestamp
      ? user.updatedAt.toDate()
      : new Date(user.updatedAt as any);

    return {
      'UID': user.uid,
      'Name': user.name,
      'Email': user.email,
      'Phone': user.phone || '',
      'Role': user.role,
      'Dealer ID': user.dealerId || '',
      'Active': user.active ? 'Yes' : 'No',
      'Disabled': user.disabled ? 'Yes' : 'No',
      'Virtual Credit': user.virtualCredit?.toFixed(2) || '0.00',
      'Total Credit Used': user.totalCreditUsed?.toFixed(2) || '0.00',
      'Total Credit Earned': user.totalCreditEarned?.toFixed(2) || '0.00',
      'Created At': format(createdAt, 'yyyy-MM-dd HH:mm:ss'),
      'Updated At': format(updatedAt, 'yyyy-MM-dd HH:mm:ss'),
    };
  });

  const worksheet = XLSX.utils.json_to_sheet(data);
  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, worksheet, 'Users');

  const maxWidth = 50;
  const cols = Object.keys(data[0] || {}).map(key => ({
    wch: Math.min(
      Math.max(
        key.length,
        ...data.map(row => String(row[key as keyof typeof data[0]] || '').length)
      ),
      maxWidth
    )
  }));
  worksheet['!cols'] = cols;

  XLSX.writeFile(workbook, filename);
}
