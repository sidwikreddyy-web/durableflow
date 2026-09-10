"use client";

import { apiRequest } from "@/lib/api";
import type { AuthResponse, User } from "@/lib/types";
import { createContext, useCallback, useContext, useEffect, useMemo, useState } from "react";

type AuthContextValue = {
  user: User | null;
  loading: boolean;
  authenticate: (mode: "login" | "register", email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  request: <T>(path: string, init?: RequestInit) => Promise<T>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [accessToken, setAccessToken] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const session = await apiRequest<AuthResponse>("/api/auth/refresh", { method: "POST" });
    setAccessToken(session.accessToken);
    setUser(session.user);
    return session.accessToken;
  }, []);

  useEffect(() => {
    const bootstrap = window.setTimeout(() => {
      refresh().catch(() => {
        setAccessToken(null);
        setUser(null);
      }).finally(() => setLoading(false));
    }, 0);
    return () => window.clearTimeout(bootstrap);
  }, [refresh]);

  const authenticate = useCallback(async (mode: "login" | "register", email: string, password: string) => {
    const session = await apiRequest<AuthResponse>(`/api/auth/${mode}`, {
      method: "POST",
      body: JSON.stringify({ email, password }),
    });
    setAccessToken(session.accessToken);
    setUser(session.user);
  }, []);

  const logout = useCallback(async () => {
    await apiRequest<void>("/api/auth/logout", { method: "POST" }).catch(() => undefined);
    setAccessToken(null);
    setUser(null);
  }, []);

  const request = useCallback(async <T,>(path: string, init: RequestInit = {}) => {
    const run = (token: string | null) => apiRequest<T>(path, {
      ...init,
      headers: { ...init.headers, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    });

    try {
      return await run(accessToken);
    } catch (error) {
      if (!(error instanceof Error) || !("status" in error) || error.status !== 401) throw error;
      const token = await refresh();
      return run(token);
    }
  }, [accessToken, refresh]);

  const value = useMemo(() => ({ user, loading, authenticate, logout, request }),
    [user, loading, authenticate, logout, request]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth must be used inside AuthProvider");
  return value;
}
