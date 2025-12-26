'use client';

import { useEffect, useMemo, useState } from 'react';
import { collection, getDocs, updateDoc, doc, setDoc, Timestamp, query, where } from 'firebase/firestore';
import { createUserWithEmailAndPassword, getAuth } from 'firebase/auth';
import { Button, Card, Col, Form, Input, InputNumber, Modal, Row, Space, Switch, Table, Tag, Typography, App, Skeleton } from 'antd';
import { PlusOutlined, EditOutlined, DollarOutlined, UserOutlined, PhoneOutlined, MailOutlined, CheckCircleTwoTone, CloseCircleTwoTone } from '@ant-design/icons';
import { db, getSecondaryAuth, signOutSecondary } from '@/lib/firebase';
import { User } from '@/lib/types';
import { format } from 'date-fns';
import { colors } from '@/lib/theme';
import { formatCurrencyWithSymbol } from '@/lib/formatUtils';
import { hashPin } from '@/lib/pinHasher';

interface DealersTabProps {
  onUpdate: () => void;
}

export default function DealersTab({ onUpdate }: DealersTabProps) {
  const { message } = App.useApp();
  const [dealers, setDealers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingDealer, setEditingDealer] = useState<User | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadDealers();
  }, []);

  const loadDealers = async () => {
    try {
      setLoading(true);
      const q = query(collection(db, 'users'), where('role', '==', 'dealer'));
      const snapshot = await getDocs(q);
      const dealersData = snapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as User));
      setDealers(dealersData);
    } catch (error) {
      console.error('Error loading dealers:', error);
    } finally {
      setLoading(false);
    }
  };

  // Normalize phone number (remove spaces, dashes, parentheses, plus signs)
  const normalizePhone = (phone: string | null | undefined): string | null => {
    if (!phone) return null;
    return phone.replace(/[\s\-\(\)\+]/g, '');
  };

  // Check if phone number already exists globally
  const checkPhoneExists = async (phone: string, excludeUserId?: string): Promise<boolean> => {
    if (!phone) return false;
    const normalizedPhone = normalizePhone(phone);
    if (!normalizedPhone) return false;

    try {
      const usersSnapshot = await getDocs(collection(db, 'users'));
      const existingUser = usersSnapshot.docs.find(doc => {
        const userData = doc.data();
        const userPhone = normalizePhone(userData.phone);
        const userId = doc.id;
        // If editing, exclude current user
        if (excludeUserId && userId === excludeUserId) return false;
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
      
      // Check for duplicate phone number if phone is provided
      if (values.phone) {
        const phoneExists = await checkPhoneExists(
          values.phone,
          editingDealer ? editingDealer.uid : undefined
        );
        
        if (phoneExists) {
          message.error('This phone number is already registered. Please use a different phone number.');
          return;
        }
      }
      
      if (editingDealer) {
        const dealerRef = doc(db, 'users', editingDealer.uid);
        await updateDoc(dealerRef, {
          name: values.name,
          phone: values.phone ? normalizePhone(values.phone) : null,
          virtualCredit: values.virtualCredit,
          disabled: values.disabled ?? false,
          updatedAt: Timestamp.now(),
        });
        setTimeout(() => message.success('Dealer updated'), 0);
      } else {
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
          role: 'dealer',
          dealerId: null,
          active: false, // New users start as not active
          disabled: values.disabled ?? false,
          virtualCredit: values.virtualCredit,
          totalCreditUsed: 0,
          totalCreditEarned: 0,
          createdAt: Timestamp.now(),
          updatedAt: Timestamp.now(),
          creditUpdatedAt: Timestamp.now(),
        });

        setTimeout(() => message.success('Dealer created'), 0);
        await signOutSecondary();
      }

      setIsModalOpen(false);
      setEditingDealer(null);
      form.resetFields();
      loadDealers();
      onUpdate();
    } catch (error) {
      const err = error as Error;
      if (err.message) message.error(err.message);
    }
  };

  const handleEdit = (dealer: User) => {
    setEditingDealer(dealer);
    setIsModalOpen(true);
    // Ensure fields are populated after modal mounts
    setTimeout(() => {
      form.setFieldsValue({
        name: dealer.name,
        phone: dealer.phone || '',
        virtualCredit: Number((dealer as any).virtualCredit) || 0,
        disabled: (dealer as any).disabled || false,
      });
    }, 0);
  };

  useEffect(() => {
    if (isModalOpen) {
      if (editingDealer) {
        setTimeout(() => {
          form.setFieldsValue({
            name: editingDealer.name,
            phone: editingDealer.phone || '',
            virtualCredit: Number((editingDealer as any).virtualCredit) || 0,
            disabled: (editingDealer as any).disabled || false,
          });
        }, 0);
      } else {
        form.resetFields();
        form.setFieldsValue({ virtualCredit: 0, disabled: false });
      }
    }
  }, [isModalOpen, editingDealer, form]);

  const columns = useMemo(() => [
    {
      title: 'Dealer',
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
  ], [form]);

  if (loading) {
    return (
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Dealers Management</Typography.Title>}
      >
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Dealers Management</Typography.Title>}
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => {
            setEditingDealer(null);
            setIsModalOpen(true);
          }}>Add Dealer</Button>
        }
      >
        <Table
          rowKey="uid"
          dataSource={dealers}
          columns={columns}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1000 }}
        />
      </Card>

        <Modal
        open={isModalOpen}
        title={editingDealer ? 'Edit Dealer' : 'Add New Dealer'}
        onCancel={() => { setIsModalOpen(false); setEditingDealer(null); }}
        onOk={handleSubmit}
        okText={editingDealer ? 'Update' : 'Create'}
        destroyOnHidden
      >
        <Form form={form} layout="vertical" preserve={false}>
          {!editingDealer && (
            <>
              <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email' }]}> 
                <Input prefix={<MailOutlined />} placeholder="dealer@test.com" />
              </Form.Item>
              <Form.Item name="password" label="Password" rules={[{ required: true, min: 6 }]}> 
                <Input.Password placeholder="Minimum 6 characters" />
              </Form.Item>
            </>
          )}
          <Form.Item name="name" label="Name" rules={[{ required: true }]}> 
            <Input prefix={<UserOutlined />} placeholder="Dealer name" />
          </Form.Item>
          <Form.Item name="phone" label="Phone">
            <Input prefix={<PhoneOutlined />} placeholder="+1234567890" />
          </Form.Item>
          {!editingDealer && (
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
          )}
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
    </Space>
  );
}

