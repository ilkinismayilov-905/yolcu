-- Fields from mocks/*.json that V1 has no place for.
alter table operators add column routes text;                          -- vergüllə: 1,2,88 (mocks/operators.json)
alter table surveys add column seed_responses int not null default 0;  -- tarixi cavab sayı (mocks/surveys.json responses)
alter table survey_questions add column seed_counts text;              -- JSON massiv, options ilə eyni sıra: [686,1154]
