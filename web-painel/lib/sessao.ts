import { apiRequest } from "./api";

export type SessionCreated = {
  accountId: string;
  activeMode: "SELLER" | "AFFILIATE";
  expiresAt: string;
};

export function login(email: string, password: string) {
  return apiRequest<SessionCreated>("/v1/sessions", {
    method: "POST",
    body: JSON.stringify({ email: email.trim(), password }),
  });
}

export function currentSession() {
  return apiRequest<SessionCreated>("/v1/sessions/current");
}

export function logout() {
  return apiRequest<void>("/v1/sessions/current", { method: "DELETE" });
}

export function switchMode(mode: SessionCreated["activeMode"]) {
  return apiRequest<SessionCreated>("/v1/sessions/current/mode", {
    method: "PUT",
    body: JSON.stringify({ mode }),
  });
}

export function requestPasswordRecovery(email: string) {
  return apiRequest<void>("/v1/password-recovery", {
    method: "POST",
    body: JSON.stringify({ email: email.trim() }),
  });
}

export function resetPassword(token: string, newPassword: string, confirmPassword: string) {
  return apiRequest<void>("/v1/password-recovery/reset", {
    method: "POST",
    body: JSON.stringify({ token, newPassword, confirmPassword }),
  });
}
