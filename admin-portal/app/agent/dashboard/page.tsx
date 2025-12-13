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
import { formatCurrencyWithSymbol } from '@/lib/formatUtils';

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
      
      // Load customers count - check multiple field names (addedBy, userId, createdBy)
      // Some customers might use different field names
      let customerCount = 0;
      try {
        // Try querying by 'addedBy' first
        const custQuery = query(collection(db, 'customers'), where('addedBy', '==', currentUser.uid));
        const custSnapshot = await getDocs(custQuery);
        customerCount = custSnapshot.size;
        
        // Also do client-side filtering to catch customers using different field names
        // This ensures we find all customers regardless of which field name they use
        const allCustomersSnapshot = await getDocs(collection(db, 'customers'));
        const allCustomers = allCustomersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id }));
        
        // Filter by checking all possible field names
        const additionalCustomers = allCustomers.filter((c: any) => {
          const matches = (c.addedBy === currentUser.uid) || 
                         (c.userId === currentUser.uid) || 
                         (c.createdBy === currentUser.uid);
          // Only count if not already counted in the query result
          return matches && !custSnapshot.docs.some(doc => doc.id === c.id);
        });
        
        customerCount += additionalCustomers.length;
      } catch (error) {
        console.error('Error loading customers count:', error);
        // Fallback: load all and filter client-side
        const allCustomersSnapshot = await getDocs(collection(db, 'customers'));
        const allCustomers = allCustomersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id }));
        customerCount = allCustomers.filter((c: any) => {
          return (c.addedBy === currentUser.uid) || 
                 (c.userId === currentUser.uid) || 
                 (c.createdBy === currentUser.uid);
        }).length;
      }

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
        totalCustomers: customerCount,
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
      const [transactionsSnapshot, customersSnapshot, commissionsSnapshot, usersSnapshot, allCustomersSnapshot] = await Promise.all([
        getDocs(query(collection(db, 'transactions'), where('userId', '==', currentUser.uid))),
        getDocs(query(collection(db, 'customers'), where('addedBy', '==', currentUser.uid))),
        getDocs(query(collection(db, 'commissions'), where('userId', '==', currentUser.uid))),
        getDocs(collection(db, 'users')),
        getDocs(collection(db, 'customers')), // Load all customers for client-side filtering
      ]);

      const transactions = transactionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
      
      // Get customers from query result
      let customers = customersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
      
      // Also check for customers using different field names (userId, createdBy)
      const allCustomers = allCustomersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id }));
      const existingCustomerIds = new Set(customers.map(c => c.id));
      
      // Filter customers that match by any of the possible field names
      const additionalCustomers = allCustomers
        .filter((c: any) => {
          const matches = (c.addedBy === currentUser.uid) || 
                         (c.userId === currentUser.uid) || 
                         (c.createdBy === currentUser.uid);
          return matches && !existingCustomerIds.has(c.id);
        })
        .map(c => ({ ...c, id: c.id } as Customer));
      
      customers = [...customers, ...additionalCustomers];
      
      const commissions = commissionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));

      const users = usersSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as UserType));
      const usersMap: { [key: string]: UserType } = {};
      users.forEach(u => { usersMap[u.uid] = u; });

      // Load related data for transactions
      const [operatorsSnapshot, actionsSnapshot] = await Promise.all([
        getDocs(collection(db, 'operators')),
        getDocs(collection(db, 'operator_actions')),
      ]);

      const operators = operatorsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as any));
      const actions = actionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as any));
      // Use all customers from the earlier query for the customers map
      const allCustomersForMap = allCustomersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));

      const operatorsMap: { [key: string]: any } = {};
      operators.forEach(op => { operatorsMap[op.id || ''] = op; });

      const actionsMap: { [key: string]: any } = {};
      actions.forEach(action => { actionsMap[action.id || ''] = action; });

      const customersMap: { [key: string]: Customer } = {};
      allCustomersForMap.forEach(c => { customersMap[c.id || ''] = c; });

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
      value: formatCurrencyWithSymbol(stats.totalCommissions),
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

