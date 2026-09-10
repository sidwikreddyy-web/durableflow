export type User = {
  id: string;
  email: string;
  role: "USER" | "ADMIN";
  createdAt: string;
};

export type AuthResponse = {
  accessToken: string;
  tokenType: "Bearer";
  expiresInSeconds: number;
  user: User;
};

export type WorkflowStatus =
  | "RUNNING"
  | "COMPENSATING"
  | "COMPENSATED"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export type Workflow = {
  id: string;
  name: string;
  status: WorkflowStatus;
  currentStep: string;
  failureReason: string | null;
  version: number;
  createdAt: string;
  updatedAt: string;
};

export type WorkflowEvent = {
  id: number;
  eventType: string;
  details: string | null;
  createdAt: string;
};
