import { NextRequest, NextResponse } from 'next/server';

export async function POST(request: NextRequest) {
  const secretKey = process.env.TURNSTILE_SECRET_KEY;

  if (!secretKey) {
    return NextResponse.json({ success: true, bypass: true });
  }

  try {
    const { token } = await request.json();

    if (!token) {
      return NextResponse.json({ error: 'Turnstile token is required' }, { status: 400 });
    }

    const verifyResponse = await fetch('https://challenges.cloudflare.com/turnstile/v0/siteverify', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        secret: secretKey,
        response: token,
      }),
    });

    const result = await verifyResponse.json();

    if (result.success) {
      return NextResponse.json({ success: true });
    }

    return NextResponse.json({ error: 'Turnstile verification failed' }, { status: 403 });
  } catch (error) {
    const err = error as Error;
    return NextResponse.json(
      { error: err.message || 'Turnstile verification failed' },
      { status: 500 },
    );
  }
}
