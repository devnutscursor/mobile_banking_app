'use client';

import { useState, useEffect, useMemo } from 'react';
import { collection, getDocs, query, where, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Customer, User } from '@/lib/types';
import { Card, Input, Space, Table, Typography, Select, DatePicker, Row, Col, Button } from 'antd';
import { SearchOutlined, DownloadOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import { colors } from '@/lib/theme';
import dayjs from 'dayjs';
import { exportCustomersToExcel } from '@/lib/exportUtils';
import { useAuth } from '@/lib/authContext';

const { RangePicker } = DatePicker;

interface FilteredCustomersTabProps {
  allowedUserIds?: string[];
  showExport?: boolean;
}

export default function FilteredCustomersTab({ allowedUserIds, showExport = true }: FilteredCustomersTabProps) {
  const { user: currentUser } = useAuth();
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
      render: (d: any) => {
        if (!d) return 'N/A';
        try {
          let date: Date;
          if (d instanceof Timestamp) {
            date = d.toDate();
          } else if (typeof d === 'string' || typeof d === 'number') {
            date = new Date(d);
          } else {
            return 'N/A';
          }
          
          // Check if date is valid
          if (isNaN(date.getTime())) {
            return d.toString(); // Return raw value if date is invalid
          }
          
          return format(date, 'PP');
        } catch (error) {
          console.error('[FilteredCustomersTab] Error formatting date:', error, 'Value:', d);
          return d?.toString() || 'N/A';
        }
      },
    },
    {
      title: 'Added By',
      dataIndex: 'addedBy',
      key: 'addedBy',
      render: (_: any, record: Customer) => {
        // Check multiple possible field names: userId, addedBy, createdBy
        const userId = (record as any).userId || (record as any).addedBy || (record as any).createdBy;
        
        if (!userId) {
          return 'Unknown';
        }
        
        // Look up user from users map
        const user = users[userId];
        
        if (user) {
          // Return user's name if available, otherwise email, otherwise just show the ID
          return user.name || user.email || userId;
        }
        
        // If user not found in map, just show the ID
        return userId;
      },
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d: any) => {
        if (!d) return 'N/A';
        try {
          let date: Date;
          if (d instanceof Timestamp) {
            date = d.toDate();
          } else if (typeof d === 'string' || typeof d === 'number') {
            date = new Date(d);
          } else {
            return 'N/A';
          }
          
          // Check if date is valid
          if (isNaN(date.getTime())) {
            return d.toString(); // Return raw value if date is invalid
          }
          
          return format(date, 'PP');
        } catch (error) {
          console.error('[FilteredCustomersTab] Error formatting createdAt date:', error, 'Value:', d);
          return d?.toString() || 'N/A';
        }
      },
    },
  ], [users, allowedUserIds]);

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [allowedUserIds]);

  const loadData = async () => {
    try {
      setLoading(true);
      
      console.log('[FilteredCustomersTab] Loading data...');
      console.log('[FilteredCustomersTab] allowedUserIds:', allowedUserIds);
      console.log('[FilteredCustomersTab] currentUser:', currentUser);
      
      // Load users
      const usersSnapshot = await getDocs(collection(db, 'users'));
      const usersMap: { [key: string]: User } = {};
      usersSnapshot.docs.forEach(doc => {
        usersMap[doc.id] = { ...doc.data(), uid: doc.id } as User;
      });
      setUsers(usersMap);
      console.log('[FilteredCustomersTab] Loaded users:', Object.keys(usersMap).length);

      // Load customers - filter by allowedUserIds if provided
      let customersData: Customer[] = [];
      
      if (allowedUserIds && allowedUserIds.length > 0) {
        console.log('[FilteredCustomersTab] Filtering by allowedUserIds:', allowedUserIds);
        
        // Load all customers first since they might use different field names (addedBy, userId, createdBy)
        const allCustomersSnapshot = await getDocs(collection(db, 'customers'));
        const allCustomers = allCustomersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id }));
        console.log('[FilteredCustomersTab] Total customers in DB:', allCustomers.length);
        
        if (allCustomers.length > 0) {
          const sampleCustomer = allCustomers[0];
          console.log('[FilteredCustomersTab] Sample customer from DB:', {
            id: sampleCustomer.id,
            fullName: sampleCustomer.fullName,
            addedBy: sampleCustomer.addedBy,
            userId: sampleCustomer.userId,
            createdBy: sampleCustomer.createdBy,
            allFields: Object.keys(sampleCustomer)
          });
        }
        
        // Try querying by 'addedBy' first (for efficiency when field name matches)
        try {
          if (allowedUserIds.length <= 10) {
            const q = query(collection(db, 'customers'), where('addedBy', 'in', allowedUserIds));
            const snapshot = await getDocs(q);
            customersData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
            console.log('[FilteredCustomersTab] Query by addedBy (single batch):', customersData.length, 'customers');
          } else {
            // Batch queries for more than 10 users
            console.log('[FilteredCustomersTab] Using batch queries for', allowedUserIds.length, 'users');
            const batches: string[][] = [];
            for (let i = 0; i < allowedUserIds.length; i += 10) {
              batches.push(allowedUserIds.slice(i, i + 10));
            }
            
            for (const batch of batches) {
              const q = query(collection(db, 'customers'), where('addedBy', 'in', batch));
              const snapshot = await getDocs(q);
              const batchData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
              customersData = [...customersData, ...batchData];
              console.log('[FilteredCustomersTab] Batch query (addedBy) returned:', batchData.length, 'customers');
            }
            console.log('[FilteredCustomersTab] Total customers from batches (addedBy):', customersData.length);
          }
        } catch (error) {
          console.error('[FilteredCustomersTab] Error querying by addedBy:', error);
        }
        
        // Always do client-side filtering to catch customers using different field names
        // This ensures we find all customers regardless of which field name they use
        if (allCustomers.length > 0) {
          console.log('[FilteredCustomersTab] Applying client-side filter to check all field names (addedBy, userId, createdBy)...');
          const clientSideFiltered = allCustomers.filter((c: any) => {
            return allowedUserIds.includes(c.addedBy) || 
                   allowedUserIds.includes(c.userId) || 
                   allowedUserIds.includes(c.createdBy);
          });
          
          // Merge with query results, avoiding duplicates
          const existingIds = new Set(customersData.map(c => c.id));
          const newCustomers = clientSideFiltered
            .filter((c: any) => !existingIds.has(c.id))
            .map((c: any) => ({ ...c, id: c.id } as Customer));
          
          if (newCustomers.length > 0) {
            console.log('[FilteredCustomersTab] Found', newCustomers.length, 'additional customers via client-side filtering');
            newCustomers.forEach(c => {
              console.log('[FilteredCustomersTab] Customer found:', {
                id: c.id,
                fullName: c.fullName,
                addedBy: (c as any).addedBy,
                userId: (c as any).userId,
                createdBy: (c as any).createdBy,
              });
            });
          }
          
          customersData = [...customersData, ...newCustomers];
          console.log('[FilteredCustomersTab] Total customers after merging:', customersData.length);
        }
        
      } else if (currentUser && currentUser.role === 'agent') {
        // Agent - only their own customers
        console.log('[FilteredCustomersTab] Loading agent customers for:', currentUser.uid);
        const q = query(collection(db, 'customers'), where('addedBy', '==', currentUser.uid));
        const snapshot = await getDocs(q);
        customersData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
        console.log('[FilteredCustomersTab] Agent customers:', customersData.length);
      } else {
        // Load all customers
        console.log('[FilteredCustomersTab] Loading ALL customers (no filter)');
        const snapshot = await getDocs(collection(db, 'customers'));
        customersData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
        console.log('[FilteredCustomersTab] All customers loaded:', customersData.length);
      }

      console.log('[FilteredCustomersTab] Final customersData length:', customersData.length);
      setCustomers(customersData);
    } catch (error) {
      console.error('[FilteredCustomersTab] Error loading data:', error);
    } finally {
      setLoading(false);
    }
  };

  const uniqueUsers = useMemo(() => {
    const userIds = allowedUserIds || [];
    return Array.from(new Set(
      customers
        .map(c => c.addedBy)
        .filter(id => !allowedUserIds || allowedUserIds.includes(id))
        .filter(Boolean)
    ));
  }, [customers, allowedUserIds]);

  const filteredCustomers = useMemo(() => {
    return customers.filter(customer => {
      // Search filter
      const searchLower = searchTerm.toLowerCase();
      const matchesSearch = customer.fullName?.toLowerCase().includes(searchLower) ||
        customer.phoneNumber?.toLowerCase().includes(searchLower);

      // User filter
      const matchesUser = userFilter === 'all' || customer.addedBy === userFilter;

      // Date range filter
      let matchesDate = true;
      if (dateRange && dateRange[0] && dateRange[1]) {
        const createdDate = customer.createdAt instanceof Timestamp 
          ? customer.createdAt.toDate() 
          : new Date(customer.createdAt as any);
        matchesDate = createdDate >= dateRange[0].toDate() && createdDate <= dateRange[1].toDate();
      }

      return matchesSearch && matchesUser && matchesDate;
    });
  }, [customers, searchTerm, userFilter, dateRange, currentUser]);

  const handleExport = () => {
    const filename = `customers_${format(new Date(), 'yyyy-MM-dd_HH-mm-ss')}.xlsx`;
    exportCustomersToExcel(filteredCustomers, users, filename);
  };

  return (
    <Card
      style={{
        background: colors.midnight_green[400],
        border: `1px solid ${colors.air_force_blue[300]}40`,
      }}
    >
      {/* Filters */}
      <Space direction="vertical" size="middle" style={{ width: '100%', marginBottom: 16 }}>
        <Row gutter={[16, 16]}>
          <Col xs={24} sm={12} md={8} lg={6}>
            <Input
              placeholder="Search name or phone..."
              prefix={<SearchOutlined />}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              allowClear
            />
          </Col>
          {uniqueUsers.length > 1 && (
            <Col xs={24} sm={12} md={8} lg={6}>
              <Select
                style={{ width: '100%' }}
                placeholder="Added By"
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
        dataSource={filteredCustomers}
        loading={loading}
        rowKey="id"
        pagination={{
          pageSize: 50,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} customers`,
        }}
        scroll={{ x: 'max-content' }}
      />
    </Card>
  );
}

