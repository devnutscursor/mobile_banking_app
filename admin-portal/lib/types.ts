import { Timestamp } from 'firebase/firestore';

export interface User {
  uid: string;
  email: string;
  name: string;
  phone: string | null;
  role: 'dealer' | 'agent' | 'admin';
  dealerId: string | null;
  active: boolean;
  disabled?: boolean;
  createdAt: Timestamp | Date;
  updatedAt: Timestamp | Date;
  virtualCredit?: number;
  totalCreditUsed?: number;
  totalCreditEarned?: number;
  creditUpdatedAt?: Timestamp | Date;
  lastUpdated?: Timestamp | Date;
}

export interface License {
  licenseKey: string;
  assignedToUserId: string | string[] | null; // Can be single user ID or array of user IDs for multi-user licenses
  issueDate: Timestamp | Date;
  expiryDate: Timestamp | Date;
  isActive: boolean;
  maxAgentCount?: number; // Maximum number of agents allowed for this license
  licenseType?: 'monthly' | 'annual'; // License type: monthly or annual
}

export interface Transaction {
  id?: string;
  userId: string;
  customerId: string;
  operatorId: string;
  actionId: string;
  amount: number;
  status: 'pending' | 'processing' | 'successful' | 'failed';
  createdAt: Timestamp | Date;
  updatedAt: Timestamp | Date;
  ussdCode?: string;
  notes?: string;
}

export interface Customer {
  id?: string;
  fullName: string;
  phoneNumber: string;
  address?: string;
  dateOfBirth?: Timestamp | Date;
  addedBy: string;
  dealerId?: string;
  createdAt: Timestamp | Date;
  updatedAt: Timestamp | Date;
  lastSyncAt?: number;
}

export interface Operator {
  id?: string;
  name: string;
  code: string;
  color: string;
  type: string;
  userId: string;
  enabled?: boolean;
  createdAt: Timestamp | Date;
  updatedAt: Timestamp | Date;
}

export interface OperatorAction {
  id?: string;
  operatorId: string;
  name: string;
  type: 'deposit' | 'withdrawal';
  ussdTemplate: string;
  userId: string;
  isActive?: boolean;
  actionCode?: string;
  disableUssd?: boolean; // If true, USSD dialer will not be launched (for verification-only actions)
  createdAt: Timestamp | Date;
  updatedAt: Timestamp | Date;
}

export interface Commission {
  id?: string;
  transactionId: string;
  transactionType: 'deposit' | 'withdrawal';
  transactionAmount: number;
  userId: string;
  userName: string;
  userRole: 'agent' | 'dealer';
  operatorId: string;
  operatorName: string;
  commissionRate: number;
  taxRate: number;
  commissionAmount: number;
  taxAmount: number;
  totalCommission: number;
  year: number;
  month: number;
  day: number;
  commissionDate: Timestamp | Date;
  createdAt?: Timestamp | Date;
  updatedAt?: Timestamp | Date;
}

export interface DashboardStats {
  totalUsers: number;
  totalDealers: number;
  totalAgents: number;
  totalTransactions: number;
  totalRevenue: number;
  weeklyTransactions: number;
  activeUsers: number;
  activeLicenses: number;
}



