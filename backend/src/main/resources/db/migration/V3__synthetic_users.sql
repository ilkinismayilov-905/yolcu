-- Passengers created by POST /demo/surveys/{id}/simulate-answers; excluded from survey audiences (no notifications).
alter table users add column synthetic boolean not null default false;
