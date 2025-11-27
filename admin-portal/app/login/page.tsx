'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/authContext';
import { Form, Input, Button, Card, Typography, message, Space } from 'antd';
import { MailOutlined, LockOutlined, LoginOutlined, BankOutlined } from '@ant-design/icons';
import { colors } from '@/lib/theme';
import AntdProvider from '@/components/AntdProvider';

const { Title, Text } = Typography;

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const { signIn, user } = useAuth();
  const router = useRouter();
  const [messageApi, contextHolder] = message.useMessage();

  // Redirect after successful login based on user role
  useEffect(() => {
    if (user) {
      messageApi.success('Login successful! Redirecting...');
      setTimeout(() => {
        if (user.role === 'admin') {
          router.push('/dashboard');
        } else if (user.role === 'dealer') {
          router.push('/dealer/dashboard');
        } else if (user.role === 'agent') {
          router.push('/agent/dashboard');
        } else {
          router.push('/dashboard');
        }
      }, 300);
    }
  }, [user, router, messageApi]);

  const onFinish = async (values: { email: string; password: string }) => {
    setLoading(true);

    try {
      await signIn(values.email, values.password);
      // User state will update via useEffect, which will handle redirect
    } catch (err) {
      const error = err as Error;
      messageApi.error(error.message || 'Failed to sign in. Please check your credentials.');
      setLoading(false);
    }
  };

  return (
    <AntdProvider>
      {contextHolder}
      <div 
        className="min-h-screen flex items-center justify-center p-4 animated-gradient"
        style={{
          position: 'relative',
          overflow: 'hidden',
        }}
      >
        {/* Decorative circles */}
        <div style={{
          position: 'absolute',
          top: '-10%',
          right: '-5%',
          width: '400px',
          height: '400px',
          borderRadius: '50%',
          background: `radial-gradient(circle, ${colors.midnight_green[600]}40, transparent)`,
          filter: 'blur(60px)',
        }} />
        <div style={{
          position: 'absolute',
          bottom: '-10%',
          left: '-5%',
          width: '400px',
          height: '400px',
          borderRadius: '50%',
          background: `radial-gradient(circle, ${colors.air_force_blue[600]}40, transparent)`,
          filter: 'blur(60px)',
        }} />

        <Card
          className="glass-card hover-lift"
          style={{
            width: '100%',
            maxWidth: 450,
            border: `1px solid ${colors.air_force_blue[300]}40`,
            boxShadow: '0 20px 60px rgba(1, 22, 30, 0.7)',
            position: 'relative',
            zIndex: 1,
          }}
        >
          {/* Header */}
          <Space direction="vertical" size="large" style={{ width: '100%', textAlign: 'center' }}>
            <div>
              <div style={{
                width: 80,
                height: 80,
                margin: '0 auto 20px',
                borderRadius: '50%',
                background: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: `0 10px 30px ${colors.midnight_green[600]}60`,
              }}>
                <BankOutlined style={{ fontSize: 40, color: colors.beige[500] }} />
              </div>
              
              <Title level={2} style={{ color: colors.beige[500], marginBottom: 8 }}>
                Admin Portal
              </Title>
              <Text style={{ color: colors.ash_gray[500], fontSize: 16 }}>
                Mobile Banking Management System
              </Text>
            </div>

            {/* Login Form */}
            <Form
              name="login"
              onFinish={onFinish}
              autoComplete="off"
              layout="vertical"
              requiredMark={false}
              style={{ marginTop: 32 }}
            >
              <Form.Item
                name="email"
                rules={[
                  { required: true, message: 'Please input your email!' },
                  { type: 'email', message: 'Please enter a valid email!' }
                ]}
              >
                <Input
                  prefix={<MailOutlined style={{ color: colors.air_force_blue[600] }} />}
                  placeholder="admin@test.com"
                  size="large"
                  style={{
                    borderRadius: 12,
                    background: colors.rich_black[400],
                    border: `1px solid ${colors.air_force_blue[300]}`,
                    color: colors.beige[500],
                  }}
                />
              </Form.Item>

              <Form.Item
                name="password"
                rules={[{ required: true, message: 'Please input your password!' }]}
              >
                <Input.Password
                  prefix={<LockOutlined style={{ color: colors.air_force_blue[600] }} />}
                  placeholder="••••••••"
                  size="large"
                  style={{
                    borderRadius: 12,
                    background: colors.rich_black[400],
                    border: `1px solid ${colors.air_force_blue[300]}`,
                    color: colors.beige[500],
                  }}
                />
              </Form.Item>

              <Form.Item style={{ marginBottom: 0, marginTop: 32 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  size="large"
                  icon={<LoginOutlined />}
                  block
                  style={{
                    height: 50,
                    borderRadius: 12,
                    fontSize: 16,
                    fontWeight: 600,
                    background: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
                    border: 'none',
                    boxShadow: `0 6px 20px ${colors.midnight_green[600]}50`,
                  }}
                >
                  {loading ? 'Signing in...' : 'Sign In'}
                </Button>
              </Form.Item>
            </Form>

            {/* Footer */}
            <div style={{ marginTop: 32, paddingTop: 24, borderTop: `1px solid ${colors.air_force_blue[300]}40` }}>
              <Text style={{ color: colors.air_force_blue[600], fontSize: 12 }}>
                🔒 Secured with Firebase Auth • Admin, Dealer & Agent Access
              </Text>
            </div>
          </Space>
        </Card>
      </div>
    </AntdProvider>
  );
}
