'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Script from 'next/script';
import { useAuth } from '@/lib/authContext';
import { Form, Input, Button, Card, Typography, message, Space } from 'antd';
import { MailOutlined, LockOutlined, LoginOutlined, BankOutlined } from '@ant-design/icons';
import { colors } from '@/lib/theme';

const { Title, Text } = Typography;

declare global {
  interface Window {
    turnstile?: {
      render: (
        container: string | HTMLElement,
        options: {
          sitekey: string;
          callback?: (token: string) => void;
          'expired-callback'?: () => void;
          'error-callback'?: () => void;
        },
      ) => string;
      reset: (widgetId: string) => void;
      remove: (widgetId: string) => void;
    };
  }
}

const TURNSTILE_SITE_KEY = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY;
const TURNSTILE_FORCE_LOCAL = process.env.NEXT_PUBLIC_TURNSTILE_FORCE_LOCAL === 'true';

function isLocalhost(): boolean {
  if (typeof window === 'undefined') return false;
  const hostname = window.location.hostname;
  return hostname === 'localhost' || hostname === '127.0.0.1';
}

function isTurnstileRequired(): boolean {
  if (!TURNSTILE_SITE_KEY) return false;
  if (isLocalhost() && !TURNSTILE_FORCE_LOCAL) return false;
  return true;
}

export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const [turnstileToken, setTurnstileToken] = useState<string | null>(null);
  const [turnstileReady, setTurnstileReady] = useState(false);
  const turnstileRef = useRef<HTMLDivElement>(null);
  const widgetIdRef = useRef<string | null>(null);
  const { signIn, user } = useAuth();
  const router = useRouter();
  const [messageApi, contextHolder] = message.useMessage();
  const turnstileRequired = isTurnstileRequired();

  const renderTurnstile = useCallback(() => {
    if (!turnstileRequired || !turnstileRef.current || !window.turnstile || widgetIdRef.current) {
      return;
    }

    widgetIdRef.current = window.turnstile.render(turnstileRef.current, {
      sitekey: TURNSTILE_SITE_KEY!,
      callback: (token: string) => setTurnstileToken(token),
      'expired-callback': () => setTurnstileToken(null),
      'error-callback': () => setTurnstileToken(null),
    });
    setTurnstileReady(true);
  }, [turnstileRequired]);

  useEffect(() => {
    if (window.turnstile) {
      renderTurnstile();
    }
  }, [renderTurnstile]);

  useEffect(() => {
    return () => {
      if (widgetIdRef.current && window.turnstile) {
        window.turnstile.remove(widgetIdRef.current);
        widgetIdRef.current = null;
      }
    };
  }, []);

  // Redirect if already signed in (or after login)
  useEffect(() => {
    if (loading || !user) return;

    const target =
      user.role === 'admin'
        ? '/dashboard'
        : user.role === 'dealer'
          ? '/dealer/dashboard'
          : user.role === 'agent'
            ? '/agent/dashboard'
            : null;

    if (target) {
      router.replace(target);
    }
  }, [user, loading, router]);

  const verifyTurnstile = async (): Promise<boolean> => {
    if (!turnstileRequired) {
      return true;
    }

    if (!turnstileToken) {
      messageApi.error('Please complete the security verification.');
      return false;
    }

    try {
      const response = await fetch('/api/verify-turnstile', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ token: turnstileToken }),
      });

      const result = await response.json();

      if (!response.ok || !result.success) {
        messageApi.error(result.error || 'Security verification failed. Please try again.');
        if (widgetIdRef.current && window.turnstile) {
          window.turnstile.reset(widgetIdRef.current);
        }
        setTurnstileToken(null);
        return false;
      }

      return true;
    } catch {
      messageApi.error('Security verification failed. Please try again.');
      return false;
    }
  };

  const onFinish = async (values: { email: string; password: string }) => {
    setLoading(true);

    try {
      const verified = await verifyTurnstile();
      if (!verified) {
        setLoading(false);
        return;
      }

      await signIn(values.email, values.password);
      messageApi.success('Login successful! Redirecting...');
      // Redirect handled by useEffect when user state updates
    } catch (err) {
      const error = err as Error;
      messageApi.error(error.message || 'Failed to sign in. Please check your credentials.');
      setLoading(false);
    }
  };

  return (
    <>
      {contextHolder}
      {turnstileRequired && (
        <Script
          src="https://challenges.cloudflare.com/turnstile/v0/api.js"
          strategy="afterInteractive"
          onLoad={renderTurnstile}
        />
      )}
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
          className="glass-card"
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
                  autoComplete="email"
                  className="portal-login-field"
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
                  autoComplete="current-password"
                  className="portal-login-field"
                />
              </Form.Item>

              {turnstileRequired && (
                <Form.Item style={{ marginBottom: 16 }}>
                  <div ref={turnstileRef} style={{ display: 'flex', justifyContent: 'center' }} />
                </Form.Item>
              )}

              <Form.Item style={{ marginBottom: 0, marginTop: turnstileRequired ? 16 : 32 }}>
                <Button
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  disabled={turnstileRequired && turnstileReady && !turnstileToken}
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
                🔒 Secured with Firebase Auth{turnstileRequired ? ' & Cloudflare Turnstile' : ''} • Admin, Dealer & Agent Access
              </Text>
            </div>
          </Space>
        </Card>
      </div>
    </>
  );
}
