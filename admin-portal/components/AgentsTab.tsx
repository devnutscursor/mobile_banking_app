'use client';

import { useEffect, useMemo, useState } from 'react';
import { collection, getDocs, updateDoc, doc, setDoc, Timestamp, query, where } from 'firebase/firestore';
import { License } from '@/lib/types';
import { createUserWithEmailAndPassword, getAuth } from 'firebase/auth';
import { Button, Card, Form, Input, InputNumber, Modal, Select, Space, Switch, Table, Tag, Typography, App, Skeleton } from 'antd';
import { PlusOutlined, EditOutlined, DollarOutlined, UserOutlined, PhoneOutlined, MailOutlined, ApartmentOutlined, CheckCircleTwoTone, CloseCircleTwoTone } from '@ant-design/icons';
import { db, getSecondaryAuth, signOutSecondary } from '@/lib/firebase';
import { User } from '@/lib/types';
import { format } from 'date-fns';
import { colors } from '@/lib/theme';
import { formatCurrencyWithSymbol } from '@/lib/formatUtils';

interface AgentsTabProps {
  onUpdate: () => void;
}

export default function AgentsTab({ onUpdate }: AgentsTabProps) {
  const { message } = App.useApp();
  const [agents, setAgents] = useState<User[]>([]);
  const [dealers, setDealers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingAgent, setEditingAgent] = useState<User | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      
      // Load agents
      const agentsQuery = query(collection(db, 'users'), where('role', '==', 'agent'));
      const agentsSnapshot = await getDocs(agentsQuery);
      const agentsData = agentsSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as User));
      setAgents(agentsData);

      // Load dealers for dropdown
      const dealersQuery = query(collection(db, 'users'), where('role', '==', 'dealer'));
      const dealersSnapshot = await getDocs(dealersQuery);
      const dealersData = dealersSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as User));
      setDealers(dealersData);
    } catch (error) {
      console.error('Error loading data:', error);
    } finally {
      setLoading(false);
    }
  };

  const checkLicenseLimit = async (dealerId: string | null, isNewAgent: boolean, currentAgentDealerId?: string | null): Promise<boolean> => {
    if (!dealerId) return true; // No dealer assigned, no limit check needed
    
    try {
      // Find license assigned to this dealer
      const licensesSnapshot = await getDocs(
        query(collection(db, 'licenses'), where('assignedToUserId', '==', dealerId))
      );
      
      let maxAgentCount: number | null = null;
      
      if (!licensesSnapshot.empty) {
        const licenseDoc = licensesSnapshot.docs[0];
        const licenseData = licenseDoc.data() as License;
        maxAgentCount = licenseData.maxAgentCount ?? null;
      }

      if (maxAgentCount === null) return true; // Unlimited

      // Count current agents for this dealer
      const agentsSnapshot = await getDocs(
        query(
          collection(db, 'users'),
          where('role', '==', 'agent'),
          where('dealerId', '==', dealerId)
        )
      );
      
      let currentCount = 1 + agentsSnapshot.size; // dealer (1) + agents
      
      // If editing and changing dealer, adjust counts
      if (!isNewAgent && currentAgentDealerId !== dealerId) {
        // If moving from another dealer to this dealer, we need to check the new dealer's limit
        // Current count doesn't include the agent being moved (it's still assigned to old dealer)
        // We're adding 1 agent (the one being moved), so check: (currentCount + 1) <= maxAgentCount
        if (currentCount + 1 > maxAgentCount) {
          const maxAgents = maxAgentCount - 1;
          message.error(`Cannot assign agent to this dealer. License limit reached (${maxAgents} agents allowed, ${maxAgentCount} total users including dealer).`);
          return false;
        }
      } else if (isNewAgent) {
        // Creating new agent - check if we can add one more
        // Current count includes dealer + existing agents, adding 1 more agent
        if (currentCount + 1 > maxAgentCount) {
          const maxAgents = maxAgentCount - 1;
          message.error(`Cannot create agent for this dealer. License limit reached (${maxAgents} agents allowed, ${maxAgentCount} total users including dealer).`);
          return false;
        }
      }
      // If editing same dealer, no limit check needed (not adding new agent)
      
      return true;
    } catch (error) {
      console.error('Error checking license limit:', error);
      return true; // Allow on error to not block operations
    }
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      
      // Check license limit if assigning to a dealer
      if (values.dealerId) {
        const canProceed = await checkLicenseLimit(
          values.dealerId, 
          !editingAgent,
          editingAgent?.dealerId
        );
        if (!canProceed) {
          return; // Error message already shown
        }
      }
      
      if (editingAgent) {
        const agentRef = doc(db, 'users', editingAgent.uid);
        await updateDoc(agentRef, {
          name: values.name,
          phone: values.phone,
          dealerId: values.dealerId || null,
          virtualCredit: values.virtualCredit,
          disabled: values.disabled ?? false,
          updatedAt: Timestamp.now(),
        });
        setTimeout(() => message.success('Agent updated'), 0);
      } else {
        const secAuth = getSecondaryAuth() || getAuth();
        const userCredential = await createUserWithEmailAndPassword(
          secAuth,
          values.email,
          values.password
        );

        await setDoc(doc(db, 'users', userCredential.user.uid), {
          uid: userCredential.user.uid,
          email: values.email,
          name: values.name,
          phone: values.phone,
          role: 'agent',
          dealerId: values.dealerId || null,
          active: false, // New users start as not active
          disabled: values.disabled ?? false,
          virtualCredit: values.virtualCredit,
          totalCreditUsed: 0,
          totalCreditEarned: 0,
          createdAt: Timestamp.now(),
          updatedAt: Timestamp.now(),
          creditUpdatedAt: Timestamp.now(),
        });

        // If phone provided, link it to the new Auth user for phone+OTP login
        if (values.phone) {
          try {
            await fetch('/api/link-phone', {
              method: 'POST',
              headers: { 'Content-Type': 'application/json' },
              body: JSON.stringify({
                uid: userCredential.user.uid,
                phone: values.phone,
              }),
            });
          } catch (e) {
            console.error('Failed to link agent phone in Auth:', e);
            setTimeout(() => message.warning('Agent created, but phone could not be linked for OTP login.'), 0);
          }
        }

        setTimeout(() => message.success('Agent created'), 0);
        await signOutSecondary();
      }

      setIsModalOpen(false);
      setEditingAgent(null);
      form.resetFields();
      loadData();
      onUpdate();
    } catch (error) {
      const err = error as Error;
      if (err.message) message.error(err.message);
    }
  };

  const handleEdit = (agent: User) => {
    setEditingAgent(agent);
    setIsModalOpen(true);
    setTimeout(() => {
      form.setFieldsValue({
        name: agent.name,
        phone: agent.phone || '',
        dealerId: agent.dealerId || undefined,
        virtualCredit: Number((agent as any).virtualCredit) || 0,
        disabled: (agent as any).disabled || false,
      });
    }, 0);
  };

  useEffect(() => {
    if (isModalOpen) {
      if (editingAgent) {
        setTimeout(() => {
          form.setFieldsValue({
            name: editingAgent.name,
            phone: editingAgent.phone || '',
            dealerId: editingAgent.dealerId || undefined,
            virtualCredit: Number((editingAgent as any).virtualCredit) || 0,
            disabled: (editingAgent as any).disabled || false,
          });
        }, 0);
      } else {
        form.resetFields();
          form.setFieldsValue({ virtualCredit: 0, disabled: false, dealerId: null });
      }
    }
  }, [isModalOpen, editingAgent, form]);

  const getDealerName = (dealerId: string | null) => {
    if (!dealerId) return 'No Dealer';
    const dealer = dealers.find(d => d.uid === dealerId);
    return dealer ? dealer.name : 'Unknown Dealer';
  };

  const columns = useMemo(() => [
    {
      title: 'Agent',
      dataIndex: 'name',
      key: 'name',
      render: (_: unknown, record: User) => (
        <Space direction="vertical" size={0}>
          <Space>
            <UserOutlined style={{ color: colors.beige[500] }} />
            <Typography.Text strong style={{ color: colors.beige[500] }}>{record.name}</Typography.Text>
          </Space>
          <Space size="small">
            <MailOutlined style={{ color: colors.air_force_blue[600] }} />
            <Typography.Text style={{ color: colors.ash_gray[500] }}>{record.email}</Typography.Text>
          </Space>
          {record.phone && (
            <Space size="small">
              <PhoneOutlined style={{ color: colors.air_force_blue[600] }} />
              <Typography.Text style={{ color: colors.ash_gray[500] }}>{record.phone}</Typography.Text>
            </Space>
          )}
        </Space>
      ),
    },
    {
      title: 'Dealer',
      dataIndex: 'dealerId',
      key: 'dealer',
      render: (dealerId: string) => {
        const dealer = dealers.find(d => d.uid === dealerId);
        return dealer ? (
          <Space>
            <ApartmentOutlined />
            <Typography.Text>{dealer.name}</Typography.Text>
          </Space>
        ) : 'No Dealer';
      },
    },
    {
      title: 'Virtual Credit',
      dataIndex: 'virtualCredit',
      key: 'virtualCredit',
      align: 'right' as const,
      render: (v: number) => (
        <Space>
          <DollarOutlined />
          <Typography.Text style={{ color: colors.beige[500] }}>{formatCurrencyWithSymbol(v || 0)}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Used',
      dataIndex: 'totalCreditUsed',
      key: 'used',
      align: 'right' as const,
      render: (v: number) => <Typography.Text>{formatCurrencyWithSymbol(v || 0)}</Typography.Text>,
    },
    {
      title: 'Earned',
      dataIndex: 'totalCreditEarned',
      key: 'earned',
      align: 'right' as const,
      render: (v: number) => <Typography.Text>{formatCurrencyWithSymbol(v || 0)}</Typography.Text>,
    },
    {
      title: 'Status',
      key: 'status',
      render: (_: unknown, record: any) => {
        if (record?.disabled) {
          return <Tag icon={<CloseCircleTwoTone twoToneColor="#ff4d4f" />} color="error">Disabled</Tag>;
        }
        const active: boolean = record?.active === true;
        return active ? (
          <Tag icon={<CheckCircleTwoTone twoToneColor="#52c41a" />} color="success">Active</Tag>
        ) : (
          <Tag icon={<CloseCircleTwoTone twoToneColor="#ff4d4f" />} color="error">Not Active</Tag>
        );
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: User) => (
        <Button icon={<EditOutlined />} onClick={() => handleEdit(record)}>Edit</Button>
      ),
    },
  ], [dealers]);

  if (loading) {
    return (
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Agents Management</Typography.Title>}
      >
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  return (
    <Card
      styles={{ body: { padding: 16 } }}
      title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Agents Management</Typography.Title>}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { setEditingAgent(null); setIsModalOpen(true); }}>Add Agent</Button>
      }
    >
      <Table rowKey="uid" dataSource={agents} columns={columns} pagination={{ pageSize: 10 }} scroll={{ x: 1000 }} />

      <Modal
        open={isModalOpen}
        title={editingAgent ? 'Edit Agent' : 'Add New Agent'}
        onCancel={() => { setIsModalOpen(false); setEditingAgent(null); }}
        onOk={handleSubmit}
        okText={editingAgent ? 'Update' : 'Create'}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="email" label="Email" rules={[{ required: !editingAgent, type: 'email' }]}>
            <Input prefix={<MailOutlined />} disabled={!!editingAgent} placeholder="agent@test.com" />
          </Form.Item>
          {!editingAgent && (
            <Form.Item name="password" label="Password" rules={[{ required: true, min: 6 }]}>
              <Input.Password placeholder="Minimum 6 characters" />
            </Form.Item>
          )}
          <Form.Item name="name" label="Name" rules={[{ required: true }]}>
            <Input prefix={<UserOutlined />} placeholder="Agent name" />
          </Form.Item>
          <Form.Item name="phone" label="Phone">
            <Input prefix={<PhoneOutlined />} placeholder="+1234567890" />
          </Form.Item>
          <Form.Item name="dealerId" label="Assign to Dealer (Optional)"> 
            <Select 
              placeholder="Select a dealer (leave empty for independent agent)" 
              allowClear
              options={dealers.map(d => ({ label: `${d.name} (${d.email})`, value: d.uid }))} 
            />
          </Form.Item>
          <Form.Item name="virtualCredit" label="Virtual Credit" rules={[{ type: 'number', min: 0 }]}> 
            <InputNumber 
              addonBefore="$" 
              style={{ width: '100%' }}
              formatter={(value) => value !== null && value !== undefined ? value.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',') : ''}
              parser={(value) => value ? value.replace(/,/g, '') : ''}
            />
          </Form.Item>
          <Form.Item name="disabled" label="Disable User"> 
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}

