import { useEffect, useState } from "react";
import type { Customer, Dealership, ServiceType, Vehicle } from "../api";

export interface SearchParams {
  dealershipId: number;
  vehicleId: number;
  serviceTypeId: number;
  customerId: number;
  near: string; // ISO 8601, UTC
  windowHours: number;
}

interface Props {
  dealerships: Dealership[];
  serviceTypes: ServiceType[];
  vehicles: Vehicle[];
  customers: Customer[];
  loading: boolean;
  onSearch: (params: SearchParams) => void;
}

const WEEKDAY_NAMES = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function formatHours(dealership: Dealership): string {
  const days = dealership.daysOpen
    .slice()
    .sort((a, b) => a - b)
    .map((d) => WEEKDAY_NAMES[d - 1])
    .join(", ");
  return `Open ${dealership.opensAt.slice(0, 5)}–${dealership.closesAt.slice(0, 5)} UTC · ${days}`;
}

/**
 * Dealership hours are stored and compared in UTC (see BusinessHours on
 * the backend) — picking a time using the browser's own local zone can
 * easily land outside business hours without the user realizing why. This
 * defaults to a known-good instant (1 hour after opening) so the demo
 * works out of the box, formatted using *local* date/time getters so the
 * <input type="datetime-local"> displays it correctly for this browser.
 */
function defaultDateTimeLocal(dealership: Dealership | undefined): string {
  const target = new Date();
  target.setDate(target.getDate() + 1);
  if (dealership) {
    const [hours, minutes] = dealership.opensAt.split(":").map(Number);
    target.setUTCHours(hours + 1, minutes, 0, 0);
  } else {
    target.setHours(10, 0, 0, 0);
  }
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${target.getFullYear()}-${pad(target.getMonth() + 1)}-${pad(target.getDate())}T${pad(target.getHours())}:${pad(target.getMinutes())}`;
}

export default function BookingForm({ dealerships, serviceTypes, vehicles, customers, loading, onSearch }: Props) {
  const [dealershipId, setDealershipId] = useState(dealerships[0]?.id ?? 0);
  const [vehicleId, setVehicleId] = useState(vehicles[0]?.id ?? 0);
  const [serviceTypeId, setServiceTypeId] = useState(serviceTypes[0]?.id ?? 0);
  const [customerId, setCustomerId] = useState(customers[0]?.id ?? 0);
  const selectedDealership = dealerships.find((d) => d.id === dealershipId);
  const [dateTimeLocal, setDateTimeLocal] = useState(() => defaultDateTimeLocal(selectedDealership));
  const [windowHours, setWindowHours] = useState(2);

  // Re-default the desired time when the dealership changes, since its
  // business hours (and therefore what "known-good" means) differ.
  useEffect(() => {
    setDateTimeLocal(defaultDateTimeLocal(selectedDealership));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [dealershipId]);

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    // datetime-local has no timezone; interpret it in the browser's local
    // zone and convert to UTC — the backend normalizes to UTC regardless
    // of which offset a client sends, so any correct conversion works.
    const near = new Date(dateTimeLocal).toISOString();
    onSearch({ dealershipId, vehicleId, serviceTypeId, customerId, near, windowHours });
  }

  return (
    <form className="card" onSubmit={handleSubmit}>
      <h2>Book a Service Appointment</h2>

      <label>
        Dealership
        <select value={dealershipId} onChange={(e) => setDealershipId(Number(e.target.value))} required>
          {dealerships.map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </label>
      {selectedDealership && <p className="hint">{formatHours(selectedDealership)}</p>}

      <label>
        Vehicle
        <select value={vehicleId} onChange={(e) => setVehicleId(Number(e.target.value))} required>
          {vehicles.map((v) => (
            <option key={v.id} value={v.id}>
              {v.make} {v.model} — {v.vin}
            </option>
          ))}
        </select>
      </label>

      <label>
        Service
        <select value={serviceTypeId} onChange={(e) => setServiceTypeId(Number(e.target.value))} required>
          {serviceTypes.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name} ({s.durationMinutes} min)
            </option>
          ))}
        </select>
      </label>

      <label>
        Customer
        <select value={customerId} onChange={(e) => setCustomerId(Number(e.target.value))} required>
          {customers.map((c) => (
            <option key={c.id} value={c.id}>
              {c.name}
            </option>
          ))}
        </select>
      </label>

      <label>
        Desired time (your local time)
        <input
          type="datetime-local"
          value={dateTimeLocal}
          onChange={(e) => setDateTimeLocal(e.target.value)}
          required
        />
      </label>

      <label>
        Search window (hours around desired time)
        <input
          type="number"
          min={1}
          max={12}
          value={windowHours}
          onChange={(e) => setWindowHours(Number(e.target.value))}
          required
        />
      </label>

      <button type="submit" disabled={loading || dealerships.length === 0}>
        {loading ? "Searching…" : "Find Availability"}
      </button>
    </form>
  );
}
