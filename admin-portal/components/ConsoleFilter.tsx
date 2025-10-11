'use client';

import { useEffect } from 'react';

export default function ConsoleFilter() {
  useEffect(() => {
    if (process.env.NODE_ENV !== 'development') return;

    const originalWarn = console.warn;
    const originalError = console.error;

    console.warn = (...args: unknown[]) => {
      const first = args[0];
      if (typeof first === 'string' && first.includes('[antd: compatible]')) {
        return; // suppress AntD React compatibility warning in dev overlay
      }
      originalWarn(...args as []);
    };

    console.error = (...args: unknown[]) => {
      const first = args[0];
      if (typeof first === 'string' && first.includes('[antd: compatible]')) {
        return; // suppress if emitted as error in some environments
      }
      originalError(...args as []);
    };

    return () => {
      console.warn = originalWarn;
      console.error = originalError;
    };
  }, []);

  return null;
}






