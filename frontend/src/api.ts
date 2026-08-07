// Types mirror the backend's web DTOs field-for-field — see
// .claude/docs/openapi.yaml (source of truth) and
// src/main/java/com/appointment_scheduler/web/dto/.

export type VehicleCategory = "SEDAN" | "SUV" | "TRUCK" | "VAN";

export type AppointmentStatus = "HELD" | "CONFIRMED" | "NEEDS_ATTENTION" | "EXPIRED" | "CANCELLED";

export interface Dealership {
  id: number;
  name: string;
  opensAt: string; // "HH:mm:ss"
  closesAt: string;
  daysOpen: number[]; // ISO weekday numbers, Monday = 1 .. Sunday = 7
}

export interface ServiceType {
  id: number;
  name: string;
  durationMinutes: number;
}

export interface Vehicle {
  id: number;
  vin: string;
  make: string | null;
  model: string | null;
  vehicleCategory: VehicleCategory | null;
}

export interface Customer {
  id: number;
  name: string;
  phone: string | null;
}

export interface AvailabilitySlot {
  startTime: string; // ISO 8601 date-time
  technicianId: number;
  technicianName: string;
}

export interface Appointment {
  id: number;
  dealershipId: number;
  status: AppointmentStatus;
  startTime: string;
  endTime: string;
  vehicleId: number;
  vin: string;
  customerId: number;
  serviceTypeId: number;
  serviceTypeName: string;
  bayId: number;
  bayLabel: string;
  technicianId: number;
  technicianName: string;
  holdToken: string | null; // only present while status = HELD
  expiresAt: string | null; // only meaningful while status = HELD
  needsAttentionReason: string | null;
}

export interface HoldRequest {
  dealershipId: number;
  vehicleId: number;
  customerId: number;
  serviceTypeId: number;
  startTime: string;
  technicianId: number;
}

/** Matches .claude/docs/openapi.yaml's ErrorResponse schema. */
export class ApiError extends Error {
  constructor(
    public readonly errorCode: string,
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });

  if (!response.ok) {
    const body = await response.json().catch(() => null);
    throw new ApiError(
      body?.error ?? "UNKNOWN_ERROR",
      body?.message ?? `Request to ${path} failed with status ${response.status}`,
      response.status,
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

export const api = {
  listDealerships: () => request<Dealership[]>("/dealerships"),
  listServiceTypes: () => request<ServiceType[]>("/service-types"),
  listVehicles: () => request<Vehicle[]>("/vehicles"),
  listCustomers: () => request<Customer[]>("/customers"),

  getAvailability: (
    dealershipId: number,
    vehicleId: number,
    serviceTypeId: number,
    near: string,
    windowHours: number,
  ) =>
    request<AvailabilitySlot[]>(
      `/dealerships/${dealershipId}/availability?` +
        new URLSearchParams({
          vehicleId: String(vehicleId),
          serviceTypeId: String(serviceTypeId),
          near,
          windowHours: String(windowHours),
        }),
    ),

  hold: (body: HoldRequest) =>
    request<Appointment>("/appointments/hold", { method: "POST", body: JSON.stringify(body) }),

  confirm: (appointmentId: number, holdToken: string) =>
    request<Appointment>(`/appointments/${appointmentId}/confirm`, {
      method: "POST",
      body: JSON.stringify({ holdToken }),
    }),
};
