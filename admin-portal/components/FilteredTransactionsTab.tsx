'use client';

import { useEffect, useMemo, useState } from 'react';
import { collection, getDocs, query, orderBy, limit, where, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Transaction, User, Customer, Operator, OperatorAction } from '@/lib/types';
import { Card, Select, Space, Table, Tag, Typography, Input, DatePicker, InputNumber, Row, Col, Button } from 'antd';
import { CheckCircleTwoTone, CloseCircleTwoTone, ClockCircleTwoTone, SearchOutlined, FilterOutlined, DownloadOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import { colors } from '@/lib/theme';
import dayjs from 'dayjs';
import { exportTransactionsToExcel } from '@/lib/exportUtils';
import { useAuth } from '@/lib/authContext';
import { formatCurrencyWithSymbol } from '@/lib/formatUtils';

const { RangePicker } = DatePicker;

interface FilteredTransactionsTabProps {
  // If provided, only show transactions for these user IDs (for dealer with agents)
  allowedUserIds?: string[];
  // If true, show export button
  showExport?: boolean;
}

export default function FilteredTransactionsTab({ allowedUserIds, showExport = true }: FilteredTransactionsTabProps) {
  const { user: currentUser } = useAuth();
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [users, setUsers] = useState<{ [key: string]: User }>({});
  const [customers, setCustomers] = useState<{ [key: string]: Customer }>({});
  const [operators, setOperators] = useState<{ [key: string]: Operator }>({});
  const [actions, setActions] = useState<{ [key: string]: OperatorAction }>({});
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [limitCount, setLimitCount] = useState(1000); // Higher limit for export
  const [searchTerm, setSearchTerm] = useState('');
  const [userFilter, setUserFilter] = useState<string>('all');
  const [operatorFilter, setOperatorFilter] = useState<string>('all');
  const [actionFilter, setActionFilter] = useState<string>('all');
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(null);
  const [minAmount, setMinAmount] = useState<number | null>(null);
  const [maxAmount, setMaxAmount] = useState<number | null>(null);

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [statusFilter, limitCount, allowedUserIds]);

  const loadData = async () => {
    try {
      setLoading(true);
      
      // Load users
      const usersSnapshot = await getDocs(collection(db, 'users'));
      const usersMap: { [key: string]: User } = {};
      usersSnapshot.docs.forEach(doc => {
        usersMap[doc.id] = { ...doc.data(), uid: doc.id } as User;
      });
      setUsers(usersMap);

      // Load customers
      const customersSnapshot = await getDocs(collection(db, 'customers'));
      const customersMap: { [key: string]: Customer } = {};
      customersSnapshot.docs.forEach(doc => {
        customersMap[doc.id] = { ...doc.data(), id: doc.id } as Customer;
      });
      setCustomers(customersMap);

      // Load operators
      const operatorsSnapshot = await getDocs(collection(db, 'operators'));
      const operatorsMap: { [key: string]: Operator } = {};
      operatorsSnapshot.docs.forEach(doc => {
        operatorsMap[doc.id] = { ...doc.data(), id: doc.id } as Operator;
      });
      setOperators(operatorsMap);

      // Load operator actions
      const actionsSnapshot = await getDocs(collection(db, 'operator_actions'));
      const actionsMap: { [key: string]: OperatorAction } = {};
      actionsSnapshot.docs.forEach(doc => {
        actionsMap[doc.id] = { ...doc.data(), id: doc.id } as OperatorAction;
      });
      setActions(actionsMap);

      // Load transactions - filter by allowedUserIds if provided
      let transactionsData: Transaction[] = [];
      
      if (allowedUserIds && allowedUserIds.length > 0) {
        // Firestore 'in' query supports max 10 items, so we need to batch if more
        if (allowedUserIds.length <= 10) {
          let q = query(collection(db, 'transactions'), where('userId', 'in', allowedUserIds), limit(limitCount));
          if (statusFilter !== 'all') {
            q = query(collection(db, 'transactions'), where('userId', 'in', allowedUserIds), where('status', '==', statusFilter), limit(limitCount));
          }
          const snapshot = await getDocs(q);
          transactionsData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
        } else {
          // Batch the queries for more than 10 users
          const batches: string[][] = [];
          for (let i = 0; i < allowedUserIds.length; i += 10) {
            batches.push(allowedUserIds.slice(i, i + 10));
          }
          
          for (const batch of batches) {
            let q = query(collection(db, 'transactions'), where('userId', 'in', batch), limit(limitCount));
            if (statusFilter !== 'all') {
              q = query(collection(db, 'transactions'), where('userId', 'in', batch), where('status', '==', statusFilter), limit(limitCount));
            }
            const snapshot = await getDocs(q);
            const batchData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
            transactionsData = [...transactionsData, ...batchData];
          }
        }
      } else if (currentUser && currentUser.role === 'agent') {
        // Agent - only their own transactions
        let q = query(collection(db, 'transactions'), where('userId', '==', currentUser.uid), limit(limitCount));
        if (statusFilter !== 'all') {
          q = query(collection(db, 'transactions'), where('userId', '==', currentUser.uid), where('status', '==', statusFilter), limit(limitCount));
        }
        const snapshot = await getDocs(q);
        transactionsData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
      } else {
        // Load all transactions (admin case or no currentUser)
        let q = query(collection(db, 'transactions'), limit(limitCount));
        if (statusFilter !== 'all') {
          q = query(collection(db, 'transactions'), where('status', '==', statusFilter), limit(limitCount));
        }
        const snapshot = await getDocs(q);
        transactionsData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
      }

      // Sort by date descending
      transactionsData.sort((a, b) => {
        const at = a.createdAt instanceof Timestamp ? a.createdAt.toMillis() : new Date(a.createdAt as any).getTime();
        const bt = b.createdAt instanceof Timestamp ? b.createdAt.toMillis() : new Date(b.createdAt as any).getTime();
        return bt - at;
      });
      
      setTransactions(transactionsData);
    } catch (error) {
      console.error('Error loading data:', error);
    } finally {
      setLoading(false);
    }
  };

  const filteredTransactions = useMemo(() => {
    return transactions.filter(txn => {
      // Search filter
      if (searchTerm) {
        const customer = customers[txn.customerId];
        const user = users[txn.userId];
        const matchesCustomer = customer?.fullName?.toLowerCase().includes(searchTerm.toLowerCase()) ||
                               customer?.phoneNumber?.toLowerCase().includes(searchTerm.toLowerCase());
        const matchesUser = user?.name?.toLowerCase().includes(searchTerm.toLowerCase());
        if (!matchesCustomer && !matchesUser) return false;
      }

      // User filter
      if (userFilter !== 'all' && txn.userId !== userFilter) return false;

      // Operator filter
      if (operatorFilter !== 'all' && txn.operatorId !== operatorFilter) return false;

      // Action filter
      if (actionFilter !== 'all' && txn.actionId !== actionFilter) return false;

      // Date range filter
      if (dateRange && dateRange[0] && dateRange[1]) {
        const txnDate = txn.createdAt instanceof Timestamp ? txn.createdAt.toDate() : new Date(txn.createdAt as any);
        if (txnDate < dateRange[0].toDate() || txnDate > dateRange[1].toDate()) return false;
      }

      // Amount filter
      if (minAmount !== null && txn.amount < minAmount) return false;
      if (maxAmount !== null && txn.amount > maxAmount) return false;

      return true;
    });
  }, [transactions, searchTerm, userFilter, operatorFilter, actionFilter, dateRange, minAmount, maxAmount, customers, users, currentUser]);

  const handleExport = () => {
    const filename = `transactions_${format(new Date(), 'yyyy-MM-dd_HH-mm-ss')}.xlsx`;
    exportTransactionsToExcel(filteredTransactions, users, customers, operators, actions, filename);
  };

  const uniqueUsers = useMemo(() => {
    const userIds = allowedUserIds || (currentUser?.role === 'agent' ? [currentUser.uid] : []);
    return Array.from(new Set(
      transactions
        .map(t => t.userId)
        .filter(id => !allowedUserIds || allowedUserIds.includes(id))
        .filter(Boolean)
    ));
  }, [transactions, allowedUserIds, currentUser]);

  const uniqueOperators = Array.from(new Set(transactions.map(t => t.operatorId).filter(Boolean)));
  const uniqueActions = Array.from(new Set(transactions.map(t => t.actionId).filter(Boolean)));

  const columns = useMemo(() => [
    {
      title: 'Date',
      dataIndex: 'createdAt',
      key: 'date',
      render: (d: any) => d ? format(d instanceof Timestamp ? d.toDate() : new Date(d), 'PPp') : 'N/A',
    },
    ...(allowedUserIds && allowedUserIds.length > 1 ? [{
      title: 'Agent/Dealer',
      dataIndex: 'userId',
      key: 'user',
      render: (id: string) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong style={{ color: colors.beige[500] }}>{users[id]?.name || 'Unknown'}</Typography.Text>
          <Typography.Text type="secondary">{users[id]?.role || 'N/A'}</Typography.Text>
        </Space>
      ),
    }] : []),
    {
      title: 'Customer',
      dataIndex: 'customerId',
      key: 'customer',
      render: (id: string) => {
        const customer = customers[id];
        return customer ? (
          <Space direction="vertical" size={0}>
            <Typography.Text strong style={{ color: colors.beige[500] }}>{customer.fullName}</Typography.Text>
            <Typography.Text type="secondary">{customer.phoneNumber}</Typography.Text>
          </Space>
        ) : 'Unknown';
      },
    },
    {
      title: 'Operator',
      dataIndex: 'operatorId',
      key: 'operator',
      render: (id: string) => operators[id]?.name || 'N/A',
    },
    {
      title: 'Action',
      dataIndex: 'actionId',
      key: 'action',
      render: (id: string) => actions[id]?.name || 'N/A',
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      render: (amt: number) => (
        <Typography.Text strong style={{ color: colors.beige[500] }}>
          {formatCurrencyWithSymbol(amt)}
        </Typography.Text>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => {
        if (status === 'successful') {
          return <Tag icon={<CheckCircleTwoTone />} color="success">{status}</Tag>;
        } else if (status === 'failed') {
          return <Tag icon={<CloseCircleTwoTone />} color="error">{status}</Tag>;
        } else {
          return <Tag icon={<ClockCircleTwoTone />} color="processing">{status}</Tag>;
        }
      },
    },
  ], [users, customers, operators, actions, allowedUserIds]);

  return (
    <Card
      style={{
        background: colors.midnight_green[400],
        border: `1px solid ${colors.air_force_blue[300]}40`,
      }}
      extra={showExport ? (
        <Button
          type="primary"
          icon={<DownloadOutlined />}
          onClick={handleExport}
          style={{
            background: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
            border: 'none',
          }}
        >
          Export to Excel
        </Button>
      ) : null}
    >
      {/* Filters */}
      <Space direction="vertical" size="middle" style={{ width: '100%', marginBottom: 16 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Input
              placeholder="Search customer, phone, agent..."
              prefix={<SearchOutlined />}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              allowClear
            />
          </Col>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Select
              style={{ width: '100%' }}
              placeholder="Status"
              value={statusFilter}
              onChange={setStatusFilter}
            >
              <Select.Option value="all">All Status</Select.Option>
              <Select.Option value="successful">Successful</Select.Option>
              <Select.Option value="failed">Failed</Select.Option>
              <Select.Option value="pending">Pending</Select.Option>
              <Select.Option value="processing">Processing</Select.Option>
            </Select>
          </Col>
          {uniqueUsers.length > 1 && (
            <Col xs={24} sm={12} md={8} lg={6}>
              <Select
                style={{ width: '100%' }}
                placeholder="Agent/Dealer"
                value={userFilter}
                onChange={setUserFilter}
              >
                <Select.Option value="all">All Agents/Dealers</Select.Option>
                {uniqueUsers.map(uid => (
                  <Select.Option key={uid} value={uid}>
                    {users[uid]?.name || uid}
                  </Select.Option>
                ))}
              </Select>
            </Col>
          )}
          <Col xs={24} sm={12} md={8} lg={6}>
            <RangePicker
              style={{ width: '100%' }}
              value={dateRange}
              onChange={setDateRange}
            />
          </Col>
        </Row>
      </Space>

      {/* Table */}
      <Table
        columns={columns}
        dataSource={filteredTransactions}
        loading={loading}
        rowKey="id"
        pagination={{
          pageSize: 50,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} transactions`,
        }}
        scroll={{ x: 'max-content' }}
      />
    </Card>
  );
}

