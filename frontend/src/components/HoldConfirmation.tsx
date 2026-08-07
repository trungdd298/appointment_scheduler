import { useEffect, useState } from "react";
import type { Appointment } from "../api";

interface Props {
  appointment: Appointment;
  loading: boolean;
  onConfirm: () => void;
  onExpired: () => void;
}

function secondsRemaining(expiresAt: string): number {
  return Math.max(0, Math.round((new Date(expiresAt).getTime() - Date.now()) / 1000));
}

export default function HoldConfirmation({ appointment, loading, onConfirm, onExpired }: Props) {
  const [remaining, setRemaining] = useState(() => secondsRemaining(appointment.expiresAt!));

  useEffect(() => {
    const interval = setInterval(() => {
      const next = secondsRemaining(appointment.expiresAt!);
      setRemaining(next);
      if (next === 0) {
        clearInterval(interval);
        onExpired();
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [appointment.expiresAt, onExpired]);

  const minutes = Math.floor(remaining / 60);
  const seconds = remaining % 60;

  return (
    <div className="card">
      <h2>Confirm Your Appointment</h2>
      <dl className="details">
        <dt>Service</dt>
        <dd>{appointment.serviceTypeName}</dd>
        <dt>Vehicle</dt>
        <dd>{appointment.vin}</dd>
        <dt>Technician</dt>
        <dd>{appointment.technicianName}</dd>
        <dt>Time</dt>
        <dd>
          {new Date(appointment.startTime).toLocaleString()} &ndash;{" "}
          {new Date(appointment.endTime).toLocaleTimeString()}
        </dd>
      </dl>

      <p className="countdown">
        Hold expires in {minutes}:{String(seconds).padStart(2, "0")}
      </p>

      <button type="button" onClick={onConfirm} disabled={loading || remaining === 0}>
        {loading ? "Confirming…" : "Confirm Appointment"}
      </button>
    </div>
  );
}
