'use client';

import { useEffect, useMemo, useState } from 'react';
import { collection, getDocs, query, orderBy, limit, where, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Transaction, User, Customer, Operator, OperatorAction } from '@/lib/types';
import { Card, Select, Space, Table, Tag, Typography, Input, DatePicker, InputNumber, Row, Col, Skeleton } from 'antd';
import { CheckCircleTwoTone, CloseCircleTwoTone, ClockCircleTwoTone, SearchOutlined, FilterOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import { colors } from '@/lib/theme';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

export default function TransactionsTab() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [users, setUsers] = useState<{ [key: string]: User }>({});
  const [customers, setCustomers] = useState<{ [key: string]: Customer }>({});
  const [operators, setOperators] = useState<{ [key: string]: Operator }>({});
  const [actions, setActions] = useState<{ [key: string]: OperatorAction }>({});
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [limitCount, setLimitCount] = useState(50);
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
  }, [statusFilter, limitCount]);

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

      // Load transactions (avoid composite index by not combining where+orderBy)
      let q = query(collection(db, 'transactions'), limit(limitCount));
      if (statusFilter !== 'all') {
        q = query(collection(db, 'transactions'), where('status', '==', statusFilter), limit(limitCount));
      }

      const transactionsSnapshot = await getDocs(q);
      const transactionsData = transactionsSnapshot.docs
        .map(doc => ({ ...doc.data(), id: doc.id } as Transaction))
        .sort((a, b) => {
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
      // Search filter (customer name or phone)
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
  }, [transactions, searchTerm, userFilter, operatorFilter, actionFilter, dateRange, minAmount, maxAmount, customers, users]);

  const uniqueUsers = Array.from(new Set(transactions.map(t => t.userId).filter(Boolean)));
  const uniqueOperators = Array.from(new Set(transactions.map(t => t.operatorId).filter(Boolean)));
  const uniqueActions = Array.from(new Set(transactions.map(t => t.actionId).filter(Boolean)));

  const columns = useMemo(() => [
    {
      title: 'Date',
      dataIndex: 'createdAt',
      key: 'date',
      render: (d: any) => d ? format(d instanceof Timestamp ? d.toDate() : new Date(d), 'PPp') : 'N/A',
    },
    {
      title: 'User',
      dataIndex: 'userId',
      key: 'user',
      render: (id: string) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong style={{ color: colors.beige[500] }}>{users[id]?.name || 'Unknown'}</Typography.Text>
          <Typography.Text type="secondary">{users[id]?.role || 'N/A'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Customer',
      dataIndex: 'customerId',
      key: 'customer',
      render: (id: string) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong style={{ color: colors.beige[500] }}>{customers[id]?.fullName || 'Unknown'}</Typography.Text>
          <Typography.Text type="secondary">{customers[id]?.phoneNumber || 'N/A'}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Operator',
      dataIndex: 'operatorId',
      key: 'operator',
      render: (id: string) => operators[id]?.name || 'Unknown',
    },
    {
      title: 'Action',
      dataIndex: 'actionId',
      key: 'action',
      render: (id: string) => actions[id]?.name || 'Unknown',
    },
    {
      title: 'Amount',
      dataIndex: 'amount',
      key: 'amount',
      align: 'right' as const,
      render: (v: number) => <Typography.Text strong>${v.toFixed(2)}</Typography.Text>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (s: string) => {
        if (s === 'successful') return <Tag icon={<CheckCircleTwoTone twoToneColor="#52c41a" />} color="success">Successful</Tag>;
        if (s === 'failed') return <Tag icon={<CloseCircleTwoTone twoToneColor="#ff4d4f" />} color="error">Failed</Tag>;
        if (s === 'processing') return <Tag color="blue">Processing</Tag>;
        return <Tag icon={<ClockCircleTwoTone />} color="warning">Pending</Tag>;
      },
    },
  ], [users, customers, operators, actions]);

  if (loading) {
    return (
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Transactions</Typography.Title>}
      >
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Transactions</Typography.Title>}
      >
        {/* Filters */}
        <Space direction="vertical" size="middle" style={{ width: '100%', marginBottom: 16 }}>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} md={8} lg={6}>
              <Input
                placeholder="Search customer or user..."
                prefix={<SearchOutlined />}
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                allowClear
              />
            </Col>
            <Col xs={24} sm={12} md={8} lg={4}>
              <Select
                value={statusFilter}
                onChange={(v) => setStatusFilter(v)}
                style={{ width: '100%' }}
                options={[
                  { value: 'all', label: 'All Status' },
                  { value: 'successful', label: 'Successful' },
                  { value: 'failed', label: 'Failed' },
                  { value: 'pending', label: 'Pending' },
                  { value: 'processing', label: 'Processing' },
                ]}
                suffixIcon={<FilterOutlined />}
              />
            </Col>
            <Col xs={24} sm={12} md={8} lg={5}>
              <Select
                value={userFilter}
                onChange={(v) => setUserFilter(v)}
                style={{ width: '100%' }}
                placeholder="Filter by user"
                options={[
                  { value: 'all', label: 'All Users' },
                  ...uniqueUsers.map(uid => ({ 
                    value: uid, 
                    label: users[uid]?.name || 'Unknown' 
                  }))
                ]}
                suffixIcon={<FilterOutlined />}
              />
            </Col>
            <Col xs={24} sm={12} md={8} lg={5}>
              <Select
                value={operatorFilter}
                onChange={(v) => setOperatorFilter(v)}
                style={{ width: '100%' }}
                placeholder="Filter by operator"
                options={[
                  { value: 'all', label: 'All Operators' },
                  ...uniqueOperators.map(id => ({ 
                    value: id, 
                    label: operators[id]?.name || 'Unknown' 
                  }))
                ]}
                suffixIcon={<FilterOutlined />}
              />
            </Col>
            <Col xs={24} sm={12} md={8} lg={4}>
              <Select
                value={limitCount}
                onChange={(v) => setLimitCount(v as number)}
                style={{ width: '100%' }}
                options={[25,50,100,200].map(n => ({ value: n, label: `${n} items` }))}
              />
            </Col>
          </Row>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} md={12} lg={8}>
              <RangePicker
                style={{ width: '100%' }}
                value={dateRange}
                onChange={(dates) => setDateRange(dates as any)}
                format="YYYY-MM-DD"
              />
            </Col>
            <Col xs={12} sm={6} md={6} lg={4}>
              <InputNumber
                placeholder="Min Amount"
                value={minAmount}
                onChange={(v) => setMinAmount(v)}
                style={{ width: '100%' }}
                prefix="$"
                min={0}
              />
            </Col>
            <Col xs={12} sm={6} md={6} lg={4}>
              <InputNumber
                placeholder="Max Amount"
                value={maxAmount}
                onChange={(v) => setMaxAmount(v)}
                style={{ width: '100%' }}
                prefix="$"
                min={0}
              />
            </Col>
            <Col xs={24} sm={12} md={12} lg={5}>
              <Select
                value={actionFilter}
                onChange={(v) => setActionFilter(v)}
                style={{ width: '100%' }}
                placeholder="Filter by action"
                options={[
                  { value: 'all', label: 'All Actions' },
                  ...uniqueActions.map(id => ({ 
                    value: id, 
                    label: actions[id]?.name || 'Unknown' 
                  }))
                ]}
                suffixIcon={<FilterOutlined />}
              />
            </Col>
          </Row>
        </Space>

        <Table 
          rowKey="id" 
          dataSource={filteredTransactions} 
          columns={columns} 
          pagination={{ pageSize: 25 }} 
          scroll={{ x: 1200 }}
        />
        {filteredTransactions.length === 0 && (
          <Typography.Text type="secondary">
            {searchTerm || statusFilter !== 'all' || userFilter !== 'all' || operatorFilter !== 'all' || actionFilter !== 'all' || dateRange || minAmount || maxAmount
              ? 'No transactions match your filters'
              : 'No transactions found'}
          </Typography.Text>
        )}
      </Card>
    </Space>
  );
}

