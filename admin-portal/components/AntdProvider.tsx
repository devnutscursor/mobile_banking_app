'use client';

import { ConfigProvider, App as AntdApp } from 'antd';
import { AntdRegistry } from '@ant-design/nextjs-registry';
import theme from '@/lib/theme';

export default function AntdProvider({ children }: { children: React.ReactNode }) {
  return (
    <AntdRegistry>
      <ConfigProvider theme={theme}>
        <AntdApp>
          {children}
        </AntdApp>
      </ConfigProvider>
    </AntdRegistry>
  );
}

