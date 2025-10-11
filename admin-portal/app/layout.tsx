import type { Metadata } from "next";
import { Inter } from "next/font/google";
import "./globals.css";
import { AuthProvider } from "@/lib/authContext";
import AntdProvider from "@/components/AntdProvider";
import ConsoleFilter from "@/components/ConsoleFilter";

const inter = Inter({ subsets: ["latin"] });

export const metadata: Metadata = {
  title: "Mobile Banking - Admin Portal",
  description: "Modern admin dashboard for managing dealers, agents, and licenses",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className={inter.className}>
        <ConsoleFilter />
        <AntdProvider>
          <AuthProvider>
            {children}
          </AuthProvider>
        </AntdProvider>
      </body>
    </html>
  );
}
