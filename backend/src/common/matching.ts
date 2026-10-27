import { Profile } from '../database/entities';

const GPA_ORDER: Record<string, number> = {
  EXCELLENT: 0,
  GOOD: 1,
  FAIR: 2,
  AVERAGE: 3,
};

export function normaliseInterest(value: string): string {
  return value.trim().toLocaleLowerCase('vi');
}

export function sharedInterests(first: string[], second: string[]): string[] {
  const other = new Set((second ?? []).map(normaliseInterest));
  return Array.from(
    new Set((first ?? []).map(normaliseInterest).filter((item) => item && other.has(item))),
  );
}

export function distanceKm(
  latA: number,
  lngA: number,
  latB: number,
  lngB: number,
): number {
  if (!latA || !lngA || !latB || !lngB) return 0;
  const radius = 6371;
  const toRad = (value: number) => (value * Math.PI) / 180;
  const dLat = toRad(latB - latA);
  const dLng = toRad(lngB - lngA);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(latA)) * Math.cos(toRad(latB)) * Math.sin(dLng / 2) ** 2;
  return Math.round(radius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 10) / 10;
}

export function compatibilityScore(viewer: Profile, candidate: Profile): number {
  const shared = sharedInterests(viewer.interests, candidate.interests);
  const union = new Set([
    ...(viewer.interests ?? []).map(normaliseInterest),
    ...(candidate.interests ?? []).map(normaliseInterest),
  ]);
  const jaccard = union.size ? shared.length / union.size : 0;
  const tierDiff = Math.abs(
    (GPA_ORDER[viewer.gpaTier] ?? 1) - (GPA_ORDER[candidate.gpaTier] ?? 1),
  );
  const gpaScore = tierDiff === 0 ? 1 : tierDiff === 1 ? 0.72 : tierDiff === 2 ? 0.44 : 0.2;
  const distance = distanceKm(viewer.lat, viewer.lng, candidate.lat, candidate.lng);
  const ageScore = Math.min(Math.max(1 - Math.abs(viewer.age - candidate.age) / 6, 0), 1);
  const distanceScore = Math.min(Math.max(1 - distance / 50, 0), 1);
  const sameSchool = viewer.school && viewer.school === candidate.school ? 1 : 0;
  const sameCampus = viewer.campus && viewer.campus === candidate.campus ? 1 : 0;
  const sameFaculty = viewer.faculty && viewer.faculty === candidate.faculty ? 1 : 0;
  const sameMode = viewer.mode === candidate.mode ? 1 : 0.35;
  const raw =
    jaccard * 32 +
    gpaScore * 20 +
    sameSchool * 9 +
    sameCampus * 4 +
    sameFaculty * 5 +
    ageScore * 10 +
    distanceScore * 10 +
    sameMode * 10;
  return Math.min(Math.max(Math.round(raw + 5), 12), 99);
}
