"""Typed Synthea settings and catalogue from the pinned generator artifact."""
import csv
from datetime import date
from functools import lru_cache
import io
import json
from typing import Literal
import zipfile

from pydantic import BaseModel, ConfigDict, Field, model_validator
import store
import synthea_runtime


class Settings(BaseModel):
    model_config = ConfigDict(extra='forbid')
    population: int = Field(default=1, ge=1, le=1000, strict=True)
    minAge: int = Field(default=30, ge=0, le=140, strict=True)
    maxAge: int = Field(default=80, ge=0, le=140, strict=True)
    gender: Literal['', 'F', 'M'] = ''
    patientSeed: str = Field(default='20260912', pattern=r'^-?\d{1,19}$')
    clinicianSeed: str = Field(default='20260912', pattern=r'^-?\d{1,19}$')
    singlePersonSeed: str = Field(default='', pattern=r'^(-?\d{1,19})?$')
    referenceDate: date = date(2026, 9, 12)
    endDate: date = date(2026, 9, 12)
    state: str = 'Massachusetts'
    city: str = ''
    yearsOfHistory: int = Field(default=0, ge=0, le=140, strict=True)
    patientFilter: Literal['all', 'alive', 'dead'] = 'all'
    overflow: bool = Field(default=True, strict=True)
    modules: list[str] = Field(default_factory=list, max_length=500)
    keepModule: str = ''
    timestepDays: int = Field(default=7, ge=1, le=365, strict=True)
    maxAttempts: int = Field(default=1000, ge=1, le=10000, strict=True)
    veteranPopulation: bool = Field(default=False, strict=True)

    @model_validator(mode='after')
    def consistent(self):
        if self.minAge > self.maxAge:
            raise ValueError('Minimum age must not exceed maximum age')
        if self.referenceDate > self.endDate or self.endDate > date.today():
            raise ValueError('Reference date must be on or before the simulation end, which cannot be in the future')
        for seed in [self.patientSeed, self.clinicianSeed, self.singlePersonSeed]:
            if seed and not -(2**63) <= int(seed) < 2**63:
                raise ValueError('Seeds must be signed 64-bit integers')
        if self.singlePersonSeed and self.population != 1:
            raise ValueError('A single-person seed requires a population of one')
        if len(set(self.modules)) != len(self.modules):
            raise ValueError('Choose each module once')
        return self


@lru_cache(maxsize=1)
def catalogue():
    root = synthea_runtime.ROOT
    revision = (root / 'target/synthea-revision.txt').read_text().strip()
    if revision != (root / 'scripts/synthea-version.txt').read_text().strip():
        raise ValueError('The installed Synthea version does not match its mappings')
    with zipfile.ZipFile(root / 'target/synthea.jar') as archive:
        locations = {}
        for row in csv.DictReader(io.StringIO(archive.read('geography/demographics.csv').decode('utf-8-sig'))):
            locations.setdefault(row['STNAME'], set()).add(row['NAME'])
        def modules(prefix):
            return [{'id': name.removeprefix(prefix).removesuffix('.json') if prefix == 'modules/' else name.removeprefix(prefix),
                     'name': json.loads(archive.read(name)).get('name', name)}
                    for name in sorted(archive.namelist()) if name.startswith(prefix) and name.endswith('.json')
                    and '/' not in name.removeprefix(prefix)]
        return {'revision': revision, 'sha256': store.digest(root / 'target/synthea.jar'),
                'locations': {state: sorted(cities) for state, cities in sorted(locations.items())},
                'modules': modules('modules/'), 'keepModules': modules('keep_modules/'),
                'defaults': Settings().model_dump(mode='json')}


def normalize(value):
    settings = Settings.model_validate(value)
    catalog = catalogue()
    if settings.state not in catalog['locations'] or (settings.city and settings.city not in catalog['locations'][settings.state]):
        raise ValueError('Choose a state and city from the installed Synthea catalogue')
    if any(module not in {m['id'] for m in catalog['modules']} for module in settings.modules):
        raise ValueError('Unknown Synthea module')
    if settings.keepModule and settings.keepModule not in {m['id'] for m in catalog['keepModules']}:
        raise ValueError('Unknown patient-selection module')
    return settings.model_dump(mode='json')


def arguments(value):
    settings = Settings.model_validate(value)
    result = ['-p', str(settings.population), '-a', f'{settings.minAge}-{settings.maxAge}',
              '-s', settings.patientSeed, '-cs', settings.clinicianSeed,
              '-r', settings.referenceDate.strftime('%Y%m%d'), '-e', settings.endDate.strftime('%Y%m%d'),
              '-o', str(settings.overflow).lower(),
              f'--exporter.years_of_history={settings.yearsOfHistory}',
              '--generate.thread_pool_size=2',
              '--generate.only_alive_patients=' + str(settings.patientFilter == 'alive').lower(),
              '--generate.only_dead_patients=' + str(settings.patientFilter == 'dead').lower(),
              f'--generate.timestep={settings.timestepDays * 86400000}',
              f'--generate.max_attempts_to_keep_patient={settings.maxAttempts}',
              '--generate.veteran_population_override=' + str(settings.veteranPopulation).lower()]
    if settings.gender:
        result += ['-g', settings.gender]
    if settings.singlePersonSeed:
        result += ['-ps', settings.singlePersonSeed]
    if settings.modules:
        result += ['-m', ':'.join(settings.modules)]
    if settings.keepModule:
        result += ['-k', settings.keepModule]
    result.append(settings.state)
    if settings.city:
        result.append(settings.city)
    return result
