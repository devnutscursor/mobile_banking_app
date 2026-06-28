'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/authContext';

function getDashboardPath(role: string): string {
  if (role === 'admin') return '/dashboard';
  if (role === 'dealer') return '/dealer/dashboard';
  if (role === 'agent') return '/agent/dashboard';
  return '/login';
}

export default function Home() {
  const { user, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;
    router.replace(user ? getDashboardPath(user.role) : '/login');
  }, [user, loading, router]);

  return (
    <div className="flex items-center justify-center min-h-screen">
      <div className="animate-spin rounded-full h-12 w-12 border-t-2 border-b-2 border-purple-500"></div>
    </div>
  );
}
