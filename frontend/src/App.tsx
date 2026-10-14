import { useEffect, useState } from "react";
import { api, ApiError } from "./api";
import type { Appointment, AvailabilitySlot, Customer, Dealership, ServiceType, Vehicle } from "./api";
import BookingForm, { type SearchParams } from "./components/BookingForm";
import AvailabilityResults from "./components/AvailabilityResults";
import HoldConfirmation from "./components/HoldConfirmation";

type Step = "loading" | "form" | "results" | "held" | "confirmed" | "fatal";

export default function App() {
    const [step, setStep] = useState<Step>("loading");
    const [error, setError] = useState<string | null>(null);
    const [actionLoading, setActionLoading] = useState(false);

    const [dealerships, setDealerships] = useState<Dealership[]>([]);
    const [serviceTypes, setServiceTypes] = useState<ServiceType[]>([]);
    const [vehicles, setVehicles] = useState<Vehicle[]>([]);
    const [customers, setCustomers] = useState<Customer[]>([]);

    const [lastSearch, setLastSearch] = useState<SearchParams | null>(null);
    const [slots, setSlots] = useState<AvailabilitySlot[]>([]);
    const [appointment, setAppointment] = useState<Appointment | null>(null);

    useEffect(() => {
        Promise.all([api.listDealerships(), api.listServiceTypes(), api.listVehicles(), api.listCustomers()])
            .then(([d, s, v, c]) => {
                setDealerships(d);
                setServiceTypes(s);
                setVehicles(v);
                setCustomers(c);
                if (d.length === 0) {
                    setError(
                        "No dealerships found. Run the backend with SPRING_PROFILES_ACTIVE=dev so DevDataSeeder inserts sample data.",
                    );
                    setStep("fatal");
                } else {
                    setStep("form");
                }
            })
            .catch((err) => {
                setError(describeError(err));
                setStep("fatal");
            });
    }, []);

    async function handleSearch(params: SearchParams) {
        setError(null);
        setActionLoading(true);
        setLastSearch(params);
        try {
            const results = await api.getAvailability(
                params.dealershipId,
                params.vehicleId,
                params.serviceTypeId,
                params.near,
                params.windowHours,
            );
            setSlots(results);
            setStep("results");
        } catch (err) {
            setError(describeError(err));
        } finally {
            setActionLoading(false);
        }
    }

    async function handleHold(slot: AvailabilitySlot) {
        if (!lastSearch) return;
        setError(null);
        setActionLoading(true);
        try {
            const held = await api.hold({
                dealershipId: lastSearch.dealershipId,
                vehicleId: lastSearch.vehicleId,
                customerId: lastSearch.customerId,
                serviceTypeId: lastSearch.serviceTypeId,
                startTime: slot.startTime,
                technicianId: slot.technicianId,
            });
            setAppointment(held);
            setStep("held");
        } catch (err) {
            // Most likely 409 SLOT_NO_LONGER_AVAILABLE — someone else took it
            // between the availability read and this hold. Re-searching is the
            // documented recovery path.
            setError(describeError(err) + " Try searching again for fresh availability.");
        } finally {
            setActionLoading(false);
        }
    }

    async function handleConfirm() {
        if (!appointment) return;
        setError(null);
        setActionLoading(true);
        try {
            const confirmed = await api.confirm(appointment.id, appointment.holdToken!);
            setAppointment(confirmed);
            setStep("confirmed");
        } catch (err) {
            setError(describeError(err));
        } finally {
            setActionLoading(false);
        }
    }

    function handleHoldExpired() {
        setError("Your hold expired before it was confirmed. Please search again.");
        setStep("results");
    }

    function handleBookAnother() {
        setAppointment(null);
        setSlots([]);
        setError(null);
        setStep("form");
    }

    return (
        <main className="app">
            <h1>Service Scheduler</h1>
            {error && <div className="error-banner">{error}</div>}

            {step === "loading" && <p>Loading…</p>}

            {step === "fatal" && (
                <div className="card">
                    <p>Couldn't load the app. {error}</p>
                </div>
            )}

            {step === "form" && (
                <BookingForm
                    dealerships={dealerships}
                    serviceTypes={serviceTypes}
                    vehicles={vehicles}
                    customers={customers}
                    loading={actionLoading}
                    onSearch={handleSearch}
                />
            )}

            {step === "results" && (
                <AvailabilityResults
                    slots={slots}
                    loading={actionLoading}
                    onSelect={handleHold}
                    onBack={() => setStep("form")}
                />
            )}

            {step === "held" && appointment && (
                <HoldConfirmation
                    appointment={appointment}
                    loading={actionLoading}
                    onConfirm={handleConfirm}
                    onExpired={handleHoldExpired}
                />
            )}

            {step === "confirmed" && appointment && (
                <div className="card">
                    <h2>Appointment Confirmed</h2>
                    <dl className="details">
                        <dt>Service</dt>
                        <dd>{appointment.serviceTypeName}</dd>
                        <dt>Technician</dt>
                        <dd>{appointment.technicianName}</dd>
                        <dt>Time</dt>
                        <dd>{new Date(appointment.startTime).toLocaleString()}</dd>
                    </dl>
                    <button type="button" onClick={handleBookAnother}>
                        Book Another Appointment
                    </button>
                </div>
            )}
        </main>
    );
}

function describeError(err: unknown): string {
    if (err instanceof ApiError) {
        return `${err.message} (${err.errorCode})`;
    }
    return err instanceof Error ? err.message : "Something went wrong.";
}
