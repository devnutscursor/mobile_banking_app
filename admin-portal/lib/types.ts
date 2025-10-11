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
  assignedToUserId: string;
  issueDate: Timestamp | Date;
  expiryDate: Timestamp | Date;
  isActive: boolean;
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
  createdAt: Timestamp | Date;
  updatedAt: Timestamp | Date;
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



