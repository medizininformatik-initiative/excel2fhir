export function resolveGenerationSeeds<T extends { timestampSeeds?: boolean; patientSeed: string; clinicianSeed: string; singlePersonSeed: string; population: number }>(settings: T, timestamp: number) {
  const { timestampSeeds, ...generation } = settings
  if (!timestampSeeds) return generation
  const seed = String(timestamp)
  return { ...generation, patientSeed: seed, clinicianSeed: seed,
    singlePersonSeed: generation.population === 1 && generation.singlePersonSeed !== '' ? seed : '' }
}
