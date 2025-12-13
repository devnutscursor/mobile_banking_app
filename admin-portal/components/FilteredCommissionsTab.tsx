'use client';

import { useState, useEffect, useMemo } from 'react';
import { collection, getDocs, query, where, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Commission, User } from '@/lib/types';
import { Card, Select, Space, Table, Typography, Row, Col, Button, InputNumber } from 'antd';
import { DownloadOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import { colors } from '@/lib/theme';
import { exportCommissionsToExcel, exportCommissionSummaryToExcel } from '@/lib/exportUtils';
import { useAuth } from '@/lib/authContext';
import { formatCurrencyWithSymbol } from '@/lib/formatUtils';

interface FilteredCommissionsTabProps {
  allowedUserIds?: string[];
  showExport?: boolean;
}

export default function FilteredCommissionsTab({ allowedUserIds, showExport = true }: FilteredCommissionsTabProps) {
  const { user: currentUser } = useAuth();
  const [commissions, setCommissions] = useState<Commission[]>([]);
  const [loading, setLoading] = useState(true);
  const [yearFilter, setYearFilter] = useState<number>(new Date().getFullYear());
  const [monthFilter, setMonthFilter] = useState<number>(new Date().getMonth() + 1);
  const [userFilter, setUserFilter] = useState<string>('all');
  const [viewMode, setViewMode] = useState<'detailed' | 'summary'>('detailed');

  useEffect(() => {
    loadData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [yearFilter, monthFilter, allowedUserIds]);

  const loadData = async () => {
    try {
      setLoading(true);
      
      let commissionsData: Commission[] = [];
      
      if (allowedUserIds && allowedUserIds.length > 0) {
        // Load commissions for multiple users
        if (allowedUserIds.length <= 10) {
          const q = query(
            collection(db, 'commissions'),
            where('userId', 'in', allowedUserIds),
            where('year', '==', yearFilter),
            where('month', '==', monthFilter)
          );
          const snapshot = await getDocs(q);
          commissionsData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));
        } else {
          // Batch queries
          const batches: string[][] = [];
          for (let i = 0; i < allowedUserIds.length; i += 10) {
            batches.push(allowedUserIds.slice(i, i + 10));
          }
          
          for (const batch of batches) {
            const q = query(
              collection(db, 'commissions'),
              where('userId', 'in', batch),
              where('year', '==', yearFilter),
              where('month', '==', monthFilter)
            );
            const snapshot = await getDocs(q);
            const batchData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));
            commissionsData = [...commissionsData, ...batchData];
          }
        }
      } else if (currentUser && currentUser.role === 'agent') {
        // Agent - only their own commissions
        const q = query(
          collection(db, 'commissions'),
          where('userId', '==', currentUser.uid),
          where('year', '==', yearFilter),
          where('month', '==', monthFilter)
        );
        const snapshot = await getDocs(q);
        commissionsData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));
      } else {
        // Load all commissions for the period
        const q = query(
          collection(db, 'commissions'),
          where('year', '==', yearFilter),
          where('month', '==', monthFilter)
        );
        const snapshot = await getDocs(q);
        commissionsData = snapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));
      }

      setCommissions(commissionsData);
    } catch (error) {
      console.error('Error loading commissions:', error);
    } finally {
      setLoading(false);
    }
  };

  const uniqueUsers = useMemo(() => {
    return Array.from(new Set(commissions.map(c => c.userId).filter(Boolean)));
  }, [commissions]);

  const filteredCommissions = useMemo(() => {
    if (viewMode === 'summary') {
      // Return summary data (grouped by user)
      return commissions;
    }
    
    return commissions.filter(comm => {
      if (userFilter !== 'all' && comm.userId !== userFilter) return false;
      return true;
    });
  }, [commissions, userFilter, viewMode]);

  const summaryData = useMemo(() => {
    const summaryMap: { [key: string]: {
      userName: string;
      userRole: string;
      transactionCount: number;
      totalCommission: number;
    } } = {};

    filteredCommissions.forEach(comm => {
      if (!summaryMap[comm.userId]) {
        summaryMap[comm.userId] = {
          userName: comm.userName,
          userRole: comm.userRole,
          transactionCount: 0,
          totalCommission: 0,
        };
      }
      summaryMap[comm.userId].transactionCount++;
      summaryMap[comm.userId].totalCommission += comm.totalCommission;
    });

    return Object.values(summaryMap);
  }, [filteredCommissions]);

  const handleExport = () => {
    if (viewMode === 'summary') {
      const filename = `commission_summary_${yearFilter}_${monthFilter}_${format(new Date(), 'yyyy-MM-dd_HH-mm-ss')}.xlsx`;
      exportCommissionSummaryToExcel(filteredCommissions, filename);
    } else {
      const filename = `commissions_${yearFilter}_${monthFilter}_${format(new Date(), 'yyyy-MM-dd_HH-mm-ss')}.xlsx`;
      exportCommissionsToExcel(filteredCommissions, filename);
    }
  };

  const columns = viewMode === 'summary' ? [
    {
      title: 'Agent/Dealer Name',
      dataIndex: 'userName',
      key: 'userName',
      render: (name: string, record: any) => (
        <Typography.Text strong style={{ color: colors.beige[500] }}>
          {name} ({record.userRole})
        </Typography.Text>
      ),
    },
    {
      title: 'Transaction Count',
      dataIndex: 'transactionCount',
      key: 'transactionCount',
    },
    {
      title: 'Total Commission',
      dataIndex: 'totalCommission',
      key: 'totalCommission',
      render: (amt: number) => (
        <Typography.Text strong style={{ color: colors.beige[500] }}>
          {formatCurrencyWithSymbol(amt)}
        </Typography.Text>
      ),
    },
  ] : [
    {
      title: 'Date',
      dataIndex: 'commissionDate',
      key: 'date',
      render: (d: any) => d ? format(d instanceof Timestamp ? d.toDate() : new Date(d), 'PP') : 'N/A',
    },
    ...(allowedUserIds && allowedUserIds.length > 1 ? [{
      title: 'Agent/Dealer',
      dataIndex: 'userName',
      key: 'userName',
      render: (name: string, record: Commission) => (
        <Typography.Text strong style={{ color: colors.beige[500] }}>
          {name} ({record.userRole})
        </Typography.Text>
      ),
    }] : []),
    {
      title: 'Operator',
      dataIndex: 'operatorName',
      key: 'operatorName',
    },
    {
      title: 'Transaction Type',
      dataIndex: 'transactionType',
      key: 'transactionType',
    },
    {
      title: 'Transaction Amount',
      dataIndex: 'transactionAmount',
      key: 'transactionAmount',
      render: (amt: number) => formatCurrencyWithSymbol(amt),
    },
    {
      title: 'Commission Rate (%)',
      dataIndex: 'commissionRate',
      key: 'commissionRate',
      render: (rate: number) => `${rate.toFixed(2)}%`,
    },
    {
      title: 'Total Commission',
      dataIndex: 'totalCommission',
      key: 'totalCommission',
      render: (amt: number) => (
        <Typography.Text strong style={{ color: colors.beige[500] }}>
          {formatCurrencyWithSymbol(amt)}
        </Typography.Text>
      ),
    },
  ];

  const dataSource = viewMode === 'summary' ? summaryData : filteredCommissions;

  // Generate year and month options
  const currentYear = new Date().getFullYear();
  const years = Array.from({ length: 5 }, (_, i) => currentYear - i);
  const months = Array.from({ length: 12 }, (_, i) => i + 1);
  const monthNames = ['January', 'February', 'March', 'April', 'May', 'June', 
    'July', 'August', 'September', 'October', 'November', 'December'];

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
          <Col xs={24} sm={12} md={6} lg={4}>
            <Select
              style={{ width: '100%' }}
              placeholder="Year"
              value={yearFilter}
              onChange={setYearFilter}
            >
              {years.map(year => (
                <Select.Option key={year} value={year}>{year}</Select.Option>
              ))}
            </Select>
          </Col>
          <Col xs={24} sm={12} md={6} lg={4}>
            <Select
              style={{ width: '100%' }}
              placeholder="Month"
              value={monthFilter}
              onChange={setMonthFilter}
            >
              {months.map(month => (
                <Select.Option key={month} value={month}>{monthNames[month - 1]}</Select.Option>
              ))}
            </Select>
          </Col>
          {viewMode === 'detailed' && uniqueUsers.length > 1 && (
            <Col xs={24} sm={12} md={6} lg={4}>
              <Select
                style={{ width: '100%' }}
                placeholder="Agent/Dealer"
                value={userFilter}
                onChange={setUserFilter}
              >
                <Select.Option value="all">All</Select.Option>
                {uniqueUsers.map(uid => {
                  const comm = commissions.find(c => c.userId === uid);
                  return (
                    <Select.Option key={uid} value={uid}>
                      {comm?.userName || uid}
                    </Select.Option>
                  );
                })}
              </Select>
            </Col>
          )}
          <Col xs={24} sm={12} md={6} lg={4}>
            <Select
              style={{ width: '100%' }}
              value={viewMode}
              onChange={setViewMode}
            >
              <Select.Option value="detailed">Detailed View</Select.Option>
              <Select.Option value="summary">Summary View</Select.Option>
            </Select>
          </Col>
        </Row>
      </Space>

      {/* Table */}
      <Table
        columns={columns}
        dataSource={dataSource}
        loading={loading}
        rowKey={(record: any) => record.id || record.userId || Math.random().toString()}
        pagination={{
          pageSize: 50,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} ${viewMode === 'summary' ? 'agents/dealers' : 'commissions'}`,
        }}
        scroll={{ x: 'max-content' }}
      />
    </Card>
  );
}

