-- Run in Supabase > SQL Editor (optional: Hibernate also creates it automatically)
create table if not exists tasks (
  id          bigint generated always as identity primary key,
  title       varchar(150) not null check (length(trim(title)) > 0),
  description text,
  deadline    date,
  priority    varchar(10) not null default 'Medium' check (priority in ('Low','Medium','High')),
  status      varchar(10) not null default 'Pending' check (status in ('Pending','Completed')),
  created_at  timestamptz not null default now()
);
create index if not exists idx_tasks_status on tasks(status);
