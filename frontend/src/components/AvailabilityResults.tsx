import type { AvailabilitySlot } from "../api";

interface Props {
  slots: AvailabilitySlot[];
  loading: boolean;
  onSelect: (slot: AvailabilitySlot) => void;
  onBack: () => void;
}

export default function AvailabilityResults({ slots, loading, onSelect, onBack }: Props) {
  return (
    <div className="card">
      <h2>Available Slots</h2>
      <button type="button" className="link" onClick={onBack}>
        &larr; Back to search
      </button>

      {slots.length === 0 && <p>No feasible slots in this window — try a different time or a wider window.</p>}

      <ul className="slot-list">
        {slots.map((slot, i) => (
          <li key={`${slot.startTime}-${slot.technicianId}-${i}`}>
            <span>
              <strong>{new Date(slot.startTime).toLocaleString()}</strong> with {slot.technicianName}
            </span>
            <button type="button" onClick={() => onSelect(slot)} disabled={loading}>
              {loading ? "Holding…" : "Hold this slot"}
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
