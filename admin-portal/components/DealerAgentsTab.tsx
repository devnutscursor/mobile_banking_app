'use client';

import { useEffect, useState } from 'react';
import { collection, getDocs, query, where, doc, setDoc, Timestamp } from 'firebase/firestore';
import { createUserWithEmailAndPassword, getAuth } from 'firebase/auth';
import { Button, Card, Form, Input, Modal, Space, Table, Typography, App, Tag } from 'antd';
import { PlusOutlined, UserOutlined, MailOutlined, PhoneOutlined } from '@ant-design/icons';
import { db, getSecondaryAuth, signOutSecondary } from '@/lib/firebase';
import { User, License } from '@/lib/types';
import { colors } from '@/lib/theme';
import { useAuth } from '@/lib/authContext';
import { hashPin } from '@/lib/pinHasher';

interface DealerAgentsTabProps {
  onAgentsUpdated?: () => void;
}

export default function DealerAgentsTab({ onAgentsUpdated }: DealerAgentsTabProps) {
  const { message } = App.useApp();
  const { user: currentUser } = useAuth();
  const [agents, setAgents] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form] = Form.useForm();
  const [licenseInfo, setLicenseInfo] = useState<{ maxAgentCount: number | null; currentCount: number } | null>(null);
  const [loadingLicense, setLoadingLicense] = useState(true);

  useEffect(() => {
    loadData();
    loadLicenseInfo();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  const loadLicenseInfo = async () => {
    if (!currentUser) return;
    
    try {
      setLoadingLicense(true);
      
      // Load all licenses and filter client-side to handle both string and array formats
      const allLicensesSnapshot = await getDocs(collection(db, 'licenses'));
      const allLicenses = allLicensesSnapshot.docs.map(doc => ({ ...doc.data(), licenseKey: doc.id } as License));
      
      // Find license assigned to this dealer (handle both string and array formats)
      const assignedLicense = allLicenses.find(license => {
        if (!license.assignedToUserId) return false;
        
        // Handle both array and string formats
        if (Array.isArray(license.assignedToUserId)) {
          return license.assignedToUserId.includes(currentUser.uid);
        }
        return license.assignedToUserId === currentUser.uid;
      });
      
      let maxAgentCount: number | null = null;
      
      if (assignedLicense) {
        maxAgentCount = assignedLicense.maxAgentCount ?? null;
      }

      // Count current agents
      const agentsSnapshot = await getDocs(
        query(
          collection(db, 'users'),
          where('role', '==', 'agent'),
          where('dealerId', '==', currentUser.uid)
        )
      );
      // maxAgentCount includes dealer + agents, so we count dealer (1) + agents
      const currentCount = 1 + agentsSnapshot.size;

      setLicenseInfo({ maxAgentCount, currentCount });
    } catch (error) {
      console.error('Error loading license info:', error);
    } finally {
      setLoadingLicense(false);
    }
  };

  const loadData = async () => {
    if (!currentUser) return;
    
    try {
      setLoading(true);
      const agentsQuery = query(
        collection(db, 'users'),
        where('role', '==', 'agent'),
        where('dealerId', '==', currentUser.uid)
      );
      const agentsSnapshot = await getDocs(agentsQuery);
      const agentsData = agentsSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as User));
      setAgents(agentsData);
    } catch (error) {
      console.error('Error loading agents:', error);
    } finally {
      setLoading(false);
    }
  };

  const canAddMoreAgents = () => {
    if (!licenseInfo) return false;
    if (licenseInfo.maxAgentCount === null) return true; // Unlimited
    return licenseInfo.currentCount < licenseInfo.maxAgentCount;
  };

  // Normalize phone number (remove spaces, dashes, parentheses, plus signs)
  const normalizePhone = (phone: string | null | undefined): string | null => {
    if (!phone) return null;
    return phone.replace(/[\s\-\(\)\+]/g, '');
  };

  // Check if phone number already exists globally
  const checkPhoneExists = async (phone: string): Promise<boolean> => {
    if (!phone) return false;
    const normalizedPhone = normalizePhone(phone);
    if (!normalizedPhone) return false;

    try {
      const usersSnapshot = await getDocs(collection(db, 'users'));
      const existingUser = usersSnapshot.docs.find(doc => {
        const userData = doc.data();
        const userPhone = normalizePhone(userData.phone);
        return userPhone === normalizedPhone;
      });
      return existingUser !== undefined;
    } catch (error) {
      console.error('Error checking phone number:', error);
      return false;
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      
      if (!currentUser) {
        message.error('User not found');
        return;
      }

      // Check for duplicate phone number (phone is now mandatory)
      if (!values.phone || values.phone.trim() === '') {
        message.error('Phone number is required');
        return;
      }
      
      const phoneExists = await checkPhoneExists(values.phone);
      
      if (phoneExists) {
        message.error('This phone number is already registered. Please use a different phone number.');
        return;
      }

      // Re-check license limit with fresh data before creating (prevents race conditions)
      const allLicensesSnapshot = await getDocs(collection(db, 'licenses'));
      const assignedLicense = allLicensesSnapshot.docs
        .map(doc => ({ ...doc.data(), licenseKey: doc.id } as License))
        .find(license => {
          if (!license.assignedToUserId) return false;
          if (Array.isArray(license.assignedToUserId)) {
            return license.assignedToUserId.includes(currentUser.uid);
          }
          return license.assignedToUserId === currentUser.uid;
        });
      
      let maxAgentCount: number | null = null;
      if (assignedLicense) {
        maxAgentCount = assignedLicense.maxAgentCount ?? null;
      }

      // Count current agents with fresh query
      const agentsSnapshot = await getDocs(
        query(
          collection(db, 'users'),
          where('role', '==', 'agent'),
          where('dealerId', '==', currentUser.uid)
        )
      );
      const currentCount = 1 + agentsSnapshot.size; // dealer (1) + agents

      // Check if we can add one more agent
      // We're adding 1 agent, so check: (currentCount + 1) <= maxAgentCount
      if (maxAgentCount !== null && currentCount + 1 > maxAgentCount) {
        const maxAgents = maxAgentCount - 1;
        message.error(`Cannot add more agents. License limit reached (${maxAgents} agents allowed, ${maxAgentCount} total users including dealer).`);
        return;
      }

      // Create agent account
      const secAuth = getSecondaryAuth() || getAuth();
      const userCredential = await createUserWithEmailAndPassword(
        secAuth,
        values.email,
        values.password
      );
      
      // Hash PIN if provided
      let hashedPin: string | undefined;
      if (values.pin) {
        try {
          hashedPin = hashPin(values.pin);
        } catch (error) {
          const err = error as Error;
          message.error(err.message || 'Invalid PIN format');
          return;
        }
      }

      await setDoc(doc(db, 'users', userCredential.user.uid), {
        uid: userCredential.user.uid,
        email: values.email,
        name: values.name,
        phone: values.phone ? normalizePhone(values.phone) : null,
        phonePin: hashedPin,
        role: 'agent',
        dealerId: currentUser.uid, // Assign to current dealer
        active: false, // New users start as not active
        disabled: false,
        virtualCredit: 0,
        totalCreditUsed: 0,
        totalCreditEarned: 0,
        createdAt: Timestamp.now(),
        updatedAt: Timestamp.now(),
        creditUpdatedAt: Timestamp.now(),
      });
      
      message.success('Agent created successfully!');
      await signOutSecondary();
      
      setIsModalOpen(false);
      form.resetFields();
      loadData();
      loadLicenseInfo();
      if (onAgentsUpdated) onAgentsUpdated();
    } catch (error: any) {
      message.error(error.message || 'Failed to create agent');
    }
  };

  const columns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      render: (name: string) => <Typography.Text strong style={{ color: colors.beige[500] }}>{name}</Typography.Text>,
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
    },
    {
      title: 'Phone',
      dataIndex: 'phone',
      key: 'phone',
      render: (phone: string | null) => phone || 'N/A',
    },
    {
      title: 'Status',
      dataIndex: 'active',
      key: 'active',
      render: (active: boolean, record: User) => {
        if (record.disabled) {
          return <Tag color="red">Disabled</Tag>;
        }
        return active ? <Tag color="green">Active</Tag> : <Tag color="orange">Inactive</Tag>;
      },
    },
  ];

  return (
    <Card
      style={{
        background: colors.midnight_green[400],
        border: `1px solid ${colors.air_force_blue[300]}40`,
      }}
    >
      {/* License Info */}
      {licenseInfo && (
        <div
          style={{
            padding: '12px 16px',
            marginBottom: 16,
            borderRadius: 8,
            background: colors.midnight_green[500],
            border: `1px solid ${colors.air_force_blue[300]}40`,
            display: 'flex',
            alignItems: 'center',
            gap: 8,
            transition: 'all 0.3s ease',
            cursor: 'default',
          }}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = colors.midnight_green[600];
            e.currentTarget.style.borderColor = colors.air_force_blue[300];
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = colors.midnight_green[500];
            e.currentTarget.style.borderColor = `${colors.air_force_blue[300]}40`;
          }}
        >
          {licenseInfo.maxAgentCount !== null && licenseInfo.currentCount === licenseInfo.maxAgentCount && (
            <span style={{ color: colors.air_force_blue[600], fontSize: 16 }}>⚠️</span>
          )}
          <Typography.Text style={{ color: colors.beige[500], fontSize: 14 }}>
            {licenseInfo.maxAgentCount === null
              ? `You have ${licenseInfo.currentCount - 1} agent(s). Unlimited users allowed.`
              : (() => {
                  const currentAgents = licenseInfo.currentCount - 1;
                  const maxAgents = licenseInfo.maxAgentCount - 1;
                  const remaining = maxAgents - currentAgents;
                  return remaining > 0 
                    ? `You have ${currentAgents} agent(s). ${remaining} agent(s) remaining.`
                    : `You have ${currentAgents} agent(s). License limit reached.`;
                })()
            }
          </Typography.Text>
        </div>
      )}

      {/* Add Agent Button */}
      <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }}>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => setIsModalOpen(true)}
          disabled={!canAddMoreAgents()}
          style={{
            background: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
            border: 'none',
          }}
        >
          Add Agent
        </Button>
      </Space>

      {/* Agents Table */}
      <Table
        columns={columns}
        dataSource={agents}
        loading={loading || loadingLicense}
        rowKey="uid"
        pagination={{
          pageSize: 50,
          showSizeChanger: true,
          showTotal: (total) => `Total ${total} agents`,
        }}
      />

      {/* Add Agent Modal */}
      <Modal
        title="Add New Agent"
        open={isModalOpen}
        onCancel={() => {
          setIsModalOpen(false);
          form.resetFields();
        }}
        onOk={handleSubmit}
        okText="Create Agent"
        cancelText="Cancel"
        destroyOnHidden
        styles={{
          content: {
            background: colors.midnight_green[400],
          },
        }}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="name"
            label="Name"
            rules={[{ required: true, message: 'Please enter agent name' }]}
          >
            <Input
              prefix={<UserOutlined style={{ color: colors.air_force_blue[600] }} />}
              placeholder="Agent Name"
            />
          </Form.Item>
          
          <Form.Item
            name="email"
            label="Email"
            rules={[
              { required: true, message: 'Please enter email' },
              { type: 'email', message: 'Please enter a valid email' }
            ]}
          >
            <Input
              prefix={<MailOutlined style={{ color: colors.air_force_blue[600] }} />}
              placeholder="agent@example.com"
            />
          </Form.Item>
          
          <Form.Item
            name="password"
            label="Password"
            rules={[
              { required: true, message: 'Please enter password' },
              { min: 6, message: 'Password must be at least 6 characters' }
            ]}
          >
            <Input.Password placeholder="••••••••" />
          </Form.Item>
          
          <Form.Item
            name="phone"
            label="Phone"
            rules={[{ required: true, message: 'Phone number is required' }]}
          >
            <Input
              prefix={<PhoneOutlined style={{ color: colors.air_force_blue[600] }} />}
              placeholder="+1234567890"
            />
          </Form.Item>
          
          <Form.Item
            name="pin"
            label="6-Digit PIN (for phone login)"
            rules={[
              { required: true, message: 'Please enter a 6-digit PIN' },
              { pattern: /^\d{6}$/, message: 'PIN must be exactly 6 digits' }
            ]}
          >
            <Input.Password
              placeholder="Enter 6-digit PIN"
              maxLength={6}
              onKeyPress={(e) => {
                if (!/[0-9]/.test(e.key)) {
                  e.preventDefault();
                }
              }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

