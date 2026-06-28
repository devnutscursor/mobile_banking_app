'use client';

import { useState, useEffect } from 'react';
import { collection, getDocs, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { User, Transaction, DashboardStats, Customer, License, Operator, OperatorAction, Commission } from '@/lib/types';
import { Card, Row, Col, Statistic, Tabs, Spin, Space, Typography, Button, App } from 'antd';
import {
  UserOutlined,
  TeamOutlined,
  DollarOutlined,
  ShoppingOutlined,
  KeyOutlined,
  RiseOutlined,
  CheckCircleOutlined,
  ShopOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import { colors } from '@/lib/theme';
import DealersTab from '@/components/DealersTab';
import AgentsTab from '@/components/AgentsTab';
import LicensesTab from '@/components/LicensesTab';
import TransactionsTab from '@/components/TransactionsTab';
import CustomersTab from '@/components/CustomersTab';
import OperatorsTab from '@/components/OperatorsTab';
import { exportTransactionsToExcel, exportCustomersToExcel, exportCommissionsToExcel, exportOperatorsToExcel, exportOperatorActionsToExcel, exportLicensesToExcel, exportUsersToExcel } from '@/lib/exportUtils';
import { format } from 'date-fns';

const { Title } = Typography;

export default function DashboardPage() {
  const { message } = App.useApp();
  const [activeTab, setActiveTab] = useState('overview');
  const [stats, setStats] = useState<DashboardStats>({
    totalUsers: 0,
    totalDealers: 0,
    totalAgents: 0,
    totalTransactions: 0,
    totalRevenue: 0,
    weeklyTransactions: 0,
    activeUsers: 0,
    activeLicenses: 0,
  });
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
    try {
      setLoading(true);

      const usersSnapshot = await getDocs(collection(db, 'users'));
      const users = usersSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as User));
      
      const dealers = users.filter(u => u.role === 'dealer');
      const agents = users.filter(u => u.role === 'agent');
      const activeUsers = users.filter(u => u.active);

      const transactionsSnapshot = await getDocs(collection(db, 'transactions'));
      const transactions = transactionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
      
      const oneWeekAgo = new Date();
      oneWeekAgo.setDate(oneWeekAgo.getDate() - 7);
      const weeklyTransactions = transactions.filter(t => {
        const createdAt = t.createdAt instanceof Timestamp ? t.createdAt.toDate() : new Date(t.createdAt);
        return createdAt >= oneWeekAgo;
      }).length;

      // Calculate total commissions for agents and dealers
      // Using commissionAmount (base commission without tax) from Firestore commissions collection
      const commissionsSnapshot = await getDocs(collection(db, 'commissions'));
      const commissions = commissionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));
      
      // Get all agent and dealer IDs
      const agentAndDealerIds = [...agents.map(a => a.uid), ...dealers.map(d => d.uid)];
      
      // Sum up commission amounts (without tax) for agents and dealers
      // commissionAmount = base commission earned (before tax)
      const totalAgentsDealersCommission = commissions
        .filter(c => agentAndDealerIds.includes(c.userId))
        .reduce((sum, c) => sum + (c.commissionAmount || 0), 0);

      const licensesSnapshot = await getDocs(collection(db, 'licenses'));
      const activeLicenses = licensesSnapshot.docs.filter(doc => doc.data().isActive).length;

      setStats({
        totalUsers: users.length,
        totalDealers: dealers.length,
        totalAgents: agents.length,
        totalTransactions: transactions.length,
        totalRevenue: totalAgentsDealersCommission, // Store total commissions in totalRevenue field
        weeklyTransactions,
        activeUsers: activeUsers.length,
        activeLicenses,
      });
    } catch (error) {
      console.error('Error loading stats:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleExportAll = async () => {
    try {
      setExporting(true);
      message.loading({ content: 'Loading all data for export...', key: 'export', duration: 0 });

      // Load all data
      const [transactionsSnapshot, customersSnapshot, commissionsSnapshot, operatorsSnapshot, actionsSnapshot, licensesSnapshot, usersSnapshot] = await Promise.all([
        getDocs(collection(db, 'transactions')),
        getDocs(collection(db, 'customers')),
        getDocs(collection(db, 'commissions')),
        getDocs(collection(db, 'operators')),
        getDocs(collection(db, 'operator_actions')),
        getDocs(collection(db, 'licenses')),
        getDocs(collection(db, 'users')),
      ]);

      const transactions = transactionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
      const customers = customersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
      const commissions = commissionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));
      const operators = operatorsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Operator));
      const operatorActions = actionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as OperatorAction));
      const licenses = licensesSnapshot.docs.map(doc => ({ ...doc.data(), licenseKey: doc.id } as License));
      const users = usersSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as User));

      const dealers = users.filter(u => u.role === 'dealer');
      const agents = users.filter(u => u.role === 'agent');

      // Create maps for relationships
      const usersMap: { [key: string]: User } = {};
      users.forEach(u => { usersMap[u.uid] = u; });

      const operatorsMap: { [key: string]: Operator } = {};
      operators.forEach(op => { operatorsMap[op.id || ''] = op; });

      const actionsMap: { [key: string]: OperatorAction } = {};
      operatorActions.forEach(action => { actionsMap[action.id || ''] = action; });

      const customersMap: { [key: string]: Customer } = {};
      customers.forEach(c => { customersMap[c.id || ''] = c; });

      message.loading({ content: 'Generating Excel files...', key: 'export' });

      const timestamp = format(new Date(), 'yyyy-MM-dd_HH-mm-ss');

      // Export each category separately
      if (transactions.length > 0) {
        exportTransactionsToExcel(transactions, usersMap, customersMap, operatorsMap, actionsMap, `admin_transactions_${timestamp}.xlsx`);
      }
      if (customers.length > 0) {
        exportCustomersToExcel(customers, usersMap, `admin_customers_${timestamp}.xlsx`);
      }
      if (commissions.length > 0) {
        exportCommissionsToExcel(commissions, `admin_commissions_${timestamp}.xlsx`);
      }
      if (operators.length > 0) {
        exportOperatorsToExcel(operators, usersMap, `admin_operators_${timestamp}.xlsx`);
      }
      if (operatorActions.length > 0) {
        exportOperatorActionsToExcel(operatorActions, operatorsMap, usersMap, `admin_operator_actions_${timestamp}.xlsx`);
      }
      if (licenses.length > 0) {
        exportLicensesToExcel(licenses, usersMap, `admin_licenses_${timestamp}.xlsx`);
      }
      if (dealers.length > 0) {
        exportUsersToExcel(dealers, `admin_dealers_${timestamp}.xlsx`);
      }
      if (agents.length > 0) {
        exportUsersToExcel(agents, `admin_agents_${timestamp}.xlsx`);
      }

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
      title: 'Total Users',
      value: stats.totalUsers,
      icon: <UserOutlined />,
      gradient: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
      color: colors.rich_black[800],
    },
    {
      title: 'Total Agents/Dealers Commission',
      value: `$${stats.totalRevenue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      icon: <DollarOutlined />,
      gradient: `linear-gradient(135deg, ${colors.air_force_blue[600]}, ${colors.midnight_green[700]})`,
      color: colors.air_force_blue[800],
    },
    {
      title: 'Total Agents',
      value: stats.totalAgents,
      icon: <TeamOutlined />,
      gradient: `linear-gradient(135deg, ${colors.midnight_green[700]}, ${colors.air_force_blue[700]})`,
      color: colors.midnight_green[800],
    },
    {
      title: 'Active Users',
      value: stats.activeUsers,
      icon: <CheckCircleOutlined />,
      gradient: `linear-gradient(135deg, ${colors.air_force_blue[700]}, ${colors.rich_black[700]})`,
      color: colors.air_force_blue[800],
    },
    {
      title: 'Total Transactions',
      value: stats.totalTransactions,
      icon: <ShoppingOutlined />,
      gradient: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
      color: colors.midnight_green[800],
    },
    {
      title: 'Weekly Transactions',
      value: stats.weeklyTransactions,
      icon: <RiseOutlined />,
      gradient: `linear-gradient(135deg, ${colors.air_force_blue[600]}, ${colors.midnight_green[700]})`,
      color: colors.air_force_blue[800],
    },
    {
      title: 'Active Licenses',
      value: stats.activeLicenses,
      icon: <KeyOutlined />,
      gradient: `linear-gradient(135deg, ${colors.air_force_blue[600]}, ${colors.rich_black[700]})`,
      color: colors.air_force_blue[800],
    },
  ];

  const tabItems = [
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
      key: 'dealers',
      label: (
        <Space>
          <ShopOutlined />
          <span>Dealers</span>
        </Space>
      ),
      children: <DealersTab onUpdate={loadStats} />,
    },
    {
      key: 'agents',
      label: (
        <Space>
          <TeamOutlined />
          <span>Agents</span>
        </Space>
      ),
      children: <AgentsTab onUpdate={loadStats} />,
    },
    {
      key: 'licenses',
      label: (
        <Space>
          <KeyOutlined />
          <span>Licenses</span>
        </Space>
      ),
      children: <LicensesTab onUpdate={loadStats} />,
    },
    {
      key: 'transactions',
      label: (
        <Space>
          <ShoppingOutlined />
          <span>Transactions</span>
        </Space>
      ),
      children: <TransactionsTab />,
    },
    {
      key: 'customers',
      label: (
        <Space>
          <UserOutlined />
          <span>Customers</span>
        </Space>
      ),
      children: <CustomersTab />,
    },
    {
      key: 'operators',
      label: (
        <Space>
          <ShopOutlined />
          <span>Operators</span>
        </Space>
      ),
      children: <OperatorsTab />,
    },
  ];

  return (
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* Page Title */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={2} style={{ color: colors.beige[500], marginBottom: 8 }}>
              Dashboard
            </Title>
            <Typography.Text style={{ color: colors.ash_gray[500], fontSize: 16 }}>
              Welcome to your management portal
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
      {loading ? (
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
                <Col xs={24} sm={12} lg={6} key={index}>
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
                    {/* Background decoration */}
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
  );
}
