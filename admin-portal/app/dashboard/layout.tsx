'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/authContext';
import { Layout, Avatar, Dropdown, Space, Typography, Button, Spin } from 'antd';
import {
  LogoutOutlined,
  UserOutlined,
  BankOutlined,
  MailOutlined,
  DownOutlined,
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { colors } from '@/lib/theme';
import AntdProvider from '@/components/AntdProvider';

const { Header, Content } = Layout;
const { Text } = Typography;

export default function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const { user, loading, signOut } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && !user) {
      router.push('/login');
    } else if (!loading && user && user.role !== 'admin') {
      // Redirect to appropriate dashboard based on role
      if (user.role === 'dealer') {
        router.push('/dealer/dashboard');
      } else if (user.role === 'agent') {
        router.push('/agent/dashboard');
      } else {
        router.push('/login');
      }
    }
  }, [user, loading, router]);

  const handleSignOut = async () => {
    await signOut();
    router.push('/login');
  };

  const menuItems: MenuProps['items'] = [
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Sign Out',
      danger: true,
      onClick: handleSignOut,
    },
  ];

  if (loading) {
    return (
      <div style={{
        height: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: colors.rich_black[500],
      }}>
        <Space direction="vertical" align="center" size="large">
          <Spin size="large" />
          <Text style={{ color: colors.beige[500] }}>Loading...</Text>
        </Space>
      </div>
    );
  }

  if (!user || user.role !== 'admin') {
    return null;
  }

  return (
    <AntdProvider>
      <Layout style={{ minHeight: '100vh', background: colors.rich_black[500] }}>
        {/* Header */}
        <Header 
        style={{
          position: 'sticky',
          top: 0,
          zIndex: 100,
          width: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 32px',
          background: `linear-gradient(135deg, ${colors.midnight_green[500]}, ${colors.midnight_green[400]})`,
          borderBottom: `1px solid ${colors.air_force_blue[300]}40`,
          boxShadow: '0 4px 20px rgba(1, 22, 30, 0.5)',
          height: 80,
        }}
      >
        {/* Logo and Title */}
        <Space size="middle">
          <div style={{
            width: 48,
            height: 48,
            borderRadius: 12,
            background: `linear-gradient(135deg, ${colors.midnight_green[700]}, ${colors.air_force_blue[600]})`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: `0 4px 12px ${colors.midnight_green[700]}60`,
          }}>
            <BankOutlined style={{ fontSize: 24, color: colors.beige[500] }} />
          </div>
          <div style={{ lineHeight: '1.2', display: 'block' }} className="header-title">
            <div style={{ color: colors.beige[500], fontSize: 16, fontWeight: 'bold', margin: 0, padding: 0 }}>
              Mobile Banking Admin
            </div>
            <div style={{ color: colors.ash_gray[500], fontSize: 12, margin: 0, padding: 0 }}>
              Management Portal
            </div>
          </div>
        </Space>

        {/* User Menu */}
        <Dropdown menu={{ items: menuItems }} trigger={['click']}>
          <Button
            type="text"
            style={{
              height: 50,
              padding: '4px 16px',
              borderRadius: 12,
              border: `1px solid ${colors.air_force_blue[300]}40`,
              background: `${colors.midnight_green[600]}40`,
            }}
          >
            <Space>
              <Avatar 
                style={{ 
                  background: `linear-gradient(135deg, ${colors.air_force_blue[600]}, ${colors.ash_gray[500]})`,
                }} 
                icon={<UserOutlined />}
              />
              <Space direction="vertical" size={0} align="start">
                <Text strong style={{ color: colors.beige[500], fontSize: 14 }}>
                  {user.name}
                </Text>
                <Space size={4}>
                  <MailOutlined style={{ fontSize: 11, color: colors.air_force_blue[600] }} />
                  <Text style={{ color: colors.ash_gray[500], fontSize: 11 }}>
                    {user.email}
                  </Text>
                </Space>
              </Space>
              <DownOutlined style={{ fontSize: 12, color: colors.air_force_blue[600] }} />
            </Space>
          </Button>
        </Dropdown>
      </Header>

      {/* Main Content */}
      <Content
        style={{
          padding: '16px',
          background: colors.rich_black[500],
          minHeight: 'calc(100vh - 80px)',
        }}
        className="dashboard-content"
      >
        {children}
      </Content>
    </Layout>
    <style jsx global>{`
      @media (min-width: 768px) {
        .dashboard-content {
          padding: 24px !important;
        }
      }
      @media (min-width: 1024px) {
        .dashboard-content {
          padding: 32px !important;
        }
      }
      @media (max-width: 640px) {
        .header-title {
          display: none !important;
        }
      }
    `}</style>
    </AntdProvider>
  );
}
