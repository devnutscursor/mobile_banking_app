'use client';

import { useState, useEffect } from 'react';
import { collection, getDocs, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { User, Transaction, DashboardStats } from '@/lib/types';
import { Card, Row, Col, Statistic, Tabs, Spin, Space, Typography } from 'antd';
import {
  UserOutlined,
  TeamOutlined,
  DollarOutlined,
  ShoppingOutlined,
  KeyOutlined,
  RiseOutlined,
  CheckCircleOutlined,
  ShopOutlined,
} from '@ant-design/icons';
import { colors } from '@/lib/theme';
import AntdProvider from '@/components/AntdProvider';
import DealersTab from '@/components/DealersTab';
import AgentsTab from '@/components/AgentsTab';
import LicensesTab from '@/components/LicensesTab';
import TransactionsTab from '@/components/TransactionsTab';
import CustomersTab from '@/components/CustomersTab';
import OperatorsTab from '@/components/OperatorsTab';

const { Title } = Typography;

export default function DashboardPage() {
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

      const totalRevenue = transactions
        .filter(t => t.status === 'successful')
        .reduce((sum, t) => sum + t.amount, 0);

      const licensesSnapshot = await getDocs(collection(db, 'licenses'));
      const activeLicenses = licensesSnapshot.docs.filter(doc => doc.data().isActive).length;

      setStats({
        totalUsers: users.length,
        totalDealers: dealers.length,
        totalAgents: agents.length,
        totalTransactions: transactions.length,
        totalRevenue,
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

  const statCards = [
    {
      title: 'Total Users',
      value: stats.totalUsers,
      icon: <UserOutlined />,
      gradient: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
      color: colors.rich_black[800],
    },
    {
      title: 'Dealers',
      value: stats.totalDealers,
      icon: <ShopOutlined />,
      gradient: `linear-gradient(135deg, ${colors.air_force_blue[600]}, ${colors.midnight_green[700]})`,
      color: colors.air_force_blue[800],
    },
    {
      title: 'Agents',
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
      title: 'Total Revenue',
      value: `$${stats.totalRevenue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
      icon: <DollarOutlined />,
      gradient: `linear-gradient(135deg, ${colors.midnight_green[700]}, ${colors.air_force_blue[700]})`,
      color: colors.midnight_green[800],
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
    <AntdProvider>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* Page Title */}
        <div>
          <Title level={2} style={{ color: colors.beige[500], marginBottom: 8 }}>
            Dashboard
          </Title>
          <Typography.Text style={{ color: colors.ash_gray[500], fontSize: 16 }}>
            Welcome to your management portal
          </Typography.Text>
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
    </AntdProvider>
  );
}
