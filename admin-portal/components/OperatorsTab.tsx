'use client';

import { useState, useEffect, useMemo } from 'react';
import { collection, getDocs, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { Operator, OperatorAction, User } from '@/lib/types';
import { Card, Input, Select, Space, Table, Tag, Typography, Collapse, Skeleton } from 'antd';
import { SearchOutlined, FilterOutlined } from '@ant-design/icons';
import { format } from 'date-fns';
import { colors } from '@/lib/theme';

const { Panel } = Collapse;

export default function OperatorsTab() {
  const [operators, setOperators] = useState<Operator[]>([]);
  const [actions, setActions] = useState<OperatorAction[]>([]);
  const [users, setUsers] = useState<{ [key: string]: User }>({});
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [typeFilter, setTypeFilter] = useState<string>('all');
  const [userFilter, setUserFilter] = useState<string>('all');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      
      // Load users
      const usersSnapshot = await getDocs(collection(db, 'users'));
      const usersMap: { [key: string]: User } = {};
      usersSnapshot.docs.forEach(doc => {
        usersMap[doc.id] = { ...doc.data(), uid: doc.id } as User;
      });
      setUsers(usersMap);

      // Load operators
      const operatorsSnapshot = await getDocs(collection(db, 'operators'));
      const operatorsData = operatorsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Operator));
      setOperators(operatorsData);

      // Load operator actions
      const actionsSnapshot = await getDocs(collection(db, 'operator_actions'));
      const actionsData = actionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as OperatorAction));
      setActions(actionsData);
    } catch (error) {
      console.error('Error loading data:', error);
    } finally {
      setLoading(false);
    }
  };

  const getOperatorActions = (operatorId: string) => {
    return actions.filter(action => {
      // Only show actions that belong to this operator
      if (action.operatorId !== operatorId) return false;
      
      // Only show active actions (treat missing isActive as true for safety)
      if (action.isActive === false) return false;
      
      // Only show actions created by non-disabled users
      const creator = users[action.userId];
      if (creator && creator.disabled === true) return false;
      
      return true;
    });
  };

  const getColorTag = (color: string) => {
    const colorMap: { [key: string]: string } = {
      purple: 'purple',
      orange: 'orange',
      blue: 'blue',
      green: 'green',
      amber: 'gold',
      red: 'red',
      teal: 'cyan',
      indigo: 'geekblue',
    };
    return colorMap[color] || 'default';
  };

  const filteredOperators = operators.filter(op => {
    // Only show active operators (treat missing enabled as true for safety)
    if (op.enabled === false) return false;
    
    // Only show operators created by non-disabled users
    const creator = users[op.userId];
    if (creator && creator.disabled === true) return false;
    
    const matchesSearch = 
      op.name?.toLowerCase().includes(searchTerm.toLowerCase()) ||
      op.code?.toLowerCase().includes(searchTerm.toLowerCase());
    const matchesType = typeFilter === 'all' || op.type === typeFilter;
    const matchesUser = userFilter === 'all' || op.userId === userFilter;
    return matchesSearch && matchesType && matchesUser;
  });

  const uniqueTypes = Array.from(new Set(operators.map(op => op.type).filter(Boolean)));
  const uniqueUsers = Array.from(new Set(
    operators
      .filter(op => {
        // Only include operators created by non-disabled users
        const creator = users[op.userId];
        return creator && creator.disabled !== true;
      })
      .map(op => op.userId)
      .filter(Boolean)
  ));

  const columns = useMemo(() => [
    {
      title: 'Operator',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record: Operator) => (
        <Space direction="vertical" size={0}>
          <Typography.Text strong style={{ color: colors.beige[500] }}>{name}</Typography.Text>
          <Tag color={getColorTag(record.color)} style={{ fontSize: 10 }}>{record.code}</Tag>
        </Space>
      ),
    },
    {
      title: 'Type',
      dataIndex: 'type',
      key: 'type',
      render: (type: string) => <Tag color="blue">{type}</Tag>,
    },
    {
      title: 'Added By',
      dataIndex: 'userId',
      key: 'userId',
      render: (userId: string) => {
        const user = users[userId];
        return (
          <Space direction="vertical" size={0}>
            <Typography.Text style={{ color: colors.beige[500] }}>{user?.name || 'Unknown'}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>{user?.email || userId}</Typography.Text>
          </Space>
        );
      },
    },
    {
      title: 'Actions',
      key: 'actions',
      align: 'center' as const,
      render: (_: any, record: Operator) => {
        const operatorActions = getOperatorActions(record.id || '');
        return (
          <Tag color="purple" style={{ fontWeight: 'bold' }}>
            {operatorActions.length} {operatorActions.length === 1 ? 'action' : 'actions'}
          </Tag>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'enabled',
      key: 'enabled',
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'success' : 'default'}>
          {enabled ? 'Enabled' : 'Disabled'}
        </Tag>
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      render: (d: any) => d ? format(d instanceof Timestamp ? d.toDate() : new Date(d), 'PP') : 'N/A',
    },
  ], [users, actions]);

  const expandedRowRender = (record: Operator) => {
    const operatorActions = getOperatorActions(record.id || '');
    
    if (operatorActions.length === 0) {
      return <Typography.Text type="secondary">No actions configured</Typography.Text>;
    }

    const actionColumns = [
      {
        title: 'Action Name',
        dataIndex: 'name',
        key: 'name',
        render: (name: string) => <Typography.Text strong style={{ color: colors.beige[500] }}>{name}</Typography.Text>,
      },
      {
        title: 'Type',
        dataIndex: 'type',
        key: 'type',
        render: (type: string) => (
          <Tag color={type === 'deposit' ? 'error' : 'success'}>
            {type}
          </Tag>
        ),
      },
      {
        title: 'Action Code',
        dataIndex: 'actionCode',
        key: 'actionCode',
        render: (code: string) => <Typography.Text code>{code}</Typography.Text>,
      },
      {
        title: 'USSD Disabled',
        dataIndex: 'disableUssd',
        key: 'disableUssd',
        render: (disableUssd: boolean) => (
          <Tag color={disableUssd ? 'warning' : 'success'}>
            {disableUssd ? 'Yes' : 'No'}
          </Tag>
        ),
      },
      {
        title: 'Status',
        dataIndex: 'isActive',
        key: 'isActive',
        render: (isActive: boolean) => (
          <Tag color={isActive !== false ? 'success' : 'default'}>
            {isActive !== false ? 'Active' : 'Inactive'}
          </Tag>
        ),
      },
    ];

    return (
      <Table
        rowKey="id"
        dataSource={operatorActions}
        columns={actionColumns}
        pagination={false}
        size="small"
      />
    );
  };

  if (loading) {
    return (
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Operators & Actions</Typography.Title>}
      >
        <Skeleton active paragraph={{ rows: 8 }} />
      </Card>
    );
  }

  return (
    <Space direction="vertical" size="large" style={{ width: '100%' }}>
      <Card
        styles={{ body: { padding: 16 } }}
        title={<Typography.Title level={3} style={{ margin: 0, color: colors.beige[500] }}>Operators & Actions</Typography.Title>}
        extra={
          <Space wrap>
            <Input
              placeholder="Search operators..."
              prefix={<SearchOutlined />}
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              style={{ width: 200 }}
              allowClear
            />
            <Select
              value={typeFilter}
              onChange={(v) => setTypeFilter(v)}
              style={{ width: 150 }}
              options={[
                { value: 'all', label: 'All Types' },
                ...uniqueTypes.map(type => ({ value: type, label: type }))
              ]}
              suffixIcon={<FilterOutlined />}
            />
            <Select
              value={userFilter}
              onChange={(v) => setUserFilter(v)}
              style={{ width: 180 }}
              options={[
                { value: 'all', label: 'All Users' },
                ...uniqueUsers.map(uid => ({ 
                  value: uid, 
                  label: users[uid]?.name || 'Unknown' 
                }))
              ]}
              suffixIcon={<FilterOutlined />}
            />
          </Space>
        }
      >
        <Table
          rowKey="id"
          dataSource={filteredOperators}
          columns={columns}
          expandable={{
            expandedRowRender,
            expandRowByClick: true,
          }}
          pagination={{ pageSize: 10 }}
          scroll={{ x: 1000 }}
        />
        {filteredOperators.length === 0 && (
          <Typography.Text type="secondary">
            {searchTerm || typeFilter !== 'all' || userFilter !== 'all' 
              ? 'No operators match your filters' 
              : 'No operators found'}
          </Typography.Text>
        )}
      </Card>
    </Space>
  );
}