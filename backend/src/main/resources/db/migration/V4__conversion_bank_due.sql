-- BankSimJob: real (wall-clock) time when the simulated bank completes a PENDING conversion.
-- null = not handled by the simulator (seeded pilot rows stay PENDING). Wall clock, not the demo clock: it survives restarts.
alter table coin_conversions add column bank_due_at timestamp with time zone;
