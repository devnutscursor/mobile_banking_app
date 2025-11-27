'use client';

import { useState, useEffect, useMemo } from 'react';
import { collection, getDocs, query, where, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Card, Row, Col, Statistic, Tabs, Space, Typography, Spin, Button, App } from 'antd';
import {
  ShoppingOutlined,
  UserOutlined,
  DollarOutlined,
  RiseOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import { colors } from '@/lib/theme';
import AntdProvider from '@/components/AntdProvider';
import FilteredTransactionsTab from '@/components/FilteredTransactionsTab';
import FilteredCustomersTab from '@/components/FilteredCustomersTab';
import FilteredCommissionsTab from '@/components/FilteredCommissionsTab';
import { useAuth } from '@/lib/authContext';
import { Transaction, Customer, Commission, User as UserType } from '@/lib/types';
import { exportTransactionsToExcel, exportCustomersToExcel, exportCommissionsToExcel } from '@/lib/exportUtils';
import { format } from 'date-fns';

const { Title } = Typography;

export default function AgentDashboardPage() {
  const { user: currentUser } = useAuth();
  const { message } = App.useApp();
  const [activeTab, setActiveTab] = useState('overview');
  const [stats, setStats] = useState({
    totalTransactions: 0,
    totalCustomers: 0,
    totalCommissions: 0,
  });
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    if (currentUser) {
      loadStats();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  const loadStats = async () => {
    if (!currentUser) return;
    
    try {
      setLoading(true);
      
      // Load transactions count
      const txnQuery = query(collection(db, 'transactions'), where('userId', '==', currentUser.uid));
      const txnSnapshot = await getDocs(txnQuery);
      
      // Load customers count
      const custQuery = query(collection(db, 'customers'), where('addedBy', '==', currentUser.uid));
      const custSnapshot = await getDocs(custQuery);

      // Load current month commissions
      const now = new Date();
      const year = now.getFullYear();
      const month = now.getMonth() + 1;
      
      const commQuery = query(
        collection(db, 'commissions'),
        where('userId', '==', currentUser.uid),
        where('year', '==', year),
        where('month', '==', month)
      );
      const commSnapshot = await getDocs(commQuery);
      let commissionSum = 0;
      commSnapshot.docs.forEach(doc => {
        const data = doc.data();
        commissionSum += data.totalCommission || 0;
      });

      setStats({
        totalTransactions: txnSnapshot.size,
        totalCustomers: custSnapshot.size,
        totalCommissions: commissionSum,
      });
    } catch (error) {
      console.error('Error loading stats:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleExportAll = async () => {
    if (!currentUser) return;
    
    try {
      setExporting(true);
      message.loading({ content: 'Loading data for export...', key: 'export', duration: 0 });

      // Load all agent's data
      const [transactionsSnapshot, customersSnapshot, commissionsSnapshot, usersSnapshot] = await Promise.all([
        getDocs(query(collection(db, 'transactions'), where('userId', '==', currentUser.uid))),
        getDocs(query(collection(db, 'customers'), where('addedBy', '==', currentUser.uid))),
        getDocs(query(collection(db, 'commissions'), where('userId', '==', currentUser.uid))),
        getDocs(collection(db, 'users')),
      ]);

      const transactions = transactionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
      const customers = customersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
      const commissions = commissionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));

      const users = usersSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as UserType));
      const usersMap: { [key: string]: UserType } = {};
      users.forEach(u => { usersMap[u.uid] = u; });

      // Load related data for transactions
      const [operatorsSnapshot, actionsSnapshot, allCustomersSnapshot] = await Promise.all([
        getDocs(collection(db, 'operators')),
        getDocs(collection(db, 'operator_actions')),
        getDocs(collection(db, 'customers')),
      ]);

      const operators = operatorsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as any));
      const actions = actionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as any));
      const allCustomers = allCustomersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));

      const operatorsMap: { [key: string]: any } = {};
      operators.forEach(op => { operatorsMap[op.id || ''] = op; });

      const actionsMap: { [key: string]: any } = {};
      actions.forEach(action => { actionsMap[action.id || ''] = action; });

      const customersMap: { [key: string]: Customer } = {};
      allCustomers.forEach(c => { customersMap[c.id || ''] = c; });

      message.loading({ content: 'Generating Excel files...', key: 'export' });

      const timestamp = format(new Date(), 'yyyy-MM-dd_HH-mm-ss');

      // Export each category
      exportTransactionsToExcel(transactions, usersMap, customersMap, operatorsMap, actionsMap, `agent_transactions_${timestamp}.xlsx`);
      exportCustomersToExcel(customers, usersMap, `agent_customers_${timestamp}.xlsx`);
      exportCommissionsToExcel(commissions, `agent_commissions_${timestamp}.xlsx`);

      message.success({ content: 'All data exported successfully!', key: 'export', duration: 3 });
    } catch (error) {
      console.error('Export error:', error);
      message.error({ content: 'Failed to export data. Please try again.', key: 'export', duration: 3 });
    } finally {
      setExporting(false);
    }
  };

  const statCards = [
    {
      title: 'My Transactions',
      value: stats.totalTransactions,
      icon: <ShoppingOutlined />,
      gradient: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
    },
    {
      title: 'My Customers',
      value: stats.totalCustomers,
      icon: <UserOutlined />,
      gradient: `linear-gradient(135deg, ${colors.air_force_blue[600]}, ${colors.midnight_green[700]})`,
    },
    {
      title: 'This Month Commissions',
      value: `$${stats.totalCommissions.toFixed(2)}`,
      icon: <DollarOutlined />,
      gradient: `linear-gradient(135deg, ${colors.midnight_green[700]}, ${colors.air_force_blue[700]})`,
    },
  ];

  const tabItems = useMemo(() => [
    {
      key: 'overview',
      label: (
        <Space>
          <RiseOutlined />
          <span>Overview</span>
        </Space>
      ),
      children: null,
    },
    {
      key: 'transactions',
      label: (
        <Space>
          <ShoppingOutlined />
          <span>My Transactions</span>
        </Space>
      ),
      children: <FilteredTransactionsTab allowedUserIds={currentUser ? [currentUser.uid] : []} showExport={true} />,
    },
    {
      key: 'customers',
      label: (
        <Space>
          <UserOutlined />
          <span>My Customers</span>
        </Space>
      ),
      children: <FilteredCustomersTab allowedUserIds={currentUser ? [currentUser.uid] : []} showExport={true} />,
    },
    {
      key: 'commissions',
      label: (
        <Space>
          <DollarOutlined />
          <span>My Commissions</span>
        </Space>
      ),
      children: <FilteredCommissionsTab allowedUserIds={currentUser ? [currentUser.uid] : []} showExport={true} />,
    },
  ], [currentUser]);

  if (!currentUser) {
    return null;
  }

  return (
    <AntdProvider>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* Page Title */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={2} style={{ color: colors.beige[500], marginBottom: 8 }}>
              Agent Dashboard
            </Title>
            <Typography.Text style={{ color: colors.ash_gray[500], fontSize: 16 }}>
              Welcome, {currentUser.name}
            </Typography.Text>
          </div>
          {activeTab === 'overview' && (
            <Button
              type="primary"
              icon={<DownloadOutlined />}
              loading={exporting}
              onClick={handleExportAll}
              size="large"
              style={{
                background: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
                border: 'none',
                height: 48,
                paddingLeft: 24,
                paddingRight: 24,
              }}
            >
              Export All Data
            </Button>
          )}
        </div>

        {/* Tabs */}
        <Card
          style={{
            background: colors.midnight_green[500],
            border: `1px solid ${colors.air_force_blue[300]}40`,
            borderRadius: 16,
            boxShadow: '0 8px 30px rgba(1, 22, 30, 0.5)',
          }}
          styles={{ body: { padding: 0 } }}
        >
          <Tabs
            activeKey={activeTab}
            onChange={setActiveTab}
            size="large"
            items={tabItems}
            style={{
              padding: '0 24px',
            }}
          />
        </Card>

        {/* Content */}
        {loading && activeTab === 'overview' ? (
          <div style={{ textAlign: 'center', padding: '80px 0' }}>
            <Spin size="large" tip="Loading dashboard...">
              <div style={{ width: 1, height: 1 }} />
            </Spin>
          </div>
        ) : (
          <>
            {activeTab === 'overview' && (
              <Row gutter={[24, 24]}>
                {statCards.map((card, index) => (
                  <Col xs={24} sm={12} lg={8} key={index}>
                    <Card
                      className="hover-lift"
                      style={{
                        background: card.gradient,
                        border: 'none',
                        borderRadius: 16,
                        boxShadow: '0 8px 24px rgba(1, 22, 30, 0.5)',
                        overflow: 'hidden',
                        position: 'relative',
                      }}
                      styles={{ body: { padding: 24 } }}
                    >
                      <div style={{
                        position: 'absolute',
                        top: -20,
                        right: -20,
                        width: 120,
                        height: 120,
                        borderRadius: '50%',
                        background: `rgba(255, 255, 255, 0.1)`,
                        filter: 'blur(40px)',
                      }} />
                      
                      <Space direction="vertical" size="small" style={{ width: '100%', position: 'relative' }}>
                        <div style={{
                          width: 48,
                          height: 48,
                          borderRadius: 12,
                          background: 'rgba(255, 255, 255, 0.2)',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center',
                          marginBottom: 8,
                        }}>
                          <span style={{ fontSize: 24, color: colors.beige[500] }}>
                            {card.icon}
                          </span>
                        </div>
                        
                        <Statistic
                          title={
                            <span style={{ color: colors.beige[600], fontSize: 13 }}>
                              {card.title}
                            </span>
                          }
                          value={card.value}
                          valueStyle={{
                            color: colors.beige[500],
                            fontSize: 32,
                            fontWeight: 700,
                          }}
                        />
                      </Space>
                    </Card>
                  </Col>
                ))}
              </Row>
            )}
          </>
        )}
      </Space>
    </AntdProvider>
  );
}

