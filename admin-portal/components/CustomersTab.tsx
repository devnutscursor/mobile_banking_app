'use client';

import { useState, useEffect, useMemo } from 'react';
import { collection, getDocs, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Customer, User } from '@/lib/types';
import { Card, Input, Space, Table, Typography, Select, DatePicker, Row, Col, Skeleton } from 'antd';
import { SearchOutlined, FilterOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import { colors } from '@/lib/theme';
import dayjs from 'dayjs';

const { RangePicker } = DatePicker;

export default function CustomersTab() {
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [users, setUsers] = useState<{ [key: string]: User }>({});
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [userFilter, setUserFilter] = useState<string>('all');
  const [dateRange, setDateRange] = useState<[dayjs.Dayjs | null, dayjs.Dayjs | null] | null>(null);

  const columns = useMemo(() => [
    {
      title: 'Name',
      dataIndex: 'fullName',
      key: 'name',
      render: (name: string) => <Typography.Text strong style={{ color: colors.beige[500] }}>{name || 'Unknown'}</Typography.Text>,
    },
    {
      title: 'Phone',
      dataIndex: 'phoneNumber',
      key: 'phone',
    },
    {
      title: 'Address',
      dataIndex: 'address',
      key: 'address',
    },
    {
      title: 'DOB',
      dataIndex: 'dateOfBirth',
      key: 'dob',
      render: (d: any) => d ? format(d instanceof Timestamp ? d.toDate() : new Date(d), 'PP') : 'N/A',
    },
    {
      title: 'Added By',
      dataIndex: 'addedBy',
      key: 'addedBy',
      render: (_: any, record: Customer) => {
        const userId: string | undefined = (record as any).userId || (record as any).addedBy || (record as any).createdBy;
        const u = userId ? users[userId] : undefined;
        return u?.name || u?.email || userId || 'Unknown';
      },
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d: any) => d ? format(d instanceof Timestamp ? d.toDate() : new Date(d), 'PP') : 'N/A',
    },
  ], [users]);

  useEffect(() => {
    loadData();
  }, []);

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
      const customersData = customersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
      setCustomers(customersData);
    } catch (error) {
      console.error('Error loading data:', error);
    } finally {
      setLoading(false);
    }
  };

  const uniqueUsers = Array.from(new Set(customers.map((c: any) => (c as any).userId || (c as any).addedBy || (c as any).createdBy).filter(Boolean)));

  const filteredCustomers = customers
    // Show only active customers (treat missing isActive as true for safety)
    .filter((customer: any) => customer.isActive !== false)
    .filter(customer => {
      // Search filter
      const searchLower = searchTerm.toLowerCase();
      const matchesSearch = customer.fullName?.toLowerCase().includes(searchLower) ||
        customer.phoneNumber?.toLowerCase().includes(searchLower) ||
        customer.address?.toLowerCase().includes(searchLower) ||
        (customer as any).nationalIdNumber?.toLowerCase().includes(searchLower);
      
      if (searchTerm && !matchesSearch) return false;

      // User filter
      if (userFilter !== 'all') {
        const custUserId: string | undefined = (customer as any).userId || (customer as any).addedBy || (customer as any).createdBy;
        if (custUserId !== userFilter) return false;
      }

      // Date range filter
      if (dateRange && dateRange[0] && dateRange[1]) {
        const createdDate = customer.createdAt instanceof Timestamp ? customer.createdAt.toDate() : new Date(customer.createdAt as any);
        if (createdDate < dateRange[0].toDate() || createdDate > dateRange[1].toDate()) return false;
      }

      return true;
    });

  if (loading) {
    return (
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Customers</Typography.Title>}
      >
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Customers</Typography.Title>}
      >
        {/* Filters */}
        <Space direction="vertical" size="middle" style={{ width: '100%', marginBottom: 16 }}>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} md={10} lg={8}>
              <Input 
                placeholder="Search name, phone, address, ID..." 
                prefix={<SearchOutlined />}
                value={searchTerm} 
                onChange={(e) => setSearchTerm(e.target.value)} 
                allowClear
              />
            </Col>
            <Col xs={24} sm={12} md={8} lg={6}>
              <Select
                value={userFilter}
                onChange={(v) => setUserFilter(v)}
                style={{ width: '100%' }}
                placeholder="Filter by added by"
                options={[
                  { value: 'all', label: 'All Users' },
                  ...uniqueUsers.map(uid => ({ 
                    value: uid, 
                    label: users[uid]?.name || users[uid]?.email || 'Unknown' 
                  }))
                ]}
                suffixIcon={<FilterOutlined />}
              />
            </Col>
            <Col xs={24} sm={24} md={12} lg={10}>
              <RangePicker
                style={{ width: '100%' }}
                value={dateRange}
                onChange={(dates) => setDateRange(dates as any)}
                format="YYYY-MM-DD"
                placeholder={['Created from', 'Created to']}
              />
            </Col>
          </Row>
        </Space>

        <Table 
          rowKey="id" 
          dataSource={filteredCustomers} 
          columns={columns} 
          pagination={{ pageSize: 12 }} 
          scroll={{ x: 1000 }}
        />
        {filteredCustomers.length === 0 && (
          <Typography.Text type="secondary">
            {searchTerm || userFilter !== 'all' || dateRange 
              ? 'No customers match your filters' 
              : 'No customers found'}
          </Typography.Text>
        )}
      </Card>
    </Space>
  );
}


