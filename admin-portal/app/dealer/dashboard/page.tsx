'use client';

import { useState, useEffect, useMemo, useCallback } from 'react';
import { collection, getDocs, query, where, Timestamp } from 'firebase/firestore';
import { db } from '@/lib/firebase';
import { User, License } from '@/lib/types';
import { Card, Row, Col, Statistic, Tabs, Space, Typography, Spin, Button, App } from 'antd';
import {
  ShoppingOutlined,
  UserOutlined,
  DollarOutlined,
  TeamOutlined,
  RiseOutlined,
  DownloadOutlined,
} from '@ant-design/icons';
import { colors } from '@/lib/theme';
import AntdProvider from '@/components/AntdProvider';
import FilteredTransactionsTab from '@/components/FilteredTransactionsTab';
import FilteredCustomersTab from '@/components/FilteredCustomersTab';
import FilteredCommissionsTab from '@/components/FilteredCommissionsTab';
import { useAuth } from '@/lib/authContext';
import DealerAgentsTab from '@/components/DealerAgentsTab';
import { Transaction, Customer, Commission, User as UserType } from '@/lib/types';
import { exportTransactionsToExcel, exportCustomersToExcel, exportCommissionsToExcel } from '@/lib/exportUtils';
import { format } from 'date-fns';
import { formatCurrencyWithSymbol } from '@/lib/formatUtils';

const { Title } = Typography;

