import { Profile } from '../database/entities';
import { distanceKm } from './matching';

export interface ProfileView {
  id: string;
  userId: string;
  nickname: string;
  fullName: string;
  age: number;
  gender: string;
  school: string;
  campus: string;
  faculty: string;
  major: string;
  academicYear: string;
  alumni: boolean;
  gpa?: number;
  gpaTier?: string;
  bio: string;
  interests: string[];
  avatarSeed: number;
  mode: string;
  distanceKm: number;
  verified: boolean;
  profileComplete: boolean;
  compatibility?: number;
  showGpaPublic: boolean;
  showLocationPublic: boolean;
}

export function toProfileView(
  profile: Profile,
  viewer?: Profile | null,
  compatibility?: number,
): ProfileView {
  const distance =
    viewer && profile.showLocationPublic
      ? distanceKm(viewer.lat, viewer.lng, profile.lat, profile.lng)
      : 0;
  return {
    id: profile.id,
    userId: profile.userId,
    nickname: profile.nickname,
    fullName: profile.fullName,
    age: profile.age,
    gender: profile.gender,
    school: profile.school,
    campus: profile.campus,
    faculty: profile.faculty,
    major: profile.major,
    academicYear: profile.academicYear,
    alumni: profile.alumni,
    ...(profile.showGpaPublic ? { gpa: profile.gpa, gpaTier: profile.gpaTier } : {}),
    bio: profile.bio,
    interests: profile.interests ?? [],
    avatarSeed: profile.avatarSeed,
    mode: profile.mode,
    distanceKm: distance,
    verified: profile.verified,
    profileComplete: profile.profileComplete,
    ...(compatibility === undefined ? {} : { compatibility }),
    showGpaPublic: profile.showGpaPublic,
    showLocationPublic: profile.showLocationPublic,
  };
}
