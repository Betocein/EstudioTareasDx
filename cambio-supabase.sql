-- Ejecutar UNA vez en Supabase > SQL Editor (antes de abrir la app nueva)
alter table tareas add column if not exists minutos_plan int not null default 0;
notify pgrst, 'reload schema';