export default function DealerDashboardPage() {
  const { user: currentUser } = useAuth();
  const { message } = App.useApp();
  const [activeTab, setActiveTab] = useState('overview');
  const [stats, setStats] = useState({
    totalTransactions: 0,
    totalCustomers: 0,
    totalCommissions: 0,
    totalAgents: 0,
  });
  const [loading, setLoading] = useState(true);
  const [exporting, setExporting] = useState(false);
  const [affiliatedAgentIds, setAffiliatedAgentIds] = useState<string[]>([]);
  const [licenseInfo, setLicenseInfo] = useState<{ maxAgentCount: number | null; currentCount: number } | null>(null);

  useEffect(() => {
    if (currentUser) {
      loadStats();
      loadAffiliatedAgents();
      loadLicenseInfo();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [currentUser]);

  const loadLicenseInfo = useCallback(async () => {
    if (!currentUser) return;
    
    try {
      console.log('[DealerDashboard] Loading license info for dealer:', currentUser.uid, currentUser.name);
      
      // Load all licenses and filter client-side to handle both string and array formats
      const allLicensesSnapshot = await getDocs(collection(db, 'licenses'));
      const allLicenses = allLicensesSnapshot.docs.map(doc => ({ ...doc.data(), licenseKey: doc.id } as License));
      
      console.log('[DealerDashboard] Total licenses found:', allLicenses.length);
      
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
        console.log('[DealerDashboard] Found assigned license:', {
          licenseKey: assignedLicense.licenseKey,
          maxAgentCount: assignedLicense.maxAgentCount,
          assignedToUserId: assignedLicense.assignedToUserId
        });
        maxAgentCount = assignedLicense.maxAgentCount ?? null;
      } else {
        console.log('[DealerDashboard] No license assigned to dealer:', currentUser.uid);
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
      
      console.log('[DealerDashboard] License info:', {
        maxAgentCount,
        currentCount,
        agentCount: agentsSnapshot.size
      });

      setLicenseInfo({ maxAgentCount, currentCount });
    } catch (error) {
      console.error('[DealerDashboard] Error loading license info:', error);
    }
  }, [currentUser]);

  const loadAffiliatedAgents = useCallback(async () => {
    if (!currentUser) {
      console.log('[DealerDashboard] loadAffiliatedAgents: No currentUser');
      return;
    }
    
    try {
      console.log('[DealerDashboard] loadAffiliatedAgents: Loading agents for dealer:', currentUser.uid, currentUser.name);
      const agentsQuery = query(
        collection(db, 'users'),
        where('role', '==', 'agent'),
        where('dealerId', '==', currentUser.uid)
      );
      const agentsSnapshot = await getDocs(agentsQuery);
      const agentIds = agentsSnapshot.docs.map(doc => doc.id);
      console.log('[DealerDashboard] loadAffiliatedAgents: Found', agentIds.length, 'agents:', agentIds);
      setAffiliatedAgentIds(agentIds);
      
      // Reload license info to ensure we have the latest data including maxAgentCount
      // This ensures license info is loaded even if it wasn't loaded initially
      const allLicensesSnapshot = await getDocs(collection(db, 'licenses'));
      const allLicenses = allLicensesSnapshot.docs.map(doc => ({ ...doc.data(), licenseKey: doc.id } as License));
      
      const assignedLicense = allLicenses.find(license => {
        if (!license.assignedToUserId) return false;
        if (Array.isArray(license.assignedToUserId)) {
          return license.assignedToUserId.includes(currentUser.uid);
        }
        return license.assignedToUserId === currentUser.uid;
      });
      
      const maxAgentCount = assignedLicense?.maxAgentCount ?? null;
      const currentCount = 1 + agentIds.length;
      
      setLicenseInfo({ maxAgentCount, currentCount });
    } catch (error) {
      console.error('[DealerDashboard] Error loading affiliated agents:', error);
    }
  }, [currentUser]);

  const loadStats = async () => {
    if (!currentUser) return;
    
    try {
      setLoading(true);
      
      // Get dealer + affiliated agent IDs
      const allUserIds = [currentUser.uid, ...affiliatedAgentIds];
      
      // Load transactions (simplified - just count)
      let transactionCount = 0;
      if (allUserIds.length <= 10) {
        const txnQuery = query(collection(db, 'transactions'), where('userId', 'in', allUserIds));
        const txnSnapshot = await getDocs(txnQuery);
        transactionCount = txnSnapshot.size;
      } else {
        // Batch queries
        const batches: string[][] = [];
        for (let i = 0; i < allUserIds.length; i += 10) {
          batches.push(allUserIds.slice(i, i + 10));
        }
        for (const batch of batches) {
          const txnQuery = query(collection(db, 'transactions'), where('userId', 'in', batch));
          const txnSnapshot = await getDocs(txnQuery);
          transactionCount += txnSnapshot.size;
        }
      }

      // Load customers - check multiple field names (addedBy, userId, createdBy)
      // Some customers might use different field names, so we load all and filter client-side
      let customerCount = 0;
      try {
        // Load all customers and filter client-side to handle different field names
        const allCustomersSnapshot = await getDocs(collection(db, 'customers'));
        const allCustomers = allCustomersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id }));
        
        // Filter customers that match any of the user IDs in any of the possible fields
        const matchingCustomers = allCustomers.filter((c: any) => {
          return allUserIds.includes(c.addedBy) || 
                 allUserIds.includes(c.userId) || 
                 allUserIds.includes(c.createdBy);
        });
        
        customerCount = matchingCustomers.length;
      } catch (error) {
        console.error('[DealerDashboard] Error loading customers count:', error);
        customerCount = 0;
      }

      // Load current month commissions
      const now = new Date();
      const year = now.getFullYear();
      const month = now.getMonth() + 1;
      
      let commissionSum = 0;
      if (allUserIds.length <= 10) {
        const commQuery = query(
          collection(db, 'commissions'),
          where('userId', 'in', allUserIds),
          where('year', '==', year),
          where('month', '==', month)
        );
        const commSnapshot = await getDocs(commQuery);
        commSnapshot.docs.forEach(doc => {
          const data = doc.data();
          commissionSum += data.totalCommission || 0;
        });
      } else {
        const batches: string[][] = [];
        for (let i = 0; i < allUserIds.length; i += 10) {
          batches.push(allUserIds.slice(i, i + 10));
        }
        for (const batch of batches) {
          const commQuery = query(
            collection(db, 'commissions'),
            where('userId', 'in', batch),
            where('year', '==', year),
            where('month', '==', month)
          );
          const commSnapshot = await getDocs(commQuery);
          commSnapshot.docs.forEach(doc => {
            const data = doc.data();
            commissionSum += data.totalCommission || 0;
          });
        }
      }

      setStats({
        totalTransactions: transactionCount,
        totalCustomers: customerCount,
        totalCommissions: commissionSum,
        totalAgents: affiliatedAgentIds.length,
      });
    } catch (error) {
      console.error('Error loading stats:', error);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    if (affiliatedAgentIds.length >= 0) {
      loadStats();
      // Reload license info when agents change to ensure we have fresh data
      loadLicenseInfo();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [affiliatedAgentIds]);

  const statCards = useMemo(() => {
    const cards = [
      {
        title: 'Total Transactions',
        value: stats.totalTransactions,
        icon: <ShoppingOutlined />,
        gradient: `linear-gradient(135deg, ${colors.midnight_green[600]}, ${colors.air_force_blue[600]})`,
      },
      {
        title: 'Total Customers',
        value: stats.totalCustomers,
        icon: <UserOutlined />,
        gradient: `linear-gradient(135deg, ${colors.air_force_blue[600]}, ${colors.midnight_green[700]})`,
      },
      {
        title: 'Total Agents',
        value: licenseInfo && licenseInfo.maxAgentCount !== null 
          ? `${stats.totalAgents} / ${licenseInfo.maxAgentCount - 1}`
          : stats.totalAgents,
        icon: <TeamOutlined />,
        gradient: `linear-gradient(135deg, ${colors.midnight_green[700]}, ${colors.air_force_blue[700]})`,
      },
      {
        title: 'This Month Commissions',
        value: formatCurrencyWithSymbol(stats.totalCommissions),
        icon: <DollarOutlined />,
        gradient: `linear-gradient(135deg, ${colors.air_force_blue[600]}, ${colors.midnight_green[700]})`,
      },
    ];
    return cards;
  }, [stats, licenseInfo]);

  // Get all user IDs for filtering (dealer + affiliated agents)
  const allUserIdsForFiltering = useMemo(() => {
    if (!currentUser) {
      console.log('[DealerDashboard] allUserIdsForFiltering: [] (no currentUser)');
      return [];
    }
    const userIds = [currentUser.uid, ...affiliatedAgentIds];
    console.log('[DealerDashboard] allUserIdsForFiltering computed:', {
      dealerId: currentUser.uid,
      dealerName: currentUser.name,
      affiliatedAgentIds,
      totalUserIds: userIds.length,
      userIds
    });
    return userIds;
  }, [currentUser, affiliatedAgentIds]);

  const handleExportAll = async () => {
    if (!currentUser) return;
    
    try {
      setExporting(true);
      message.loading({ content: 'Loading data for export...', key: 'export', duration: 0 });

      const allUserIds = [currentUser.uid, ...affiliatedAgentIds];

      // Load all related data
      const [transactionsSnapshot, customersSnapshot, commissionsSnapshot, usersSnapshot] = await Promise.all([
        getDocs(query(collection(db, 'transactions'), where('userId', 'in', allUserIds.length > 10 ? allUserIds.slice(0, 10) : allUserIds))),
        getDocs(query(collection(db, 'customers'), where('addedBy', 'in', allUserIds.length > 10 ? allUserIds.slice(0, 10) : allUserIds))),
        getDocs(query(collection(db, 'commissions'), where('userId', 'in', allUserIds.length > 10 ? allUserIds.slice(0, 10) : allUserIds))),
        getDocs(collection(db, 'users')),
      ]);

      // Handle batch queries if more than 10 users
      let transactions: Transaction[] = [];
      let customers: Customer[] = [];
      let commissions: Commission[] = [];

      if (allUserIds.length <= 10) {
        transactions = transactionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction));
        customers = customersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));
        commissions = commissionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Commission));
      } else {
        // Batch queries for transactions
        for (let i = 0; i < allUserIds.length; i += 10) {
          const batch = allUserIds.slice(i, i + 10);
          const batchSnapshot = await getDocs(query(collection(db, 'transactions'), where('userId', 'in', batch)));
          transactions.push(...batchSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Transaction)));
        }
        // Similar for customers and commissions...
      }

      const users = usersSnapshot.docs.map(doc => ({ ...doc.data(), uid: doc.id } as UserType));
      const usersMap: { [key: string]: UserType } = {};
      users.forEach(u => { usersMap[u.uid] = u; });

      // Load related data for transactions
      const [operatorsSnapshot, actionsSnapshot, allCustomersSnapshot] = await Promise.all([
        getDocs(collection(db, 'operators')),
        getDocs(collection(db, 'operator_actions')),
        getDocs(collection(db, 'customers')),
      ]);

      const operators = operatorsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as any));
      const actions = actionsSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as any));
      const allCustomers = allCustomersSnapshot.docs.map(doc => ({ ...doc.data(), id: doc.id } as Customer));

      const operatorsMap: { [key: string]: any } = {};
      operators.forEach(op => { operatorsMap[op.id || ''] = op; });

      const actionsMap: { [key: string]: any } = {};
      actions.forEach(action => { actionsMap[action.id || ''] = action; });

      const customersMap: { [key: string]: Customer } = {};
      allCustomers.forEach(c => { customersMap[c.id || ''] = c; });

      message.loading({ content: 'Generating Excel files...', key: 'export' });

      const timestamp = format(new Date(), 'yyyy-MM-dd_HH-mm-ss');

      // Export each category
      exportTransactionsToExcel(transactions, usersMap, customersMap, operatorsMap, actionsMap, `dealer_transactions_${timestamp}.xlsx`);
      exportCustomersToExcel(customers, usersMap, `dealer_customers_${timestamp}.xlsx`);
      exportCommissionsToExcel(commissions, `dealer_commissions_${timestamp}.xlsx`);

      message.success({ content: 'All data exported successfully!', key: 'export', duration: 3 });
    } catch (error) {
      console.error('Export error:', error);
      message.error({ content: 'Failed to export data. Please try again.', key: 'export', duration: 3 });
    } finally {
      setExporting(false);
    }
  };

  const tabItems = useMemo(() => [
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
      key: 'transactions',
      label: (
        <Space>
          <ShoppingOutlined />
          <span>Transactions</span>
        </Space>
      ),
      children: <FilteredTransactionsTab allowedUserIds={allUserIdsForFiltering} showExport={true} />,
    },
    {
      key: 'customers',
      label: (
        <Space>
          <UserOutlined />
          <span>Customers</span>
        </Space>
      ),
      children: <FilteredCustomersTab allowedUserIds={allUserIdsForFiltering} showExport={true} />,
    },
    {
      key: 'commissions',
      label: (
        <Space>
          <DollarOutlined />
          <span>Commissions</span>
        </Space>
      ),
      children: <FilteredCommissionsTab allowedUserIds={allUserIdsForFiltering} showExport={true} />,
    },
    {
      key: 'agents',
      label: (
        <Space>
          <TeamOutlined />
          <span>Manage Agents</span>
        </Space>
      ),
      children: <DealerAgentsTab onAgentsUpdated={loadAffiliatedAgents} />,
    },
  ], [allUserIdsForFiltering]);

  if (!currentUser) {
    return null;
  }

  return (
    <AntdProvider>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* Page Title */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <Title level={2} style={{ color: colors.beige[500], marginBottom: 8 }}>
              Dealer Dashboard
            </Title>
            <Typography.Text style={{ color: colors.ash_gray[500], fontSize: 16 }}>
              Welcome, {currentUser.name}
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
        {loading && activeTab === 'overview' ? (
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
                        height: '100%',
                        display: 'flex',
                        flexDirection: 'column',
                      }}
                      styles={{ body: { padding: 24, flex: 1, display: 'flex', flexDirection: 'column' } }}
                    >
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
                      
                      <Space direction="vertical" size="small" style={{ width: '100%', position: 'relative', flex: 1, justifyContent: 'space-between', display: 'flex' }}>
                        <div>
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
                        </div>
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

