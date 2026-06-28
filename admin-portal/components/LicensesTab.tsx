'use client';

import { useEffect, useMemo, useState } from 'react';
import { collection, getDocs, updateDoc, doc, setDoc, Timestamp } from 'firebase/firestore';
import { Button, Card, DatePicker, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, App, Skeleton } from 'antd';
import dayjs from 'dayjs';
import { PlusOutlined, EditOutlined, KeyOutlined, CheckCircleTwoTone, CloseCircleTwoTone, ReloadOutlined } from '@ant-design/icons';
import { db } from '@/lib/firebase';
import { License, User } from '@/lib/types';
import { format, addYears, addMonths } from 'date-fns';
import { colors } from '@/lib/theme';

interface LicensesTabProps {
  onUpdate: () => void;
}

export default function LicensesTab({ onUpdate }: LicensesTabProps) {
  const { message } = App.useApp();
  const [licenses, setLicenses] = useState<License[]>([]);
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingLicense, setEditingLicense] = useState<License | null>(null);
  const [form] = Form.useForm();
  const [existingLicenseKeys, setExistingLicenseKeys] = useState<Set<string>>(new Set());

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      
      // Load licenses
      const licensesSnapshot = await getDocs(collection(db, 'licenses'));
      const licensesData = licensesSnapshot.docs.map(doc => ({ ...doc.data() } as License));
      licensesData.sort((a, b) => {
        const at = a.issueDate instanceof Timestamp ? a.issueDate.toMillis() : new Date(a.issueDate).getTime();
        const bt = b.issueDate instanceof Timestamp ? b.issueDate.toMillis() : new Date(b.issueDate).getTime();
        return bt - at;
      });
      setLicenses(licensesData);
      
      // Store existing license keys for duplicate checking
      const keys = new Set(licensesSnapshot.docs.map(doc => doc.id));
      setExistingLicenseKeys(keys);

      // Load all users (dealers and agents)
      const usersSnapshot = await getDocs(collection(db, 'users'));
      const usersData = usersSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as User))
        .filter(u => u.role !== 'admin');
      setUsers(usersData);
    } catch (error) {
      console.error('Error loading data:', error);
    } finally {
      setLoading(false);
    }
  };

  const generateLicenseKey = (): string => {
    // Generate a unique alphanumeric license key: LIC-YYYYMMDD-HHMMSS-XXXX
    const now = new Date();
    const year = now.getFullYear();
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const day = String(now.getDate()).padStart(2, '0');
    const hours = String(now.getHours()).padStart(2, '0');
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const seconds = String(now.getSeconds()).padStart(2, '0');
    
    // Generate random alphanumeric 4-character suffix using crypto RNG
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
    const randomBytes = new Uint8Array(4);
    crypto.getRandomValues(randomBytes);
    let randomSuffix = '';
    for (let i = 0; i < 4; i++) {
      randomSuffix += chars.charAt(randomBytes[i] % chars.length);
    }
    
    return `LIC-${year}${month}${day}-${hours}${minutes}${seconds}-${randomSuffix}`;
  };

  const generateUniqueLicenseKey = async (): Promise<string> => {
    // Load fresh license keys to ensure we have the latest data
    const licensesSnapshot = await getDocs(collection(db, 'licenses'));
    const currentKeys = new Set(licensesSnapshot.docs.map(doc => doc.id));
    
    let key = generateLicenseKey();
    let attempts = 0;
    const maxAttempts = 100;
    
    // Ensure uniqueness by checking against existing keys
    while (currentKeys.has(key) && attempts < maxAttempts) {
      key = generateLicenseKey();
      attempts++;
    }
    
    if (attempts >= maxAttempts) {
      // Fallback: add timestamp with alphanumeric suffix to ensure uniqueness
      const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';
      const randomBytes = new Uint8Array(6);
      crypto.getRandomValues(randomBytes);
      let randomSuffix = '';
      for (let i = 0; i < 6; i++) {
        randomSuffix += chars.charAt(randomBytes[i] % chars.length);
      }
      key = `LIC-${Date.now()}-${randomSuffix}`;
    }
    
    return key;
  };

  const handleGenerateKey = async () => {
    const newKey = await generateUniqueLicenseKey();
    form.setFieldsValue({ licenseKey: newKey });
    message.success('License key generated');
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      
      // Check for duplicate license key when creating new license
      if (!editingLicense) {
        // Check if license key already exists in Firestore
        const licenseDoc = await getDocs(collection(db, 'licenses'));
        const existingKeys = licenseDoc.docs.map(doc => doc.id);
        
        if (existingKeys.includes(values.licenseKey)) {
          message.error('This license key already exists. Please use a different key or generate a new one.');
          return;
        }
      }
      // When editing, keep original assigned user and issueDate; only recompute expiry
      const baseIssueDate: Date = editingLicense
        ? (editingLicense.issueDate instanceof Timestamp ? editingLicense.issueDate.toDate() : new Date(editingLicense.issueDate))
        : (values.issueDate.toDate ? values.issueDate.toDate() : values.issueDate);
      
      // Calculate expiry date based on license type
      const licenseType = values.licenseType || 'annual';
      let expiryDate: Date;
      if (licenseType === 'monthly') {
        expiryDate = addMonths(baseIssueDate, values.expiryMonths || 1);
      } else {
        expiryDate = addYears(baseIssueDate, values.expiryYears || 1);
      }
      
      const licenseData = editingLicense ? {
        licenseKey: editingLicense.licenseKey,
        assignedToUserId: editingLicense.assignedToUserId,
        issueDate: Timestamp.fromDate(baseIssueDate),
        expiryDate: Timestamp.fromDate(expiryDate),
        isActive: editingLicense.isActive,
        maxAgentCount: values.maxAgentCount,
        licenseType: licenseType,
      } : {
        licenseKey: values.licenseKey,
        assignedToUserId: null,
        issueDate: Timestamp.fromDate(baseIssueDate),
        expiryDate: Timestamp.fromDate(expiryDate),
        isActive: values.isActive ?? true,
        maxAgentCount: values.maxAgentCount,
        licenseType: licenseType,
      };

      if (editingLicense) {
        await updateDoc(doc(db, 'licenses', editingLicense.licenseKey), licenseData);
        setTimeout(() => message.success('License updated'), 0);
      } else {
        await setDoc(doc(db, 'licenses', values.licenseKey), licenseData);
        setTimeout(() => message.success('License created'), 0);
      }

      setIsModalOpen(false);
      setEditingLicense(null);
      form.resetFields();
      loadData();
      onUpdate();
    } catch (error) {
      const err = error as Error;
      if (err.message) setTimeout(() => message.error(err.message), 0);
    }
  };

  const handleEdit = (license: License) => {
    setEditingLicense(license);
    setIsModalOpen(true);
    // Ensure form is reset and then populated
    form.resetFields();
  };

  useEffect(() => {
    if (isModalOpen && editingLicense) {
      // Small delay to ensure modal is fully rendered
      setTimeout(() => {
        const issueDate = editingLicense.issueDate instanceof Timestamp 
          ? editingLicense.issueDate.toDate() 
          : new Date(editingLicense.issueDate);
        const expiryDate = editingLicense.expiryDate instanceof Timestamp 
          ? editingLicense.expiryDate.toDate() 
          : new Date(editingLicense.expiryDate);
        
        const licenseType = editingLicense.licenseType || 'annual';
        let expiryValue: number;
        if (licenseType === 'monthly') {
          const monthsDiff = Math.round((expiryDate.getTime() - issueDate.getTime()) / (1000 * 60 * 60 * 24 * 30));
          expiryValue = monthsDiff;
        } else {
          const yearsDiff = Math.round((expiryDate.getTime() - issueDate.getTime()) / (1000 * 60 * 60 * 24 * 365));
          expiryValue = yearsDiff;
        }

        form.setFieldsValue({
          licenseKey: editingLicense.licenseKey,
          issueDate: dayjs(issueDate),
          licenseType: licenseType,
          expiryYears: licenseType === 'annual' ? expiryValue : 1,
          expiryMonths: licenseType === 'monthly' ? expiryValue : 1,
          maxAgentCount: editingLicense.maxAgentCount || null,
          isActive: editingLicense.isActive,
        });
      }, 100);
    }
  }, [isModalOpen, editingLicense, form]);

  const getUserName = (userId: string) => {
    const user = users.find(u => u.uid === userId);
    return user ? `${user.name} (${user.role})` : 'Unknown User';
  };

  const isLicenseExpired = (license: License) => {
    const expiryDate = license.expiryDate instanceof Timestamp 
      ? license.expiryDate.toDate() 
      : new Date(license.expiryDate);
    return expiryDate < new Date();
  };

  const columns = useMemo(() => [
    {
      title: 'License',
      dataIndex: 'licenseKey',
      key: 'licenseKey',
      render: (key: string) => (
        <Space>
          <KeyOutlined />
          <Typography.Text strong style={{ color: colors.beige[500] }}>{key}</Typography.Text>
        </Space>
      ),
    },
    {
      title: 'Assigned To',
      dataIndex: 'assignedToUserId',
      key: 'user',
      render: (userIdOrArray: string | string[] | null) => {
        if (!userIdOrArray) return <Tag color="default">Not Assigned</Tag>;
        
        // Handle both array (new) and single string (legacy) formats
        const userIds = Array.isArray(userIdOrArray) ? userIdOrArray : [userIdOrArray];
        
        if (userIds.length === 0) return <Tag color="default">Not Assigned</Tag>;
        
        return (
          <Space wrap>
            {userIds.map((userId, index) => {
              const u = users.find((x) => x.uid === userId);
              return (
                <Tag key={index} color="blue">
                  {u ? `${u.name} (${u.role})` : userId || 'Unknown'}
                </Tag>
              );
            })}
          </Space>
        );
      },
    },
    {
      title: 'Issue Date',
      dataIndex: 'issueDate',
      key: 'issueDate',
      render: (d: any) => d ? format(d instanceof Timestamp ? d.toDate() : new Date(d), 'PPP') : 'N/A',
    },
    {
      title: 'Expiry Date',
      dataIndex: 'expiryDate',
      key: 'expiryDate',
      render: (d: any) => {
        const date = d instanceof Timestamp ? d.toDate() : new Date(d);
        const expired = date < new Date();
        return (
          <Tag icon={expired ? <CloseCircleTwoTone twoToneColor="#ff4d4f" /> : <CheckCircleTwoTone twoToneColor="#52c41a" />} color={expired ? 'error' : 'success'}>
            {format(date, 'PPP')}
          </Tag>
        );
      },
    },
    {
      title: 'Type',
      dataIndex: 'licenseType',
      key: 'licenseType',
      render: (type: string) => {
        if (!type) return <Tag color="default">Annual</Tag>;
        return type === 'monthly' ? (
          <Tag color="blue">Monthly</Tag>
        ) : (
          <Tag color="green">Annual</Tag>
        );
      },
    },
    {
      title: 'Max Users',
      dataIndex: 'maxAgentCount',
      key: 'maxAgentCount',
      render: (count: number) => count ? count : <Typography.Text type="secondary">Unlimited</Typography.Text>,
    },
    {
      title: 'Status',
      dataIndex: 'isActive',
      key: 'status',
      render: (active: boolean) => active ? (
        <Tag color="success">Active</Tag>
      ) : (
        <Tag color="error">Inactive</Tag>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (_: unknown, record: License) => (
        <Button icon={<EditOutlined />} onClick={() => handleEdit(record)}>Edit</Button>
      ),
    },
  ], [users]);

  if (loading) {
    return (
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Licenses Management</Typography.Title>}
      >
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  return (
    <Card
      styles={{ body: { padding: 16 } }}
      title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Licenses Management</Typography.Title>}
      extra={
        <Button type="primary" icon={<PlusOutlined />} onClick={() => { 
          setEditingLicense(null);
          setIsModalOpen(true);
        }}>Add License</Button>
      }
    >
      <Table rowKey="licenseKey" dataSource={licenses} columns={columns} pagination={{ pageSize: 10 }} scroll={{ x: 1000 }} />

      <Modal
        open={isModalOpen}
        title={editingLicense ? 'Edit License' : 'Add New License'}
        onCancel={() => { 
          setIsModalOpen(false); 
          setEditingLicense(null); 
          form.resetFields(); 
        }}
        onOk={handleSubmit}
        okText={editingLicense ? 'Update' : 'Create'}
        destroyOnHidden
        afterOpenChange={async (open) => {
          if (open && !editingLicense) {
            // Generate and set license key when modal opens
            // Wait a bit for form to be fully initialized
            await new Promise(resolve => setTimeout(resolve, 400));
            
            try {
              const autoGeneratedKey = await generateUniqueLicenseKey();
              
              // Set all form values at once
              form.setFieldsValue({ 
                licenseKey: autoGeneratedKey,
                issueDate: dayjs(),
                licenseType: 'annual',
                expiryYears: 1,
                expiryMonths: 1,
                maxAgentCount: 1,
                isActive: true 
              });
              
              // Force form to update by triggering a re-render
              form.validateFields(['licenseKey']).catch(() => {});
            } catch (error) {
              console.error('Error generating license key:', error);
            }
          }
        }}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item 
            name="licenseKey" 
            label="License Key" 
            rules={[
              { required: true, message: 'Please enter or generate a license key' },
              {
                validator: async (_, value) => {
                  if (!value) return Promise.resolve();
                  if (editingLicense) return Promise.resolve(); // Skip check when editing
                  
                  // Check if license key already exists
                  const licenseDoc = await getDocs(collection(db, 'licenses'));
                  const existingKeys = licenseDoc.docs.map(doc => doc.id);
                  
                  if (existingKeys.includes(value)) {
                    return Promise.reject(new Error('This license key already exists. Please use a different key.'));
                  }
                  return Promise.resolve();
                }
              }
            ]}
          > 
            <Space.Compact style={{ width: '100%' }}>
              <Input 
                placeholder="LIC-YYYYMMDD-HHMMSS-XXXX" 
                disabled={!!editingLicense}
                style={{ flex: 1 }}
              />
              {!editingLicense && (
                <Button 
                  icon={<ReloadOutlined />} 
                  onClick={handleGenerateKey}
                >
                  Generate
                </Button>
              )}
            </Space.Compact>
          </Form.Item>
          {!editingLicense && (
            <Form.Item name="issueDate" label="Issue Date" initialValue={dayjs()} rules={[{ required: true }]}> 
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          )}
          <Form.Item name="licenseType" label="License Type" rules={[{ required: true }]}> 
            <Select options={[
              { label: 'Annual', value: 'annual' },
              { label: 'Monthly', value: 'monthly' }
            ]} />
          </Form.Item>
          <Form.Item 
            noStyle 
            shouldUpdate={(prevValues, currentValues) => prevValues.licenseType !== currentValues.licenseType}
          >
            {({ getFieldValue }) => {
              const licenseType = getFieldValue('licenseType') || 'annual';
              if (licenseType === 'monthly') {
                return (
                  <Form.Item name="expiryMonths" label="Validity (Months)" rules={[{ required: true }]}> 
                    <Select options={[1,2,3,4,5,6,7,8,9,10,11,12].map(n => ({ label: `${n}`, value: n }))} />
                  </Form.Item>
                );
              }
              return (
                <Form.Item name="expiryYears" label="Validity (Years)" rules={[{ required: true }]}> 
                  <Select options={[1,2,3,4,5,6,7,8,9,10].map(n => ({ label: `${n}`, value: n }))} />
                </Form.Item>
              );
            }}
          </Form.Item>
          <Form.Item
            name="maxAgentCount"
            label="Maximum User Count"
            initialValue={1}
            help="Minimum 1. Set to dealer + agents (for example, dealer + 3 agents = 4)."
            rules={[
              { required: true, message: 'Please enter maximum user count' },
              { type: 'number', min: 1, message: 'Minimum value is 1' },
            ]}
          > 
            <InputNumber placeholder="e.g., 5" min={1} style={{ width: '100%' }} />
          </Form.Item>
          {/* Status editing removed as per requirements */}
        </Form>
      </Modal>
    </Card>
  );
}

